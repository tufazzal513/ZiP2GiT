package com.zip2git

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.zip2git.ui.MainViewModel
import com.zip2git.ui.Screen
import com.zip2git.ui.screens.DashboardScreen
import com.zip2git.ui.screens.ResultScreen
import com.zip2git.ui.screens.SetupScreen
import com.zip2git.ui.screens.SyncScreen
import com.zip2git.ui.theme.Zip2GitTheme

/**
 * Single activity host. The screen shown is decided entirely by
 * [MainViewModel.uiState]; this class only owns the three SAF pickers, because
 * those need Activity-scoped result launchers.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** `ACTION_OPEN_DOCUMENT` for one ZIP, read as a stream and held in memory. */
    private val pickZip = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        val resolver = contentResolver
        viewModel.loadZip(displayNameOf(uri)) {
            resolver.openInputStream(uri)
        }
    }

    /** `ACTION_OPEN_DOCUMENT_TREE` for a whole project folder. */
    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@registerForActivityResult
        // Persist access so a long upload does not lose the grant mid-flight.
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        viewModel.loadFolderTree(uri)
    }

    /** `ACTION_OPEN_DOCUMENT` in multi-select mode for individual files. */
    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        viewModel.loadDocuments(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsState()

            Zip2GitTheme(darkTheme = state.darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (state.screen) {
                        Screen.SETUP -> SetupScreen(
                            state = state,
                            onRepoUrlChange = viewModel::onRepoUrlChange,
                            onTokenChange = viewModel::onTokenChange,
                            onConnect = viewModel::connect,
                        )

                        Screen.DASHBOARD -> DashboardScreen(
                            state = state,
                            onTokenChange = viewModel::onTokenChange,
                            onTestToken = viewModel::testTokenAndLoadRepos,
                            onSelectRepo = viewModel::selectRepoFromList,
                            onRefreshRepos = viewModel::loadUserRepos,
                            onRepoUrlChange = viewModel::onRepoUrlChange,
                            onPickZip = { pickZip.launch(ZIP_MIME_TYPES) },
                            onPickFolder = { pickFolder.launch(null) },
                            onPickFiles = { pickFiles.launch(arrayOf("*/*")) },
                            onClearSource = viewModel::clearSource,
                            onBranchChange = viewModel::onBranchChange,
                            onRefreshBranches = viewModel::loadBranches,
                            onCommitMessageChange = viewModel::onCommitMessageChange,
                            onMirrorSyncChange = viewModel::setMirrorSync,
                            onInspectChanges = viewModel::inspectChanges,
                            onRefreshComparison = viewModel::refreshComparison,
                            onTogglePathExcluded = viewModel::togglePathExcluded,
                            onClearExcluded = viewModel::clearExcludedPaths,
                            onToggleExpandedPath = viewModel::toggleExpandedPath,
                            onStartSync = viewModel::startSync,
                            onDisconnect = viewModel::disconnect,
                            onToggleTheme = viewModel::toggleDarkTheme,
                            onDismissBanner = viewModel::dismissBanner,
                            onDismissError = viewModel::dismissError,
                            onOpenRecent = viewModel::openRecent,
                            onToggleAdvanced = viewModel::toggleAdvanced,
                            onKeepImportantChange = viewModel::setKeepImportantFiles,
                            onStripRootChange = viewModel::setStripRootFolder,
                            onAddFilterRule = viewModel::addFilterRule,
                            onRemoveFilterRule = viewModel::removeFilterRule,
                            onToggleFilterRule = viewModel::toggleFilterRule,
                        )

                        Screen.SYNC -> SyncScreen(
                            state = state,
                            onCancel = viewModel::cancelSync,
                        )

                        Screen.RESULT -> ResultScreen(
                            state = state,
                            onSyncAnother = viewModel::startNewPush,
                            onBackToDashboard = viewModel::goToDashboard,
                        )
                    }
                }
            }
        }
    }

    /**
     * Resolves a human-readable name for the picked document, falling back to the
     * last path segment when the provider does not expose [OpenableColumns].
     */
    private fun displayNameOf(uri: Uri): String {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "project.zip"
    }

    private companion object {
        /** Some file managers report a ZIP as octet-stream, so both are offered. */
        val ZIP_MIME_TYPES = arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream",
        )
    }
}
