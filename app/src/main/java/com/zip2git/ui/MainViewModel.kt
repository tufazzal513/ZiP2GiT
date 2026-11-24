package com.zip2git.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zip2git.Zip2GitApp
import com.zip2git.data.model.BranchInfo
import com.zip2git.data.model.CompareOptions
import com.zip2git.data.model.GitHubApiException
import com.zip2git.data.model.GhUserResponse
import com.zip2git.data.model.RecentPushItem
import com.zip2git.data.model.SourceType
import com.zip2git.data.model.SyncErrorInfo
import com.zip2git.data.model.SyncProgress
import com.zip2git.data.model.SyncResult
import com.zip2git.data.model.SyncStep
import com.zip2git.data.model.ZipFileEntry
import com.zip2git.data.remote.GitHubRepository
import com.zip2git.domain.DiffEngine
import com.zip2git.domain.ErrorMapper
import com.zip2git.domain.ProjectSource
import com.zip2git.domain.RepoUrlParser
import com.zip2git.domain.RepositoryChangedException
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
        val coords = RepoUrlParser.parseOrNull(state.repoUrlInput)
        if (coords == null) {
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
                .onSuccess { user -> onConnected(user, coords.owner, coords.name) }
                .onFailure { error -> reportSetupFailure(error) }
        }
    }

    private fun onConnected(user: GhUserResponse, owner: String, name: String) {
        appContainer.tokenStore.lastUserLogin = user.login
        _uiState.update {
            it.copy(
                isValidatingToken = false,
                tokenIsSet = true,
                connectedLogin = user.login,
                tokenInput = "",
                tokenValidationMessage = "Connected as @${user.login}",
                screen = Screen.DASHBOARD,
            )
        }
        loadRepository(owner, name)
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
            val branches = repo.listBranches(details.owner, details.name, candidates)
                .map { BranchInfo(it.name, it.sha) }
            _uiState.update {
                it.copy(isFetchingBranches = false, repoBranches = branches)
            }
        }
    }

    fun onBranchChange(branch: String) = _uiState.update { it.copy(selectedBranch = branch) }

    fun onCommitMessageChange(message: String) =
        _uiState.update { it.copy(commitMessage = message) }

    fun setMirrorSync(enabled: Boolean) = _uiState.update { it.copy(mirrorSync = enabled) }

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
                ZipProjectSource.from(bytes, stripRoot = true)
            }.onSuccess { source ->
                projectSource = source
                _uiState.update {
                    it.copy(
                        isProcessingZip = false,
                        sourceType = SourceType.ZIP,
                        totalZipFiles = source.entries.size,
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
                    options = CompareOptions(mirrorSync = _uiState.value.mirrorSync),
                )
            }.onSuccess { comparison ->
                _uiState.update { it.copy(comparison = comparison) }
            }.onFailure { error ->
                _uiState.update { it.copy(syncError = ErrorMapper.map(error)) }
            }
        }
    }

    // -- sync ------------------------------------------------------------------

    fun startSync() {
        val state = _uiState.value
        val source = projectSource
        val details = state.repo
        when {
            source == null -> {
                _uiState.update {
                    it.copy(banner = "Please choose a ZIP file or individual files to synchronize.")
                }
                return
            }
            details == null -> return
            state.selectedBranch.isBlank() -> {
                _uiState.update { it.copy(banner = "Please specify the repository branch name.") }
                return
            }
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
                    mirrorSync = state.mirrorSync,
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
                screen = Screen.DASHBOARD,
                banner = "Sync cancelled by user",
            )
        }
    }

    // -- navigation ------------------------------------------------------------

    fun goToDashboard() {
        _uiState.update { it.copy(screen = Screen.DASHBOARD, syncError = null, syncResult = null) }
    }

    fun startNewPush() {
        projectSource?.close()
        projectSource = null
        _uiState.update {
            it.copy(
                screen = Screen.DASHBOARD,
                sourceType = null,
                sourceName = "",
                totalZipFiles = 0,
                comparison = null,
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
            )
        }
    }

    val selectedEntries: List<ZipFileEntry>
        get() = projectSource?.entries ?: emptyList()
}
