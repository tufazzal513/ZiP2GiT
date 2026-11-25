package com.zip2git.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zip2git.Zip2GitApp
import com.zip2git.data.model.BranchInfo
import com.zip2git.data.model.CompareOptions
import com.zip2git.data.model.GhUserResponse
import com.zip2git.data.model.RecentPushItem
import com.zip2git.data.model.RepoSummary
import com.zip2git.data.model.SmartFilterRule
import com.zip2git.data.model.SourceType
import com.zip2git.data.model.SyncProgress
import com.zip2git.data.model.SyncResult
import com.zip2git.data.model.SyncStep
import com.zip2git.data.model.ZipFileEntry
import com.zip2git.data.remote.GitHubRepository
import com.zip2git.domain.DiffEngine
import com.zip2git.domain.ErrorMapper
import com.zip2git.domain.ProjectSource
import com.zip2git.domain.RepoUrlParser
import com.zip2git.domain.SafProjectSource
import com.zip2git.domain.SyncEngine
import com.zip2git.domain.ZipProjectSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream

/**
 * Owns all app state and orchestrates: token validation → repo load → source
 * picking → diff → sync → result. The UI is a pure function of [uiState].
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appContainer = app as Zip2GitApp
    private val repo = GitHubRepository()
    private val engine = SyncEngine(repo)

    private val _uiState = MutableStateFlow(
        MainUiState(
            darkTheme = appContainer.themeStore.darkMode,
            tokenIsSet = appContainer.tokenStore.hasToken(),
            connectedLogin = appContainer.tokenStore.lastUserLogin.orEmpty(),
            recentPushes = appContainer.historyStore.items,
            filterRules = appContainer.filterStore.rules,
            keepImportantFiles = appContainer.filterStore.keepImportantFiles,
            stripRootFolder = appContainer.filterStore.stripRootFolder,
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var projectSource: ProjectSource? = null
    private var syncJob: Job? = null

    // -- credentials -----------------------------------------------------------

    fun onRepoUrlChange(value: String) = _uiState.update { it.copy(repoUrlInput = value) }

    fun onTokenChange(value: String) = _uiState.update { it.copy(tokenInput = value) }

    fun toggleDarkTheme() {
        val next = appContainer.themeStore.toggle()
        _uiState.update { it.copy(darkTheme = next) }
    }

    /** Saves the token (if edited) and validates it against `GET /user`. */
    fun connect() {
        val state = _uiState.value
        val inputToken = state.tokenInput.trim().ifBlank { appContainer.tokenStore.token.orEmpty() }

        if (inputToken.isBlank()) {
            _uiState.update {
                it.copy(tokenValidationMessage = "Please enter a Personal Access Token first.")
            }
            return
        }

        // The URL is optional here: a blank field just means the repository is
        // picked from the dropdown on the next wizard page.
        val coords = state.repoUrlInput.trim().let {
            if (it.isEmpty()) null else RepoUrlParser.parseOrNull(it)
        }
        if (coords == null && state.repoUrlInput.isNotBlank()) {
            _uiState.update {
                it.copy(
                    tokenValidationMessage =
                        "Please enter a valid GitHub repository URL (e.g. https://github.com/owner/repository).",
                )
            }
            return
        }

        appContainer.tokenStore.token = inputToken
        _uiState.update { it.copy(isValidatingToken = true, tokenValidationMessage = null) }

        viewModelScope.launch {
            runCatching { repo.getUser() }
                .onSuccess { user ->
                    onConnected(user, coords?.owner, coords?.name)
                }
                .onFailure { error -> reportSetupFailure(error) }
        }
    }

    private fun onConnected(user: GhUserResponse, owner: String?, name: String?) {
        appContainer.tokenStore.lastUserLogin = user.login
        _uiState.update {
            it.copy(
                isValidatingToken = false,
                tokenIsSet = true,
                connectedLogin = user.login,
                tokenInput = "",
                tokenValidationMessage = "Connected as @${user.login}",
                screen = Screen.DASHBOARD,
                step = WizardStep.REPOSITORY,
            )
        }
        loadUserRepos()
        if (owner != null && name != null) loadRepository(owner, name)
    }

    private fun reportSetupFailure(error: Throwable) {
        val info = ErrorMapper.map(error)
        _uiState.update {
            it.copy(
                isValidatingToken = false,
                tokenValidationMessage = info.message,
            )
        }
    }

    // -- repository ------------------------------------------------------------

    fun loadRepository(owner: String, name: String, preferredBranch: String? = null) {
        _uiState.update { it.copy(isInitializingRepo = true, banner = null) }
        viewModelScope.launch {
            runCatching { repo.getRepoDetails(owner, name) }
                .onSuccess { details ->
                    _uiState.update {
                        it.copy(
                            isInitializingRepo = false,
                            repo = details,
                            selectedBranch = preferredBranch?.takeIf { b -> b.isNotBlank() }
                                ?: details.defaultBranch,
                            banner = null,
                        )
                    }
                    loadBranches()
                }
                .onFailure { error ->
                    val info = ErrorMapper.map(error)
                    _uiState.update {
                        it.copy(isInitializingRepo = false, syncError = info)
                    }
                }
        }
    }

    fun loadBranches() {
        val details = _uiState.value.repo ?: return
        _uiState.update { it.copy(isFetchingBranches = true) }
        viewModelScope.launch {
            val candidates = (listOf(details.defaultBranch, "main", "master") +
                _uiState.value.repoBranches.map { it.name }).distinct()
            runCatching {
                repo.listBranches(details.owner, details.name, candidates)
                    .map { BranchInfo(it.name, it.sha) }
            }.onSuccess { branches ->
                _uiState.update {
                    it.copy(isFetchingBranches = false, repoBranches = branches)
                }
            }.onFailure {
                _uiState.update { it.copy(isFetchingBranches = false) }
            }
        }
    }

    fun onBranchChange(branch: String) = _uiState.update { it.copy(selectedBranch = branch) }

    // -- repository picker -----------------------------------------------------

    /** Backs the "Referesh repositories list" action next to the dropdown. */
    fun loadUserRepos() {
        _uiState.update { it.copy(isFetchingRepos = true, repoValidationMessage = null) }
        viewModelScope.launch {
            runCatching { repo.listUserRepos() }
                .onSuccess { repos ->
                    _uiState.update {
                        it.copy(
                            isFetchingRepos = false,
                            availableRepos = repos,
                            repoValidationMessage =
                                if (repos.isEmpty()) {
                                    "No repositories found for this token."
                                } else {
                                    "Connected as @${it.connectedLogin} (${repos.size} repos found)"
                                },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isFetchingRepos = false,
                            repoValidationMessage = ErrorMapper.map(error).message,
                        )
                    }
                }
        }
    }

    /** "Select from your repos": fills the URL field and loads the repository. */
    fun selectRepoFromList(summary: RepoSummary) {
        _uiState.update {
            it.copy(
                repoUrlInput = "https://github.com/${summary.fullName}",
                banner = null,
                syncError = null,
            )
        }
        loadRepository(summary.owner, summary.name, preferredBranch = summary.defaultBranch)
    }

    /**
     * "Test Token & Load Repos": saves the PAT when it was edited, validates it
     * with `GET /user`, then fills the "Select from your repos" dropdown.
     */
    fun testTokenAndLoadRepos() {
        val state = _uiState.value
        val inputToken = state.tokenInput.trim().ifBlank { appContainer.tokenStore.token.orEmpty() }
        if (inputToken.isBlank()) {
            _uiState.update {
                it.copy(tokenValidationMessage = "Please enter a Personal Access Token first.")
            }
            return
        }

        appContainer.tokenStore.token = inputToken
        _uiState.update { it.copy(isValidatingToken = true, tokenValidationMessage = null) }

        viewModelScope.launch {
            runCatching { repo.getUser() }
                .onSuccess { user ->
                    appContainer.tokenStore.lastUserLogin = user.login
                    _uiState.update {
                        it.copy(
                            isValidatingToken = false,
                            tokenIsSet = true,
                            connectedLogin = user.login,
                            tokenInput = "",
                            tokenValidationMessage = "Connected as @${user.login}",
                        )
                    }
                    loadUserRepos()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isValidatingToken = false,
                            tokenValidationMessage = ErrorMapper.map(error).message,
                        )
                    }
                }
        }
    }

    fun onCommitMessageChange(message: String) =
        _uiState.update { it.copy(commitMessage = message) }

    fun setMirrorSync(enabled: Boolean) = _uiState.update { it.copy(mirrorSync = enabled) }

    // -- advanced options ------------------------------------------------------

    fun toggleAdvanced() = _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }

    fun setKeepImportantFiles(enabled: Boolean) {
        appContainer.filterStore.keepImportantFiles = enabled
        _uiState.update { it.copy(keepImportantFiles = enabled) }
    }

    fun setStripRootFolder(enabled: Boolean) {
        appContainer.filterStore.stripRootFolder = enabled
        _uiState.update { it.copy(stripRootFolder = enabled) }
    }

    fun addFilterRule(pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return
        val rules = _uiState.value.filterRules.toMutableList()
        rules += SmartFilterRule(trimmed)
        appContainer.filterStore.rules = rules
        _uiState.update { it.copy(filterRules = rules) }
        refreshComparison()
    }

    fun removeFilterRule(pattern: String) {
        val rules = _uiState.value.filterRules.filterNot { it.pattern == pattern }
        appContainer.filterStore.rules = rules
        _uiState.update { it.copy(filterRules = rules) }
        refreshComparison()
    }

    fun toggleFilterRule(pattern: String) {
        val rules = _uiState.value.filterRules.map {
            if (it.pattern == pattern) it.copy(enabled = !it.enabled) else it
        }
        appContainer.filterStore.rules = rules
        _uiState.update { it.copy(filterRules = rules) }
        refreshComparison()
    }

    // -- per-file review -------------------------------------------------------

    /**
     * Unchecks a path in the change review: it is treated as never selected, so
     * it is neither uploaded nor counted as a deletion.
     */
    fun togglePathExcluded(path: String) {
        val current = _uiState.value.excludedPaths
        val next = if (current.contains(path)) current - path else current + path
        _uiState.update { it.copy(excludedPaths = next) }
        refreshComparison()
    }

    fun clearExcludedPaths() {
        _uiState.update { it.copy(excludedPaths = emptySet()) }
        refreshComparison()
    }

    /** Expands the local/remote preview row for [path], or collapses it. */
    fun toggleExpandedPath(path: String) {
        _uiState.update {
            it.copy(expandedPath = if (it.expandedPath == path) null else path)
        }
    }

    /** "Inspect Changes & Validate": validates the branch, then runs the diff. */
    fun inspectChanges() {
        val state = _uiState.value
        val details = state.repo
        if (projectSource == null) {
            _uiState.update { it.copy(banner = "Pick a ZIP, Folder, or individual files to upload") }
            return
        }
        if (details == null) {
            _uiState.update { it.copy(banner = "Load a GitHub repository first.") }
            return
        }
        if (state.selectedBranch.isBlank()) {
            _uiState.update { it.copy(banner = "Please specify the repository branch name.") }
            return
        }
        _uiState.update {
            it.copy(
                showReview = true,
                banner = null,
                syncError = null,
                syncProgress = SyncProgress(SyncStep.VALIDATING_REPO, "Checking GitHub repository & permissions..."),
            )
        }
        refreshComparison()
    }

    // -- source selection ------------------------------------------------------

    /** Loads a picked ZIP stream into memory and computes the diff. */
    fun loadZip(displayName: String, openStream: () -> InputStream?) {
        _uiState.update {
            it.copy(isProcessingZip = true, sourceName = displayName, banner = null, syncError = null)
        }
        viewModelScope.launch {
            runCatching {
                val bytes = openStream()?.use { it.readBytes() }
                    ?: throw IllegalStateException("Unable to open selected ZIP file.")
                ZipProjectSource.from(bytes, stripRoot = _uiState.value.stripRootFolder)
            }.onSuccess { source ->
                projectSource = source
                _uiState.update {
                    it.copy(
                        isProcessingZip = false,
                        sourceType = SourceType.ZIP,
                        totalZipFiles = source.entries.size,
                        totalZipBytes = source.entries.sumOf { it.size },
                        excludedPaths = emptySet(),
                        expandedPath = null,
                        showReview = false,
                    )
                }
                refreshComparison()
            }.onFailure { error ->
                val info = ErrorMapper.map(error)
                _uiState.update {
                    it.copy(isProcessingZip = false, syncError = info, comparison = null)
                }
            }
        }
    }

    fun loadFolderTree(uri: Uri) {
        _uiState.update { it.copy(isProcessingZip = true, syncError = null) }
        viewModelScope.launch {
            runCatching { SafProjectSource.fromTree(getApplication<Application>(), uri) }
                .onSuccess { source ->
                    projectSource = source
                    _uiState.update {
                        it.copy(
                            isProcessingZip = false,
                            sourceType = SourceType.FOLDER,
                            sourceName = uri.lastPathSegment ?: "folder",
                            totalZipFiles = source.entries.size,
                            totalZipBytes = source.entries.sumOf { it.size },
                            excludedPaths = emptySet(),
                            expandedPath = null,
                            showReview = false,
                        )
                    }
                    refreshComparison()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isProcessingZip = false, syncError = ErrorMapper.map(error), comparison = null)
                    }
                }
        }
    }

    fun loadDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _uiState.update { it.copy(isProcessingZip = true, syncError = null) }
        viewModelScope.launch {
            runCatching { SafProjectSource.fromDocuments(getApplication<Application>(), uris) }
                .onSuccess { source ->
                    projectSource = source
                    _uiState.update {
                        it.copy(
                            isProcessingZip = false,
                            sourceType = SourceType.FILES,
                            sourceName = "${source.entries.size} file(s) selected",
                            totalZipFiles = source.entries.size,
                            totalZipBytes = source.entries.sumOf { it.size },
                            excludedPaths = emptySet(),
                            expandedPath = null,
                            showReview = false,
                        )
                    }
                    refreshComparison()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isProcessingZip = false, syncError = ErrorMapper.map(error), comparison = null)
                    }
                }
        }
    }

    /** Clears the picked source so the user can choose a different ZIP/folder/files. */
    fun clearSource() {
        projectSource?.close()
        projectSource = null
        _uiState.update {
            it.copy(
                sourceType = null,
                sourceName = "",
                totalZipFiles = 0,
                totalZipBytes = 0,
                comparison = null,
                excludedPaths = emptySet(),
                expandedPath = null,
                showReview = false,
            )
        }
    }

    /** Recomputes added/modified/deleted counts against the current remote branch. */
    fun refreshComparison() {
        val source = projectSource ?: return
        val details = _uiState.value.repo ?: return
        val branch = _uiState.value.selectedBranch.ifBlank { details.defaultBranch }

        viewModelScope.launch {
            runCatching {
                val tip = repo.getBranchOrNull(details.owner, details.name, branch)
                val treeSha = tip?.let {
                    repo.getCommitTreeSha(details.owner, details.name, it.commit.sha)
                }
                val tree = treeSha?.let { repo.listTreeFiles(details.owner, details.name, it) }
                    ?: emptyList()
                DiffEngine.compare(
                    localFiles = source.entries,
                    remoteTree = tree,
                    options = CompareOptions(
                        mirrorSync = _uiState.value.mirrorSync,
                        filterRules = _uiState.value.filterRules,
                        excludedPaths = _uiState.value.excludedPaths,
                        keepImportantFiles = _uiState.value.keepImportantFiles,
                    ),
                )
            }.onSuccess { comparison ->
                _uiState.update { it.copy(comparison = comparison, syncProgress = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(syncError = ErrorMapper.map(error), syncProgress = null)
                }
            }
        }
    }

    // -- sync ------------------------------------------------------------------

    fun startSync() {
        val state = _uiState.value
        val source = projectSource
        val details = state.repo
        if (source == null) {
            _uiState.update {
                it.copy(banner = "Please choose a ZIP file or individual files to synchronize.")
            }
            return
        }
        if (details == null) return
        if (state.selectedBranch.isBlank()) {
            _uiState.update { it.copy(banner = "Please specify the repository branch name.") }
            return
        }

        val branch = state.selectedBranch
        _uiState.update {
            it.copy(isSyncing = true, syncError = null, syncResult = null, screen = Screen.SYNC, step = WizardStep.PROGRESS)
        }

        syncJob = viewModelScope.launch {
            runCatching {
                engine.sync(
                    owner = details.owner,
                    repoName = details.name,
                    branch = branch,
                    commitMessage = state.commitMessage,
                    source = source,
                    options = CompareOptions(
                        mirrorSync = state.mirrorSync,
                        filterRules = state.filterRules,
                        excludedPaths = state.excludedPaths,
                        keepImportantFiles = state.keepImportantFiles,
                    ),
                    defaultBranch = details.defaultBranch,
                    isEmptyRepo = false,
                    onProgress = { progress -> _uiState.update { it.copy(syncProgress = progress) } },
                )
            }.onSuccess { outcome ->
                val result = SyncResult(
                    commitSha = outcome.commitSha,
                    commitUrl = outcome.commitUrl,
                    filesSynchronized = outcome.filesSynchronized,
                    isNewBranch = outcome.isNewBranch,
                    isEmptyRepo = outcome.isEmptyRepo,
                )
                appContainer.historyStore.record(
                    RecentPushItem(
                        repoOwner = details.owner,
                        repoName = details.name,
                        branch = branch,
                        fileName = state.sourceName.ifBlank { "project.zip" },
                        timestamp = System.currentTimeMillis(),
                    ),
                )
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncProgress = SyncProgress(SyncStep.DONE, "Successfully Pushed to GitHub!"),
                        syncResult = result,
                        recentPushes = appContainer.historyStore.items,
                        screen = Screen.RESULT,
                        step = WizardStep.RESULT,
                    )
                }
                loadBranches()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncError = ErrorMapper.map(error),
                        screen = Screen.RESULT,
                        step = WizardStep.RESULT,
                    )
                }
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        syncJob = null
        _uiState.update {
            it.copy(
                isSyncing = false,
                syncProgress = null,
                screen = Screen.DASHBOARD,
                step = WizardStep.COMMIT,
                banner = "Sync cancelled by user",
            )
        }
    }

    // -- navigation ------------------------------------------------------------

    /**
     * Jumps to any workflow page. Progress and Result are separate full screens,
     * so they also switch [Screen]; every other page lives on the dashboard.
     */
    fun goToStep(step: WizardStep) {
        val target = when (step) {
            WizardStep.PROGRESS -> Screen.SYNC
            WizardStep.RESULT -> Screen.RESULT
            else -> Screen.DASHBOARD
        }
        _uiState.update { it.copy(step = step, screen = target, banner = null) }
    }

    /** Moves one page forward, guarding the pages that need data first. */
    fun nextStep() {
        val state = _uiState.value
        when (state.step) {
            WizardStep.TOKEN -> {
                if (state.connectedLogin.isBlank()) {
                    _uiState.update {
                        it.copy(
                            tokenValidationMessage =
                                "Connect your GitHub token first, then continue.",
                        )
                    }
                    return
                }
                goToStep(WizardStep.REPOSITORY)
            }

            WizardStep.REPOSITORY -> {
                if (state.repo == null) {
                    _uiState.update { it.copy(banner = "Load a GitHub repository first.") }
                    return
                }
                loadBranches()
                goToStep(WizardStep.SOURCE)
            }

            WizardStep.SOURCE -> {
                if (state.sourceName.isBlank()) {
                    _uiState.update {
                        it.copy(banner = "Pick a ZIP, Folder, or individual files to upload")
                    }
                    return
                }
                goToStep(WizardStep.REVIEW)
            }

            // Review → commit is allowed even with nothing to upload, because the
            // commit page is where the user sees "everything is up to date".
            WizardStep.REVIEW -> goToStep(WizardStep.COMMIT)
            WizardStep.COMMIT -> startSync()
            WizardStep.PROGRESS -> goToStep(WizardStep.RESULT)
            WizardStep.RESULT -> goToStep(WizardStep.HISTORY)
            WizardStep.HISTORY -> startNewPush()
        }
    }

    /** Moves one page back; the first page stays put. */
    fun previousStep() {
        val state = _uiState.value
        when (state.step) {
            WizardStep.TOKEN -> Unit
            WizardStep.REPOSITORY -> goToStep(WizardStep.TOKEN)
            WizardStep.SOURCE -> goToStep(WizardStep.REPOSITORY)
            WizardStep.REVIEW -> goToStep(WizardStep.SOURCE)
            WizardStep.COMMIT -> goToStep(WizardStep.REVIEW)
            // Progress has no back button: the commit may already exist.
            WizardStep.PROGRESS -> Unit
            WizardStep.RESULT -> goToStep(WizardStep.COMMIT)
            WizardStep.HISTORY -> goToStep(WizardStep.RESULT)
        }
    }

    fun goToDashboard() {
        _uiState.update {
            it.copy(
                screen = Screen.DASHBOARD,
                syncError = null,
                syncResult = null,
            )
        }
    }

    fun startNewPush() {
        projectSource?.close()
        projectSource = null
        _uiState.update {
            it.copy(
                screen = Screen.DASHBOARD,
                step = WizardStep.SOURCE,
                sourceType = null,
                sourceName = "",
                totalZipFiles = 0,
                totalZipBytes = 0,
                comparison = null,
                excludedPaths = emptySet(),
                expandedPath = null,
                showReview = false,
                syncProgress = null,
                syncResult = null,
                syncError = null,
            )
        }
    }

    fun dismissBanner() = _uiState.update { it.copy(banner = null) }

    fun dismissError() = _uiState.update { it.copy(syncError = null) }

    /**
     * Re-opens a project from the history list: restores the repository URL and
     * branch, then loads the repository so the user can pick a fresh source.
     */
    fun openRecent(item: RecentPushItem) {
        val url = "https://github.com/${item.repoOwner}/${item.repoName}"
        _uiState.update {
            it.copy(
                screen = Screen.DASHBOARD,
                step = WizardStep.SOURCE,
                repoUrlInput = url,
                banner = "Loaded ${item.repoOwner}/${item.repoName} — pick a source to push again.",
                syncError = null,
                syncResult = null,
            )
        }
        loadRepository(item.repoOwner, item.repoName, preferredBranch = item.branch)
    }

    fun disconnect() {
        appContainer.tokenStore.token = null
        projectSource?.close()
        projectSource = null
        _uiState.update {
            MainUiState(
                screen = Screen.SETUP,
                darkTheme = appContainer.themeStore.darkMode,
                recentPushes = appContainer.historyStore.items,
                filterRules = appContainer.filterStore.rules,
                keepImportantFiles = appContainer.filterStore.keepImportantFiles,
                stripRootFolder = appContainer.filterStore.stripRootFolder,
            )
        }
    }

    val selectedEntries: List<ZipFileEntry>
        get() = projectSource?.entries ?: emptyList()
}
