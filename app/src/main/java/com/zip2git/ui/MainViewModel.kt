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
import com.zip2git.domain.SecretScanner
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
            // A saved token means the user already connected, so the app opens on
            // the home page instead of asking for credentials again.
            screen = if (appContainer.tokenStore.hasToken()) Screen.DASHBOARD else Screen.SETUP,
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

    fun onTargetFolderChange(value: String) = _uiState.update { it.copy(targetFolder = value) }

    fun onTokenChange(value: String) = _uiState.update { it.copy(tokenInput = value) }

    fun setHideSecrets(enabled: Boolean) = _uiState.update { it.copy(hideSecrets = enabled) }

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
        // picked from the dropdown on the New Git Push form.
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
                screen = Screen.DASHBOARD_PUSH,
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

    /** Backs the "Refresh repositories list" action next to the dropdown. */
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
     * The paths the diff engine should ignore: whatever the user unchecked plus,
     * when [MainUiState.hideSecrets] is on, every detected secret file.
     */
    private fun effectiveExcludedPaths(state: MainUiState): Set<String> {
        if (!state.hideSecrets || state.secretPaths.isEmpty()) return state.excludedPaths
        return state.excludedPaths + state.secretPaths
    }

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
                val secrets = SecretScanner.detectWithContent(source.entries) { source.open(it) }
                _uiState.update {
                    it.copy(
                        isProcessingZip = false,
                        sourceType = SourceType.ZIP,
                        totalZipFiles = source.entries.size,
                        totalZipBytes = source.entries.sumOf { it.size },
                        secretPaths = secrets,
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
                    val secrets = SecretScanner.detectWithContent(source.entries) {
                        source.open(it)
                    }
                    _uiState.update {
                        it.copy(
                            isProcessingZip = false,
                            sourceType = SourceType.FOLDER,
                            sourceName = uri.lastPathSegment ?: "folder",
                            totalZipFiles = source.entries.size,
                            totalZipBytes = source.entries.sumOf { it.size },
                            secretPaths = secrets,
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
                    val secrets = SecretScanner.detectWithContent(source.entries) {
                        source.open(it)
                    }
                    _uiState.update {
                        it.copy(
                            isProcessingZip = false,
                            sourceType = SourceType.FILES,
                            sourceName = "${source.entries.size} file(s) selected",
                            totalZipFiles = source.entries.size,
                            totalZipBytes = source.entries.sumOf { it.size },
                            secretPaths = secrets,
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
                secretPaths = emptyList(),
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
                        excludedPaths = effectiveExcludedPaths(_uiState.value),
                        keepImportantFiles = _uiState.value.keepImportantFiles,
                        pathPrefix = _uiState.value.targetFolder,
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
            it.copy(isSyncing = true, syncError = null, syncResult = null, screen = Screen.SYNC)
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
                        excludedPaths = effectiveExcludedPaths(state),
                        keepImportantFiles = state.keepImportantFiles,
                        pathPrefix = state.targetFolder,
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
                    )
                }
                loadBranches()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncError = ErrorMapper.map(error),
                        screen = Screen.RESULT,
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
                screen = Screen.DASHBOARD_PUSH,
                banner = "Sync cancelled by user",
            )
        }
    }

    // -- navigation ------------------------------------------------------------

    /** Back arrow / "Back to Dashboard": leave the form and return to the home page. */
    fun goToDashboard() {
        _uiState.update {
            it.copy(
                screen = Screen.DASHBOARD,
                syncError = null,
                syncResult = null,
                banner = null,
            )
        }
    }

    /** "Start New Push" on the home page: open the form with nothing pre-selected. */
    fun openPushForm() {
        clearSource()
        _uiState.update {
            it.copy(
                screen = Screen.DASHBOARD_PUSH,
                syncProgress = null,
                syncResult = null,
                syncError = null,
                banner = null,
            )
        }
    }

    /** "Sync Another": same as [openPushForm] but keeps the repository loaded. */
    fun startNewPush() {
        clearSource()
        _uiState.update {
            it.copy(
                screen = Screen.DASHBOARD_PUSH,
                syncProgress = null,
                syncResult = null,
                syncError = null,
                banner = null,
            )
        }
    }

    /** "Continue to Push History": the completed pushes live on the home page. */
    fun openHistory() {
        _uiState.update { it.copy(screen = Screen.DASHBOARD, banner = null) }
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
                screen = Screen.DASHBOARD_PUSH,
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
