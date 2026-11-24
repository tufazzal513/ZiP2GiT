package com.zip2git.ui

import com.zip2git.data.model.BranchInfo
import com.zip2git.data.model.FileComparison
import com.zip2git.data.model.RecentPushItem
import com.zip2git.data.model.RepoDetails
import com.zip2git.data.model.SmartFilterRule
import com.zip2git.data.model.SourceType
import com.zip2git.data.model.SyncErrorInfo
import com.zip2git.data.model.SyncProgress
import com.zip2git.data.model.SyncResult

/** Top-level screens driven by [MainViewModel]. */
enum class Screen { SETUP, DASHBOARD, SYNC, RESULT }

/**
 * A hand-written state holder instead of a dataclass per screen. Boolean flags
 * mirror the original `isValidatingToken`, `isFetchingBranches`, `isProcessingZip`
 * etc. so the UI can show the same spinners.
 */
data class MainUiState(
    val screen: Screen = Screen.SETUP,
    val darkTheme: Boolean = false,

    // Setup / credentials
    val repoUrlInput: String = "",
    val tokenInput: String = "",
    val tokenIsSet: Boolean = false,
    val connectedLogin: String = "",
    val tokenValidationMessage: String? = null,
    val isValidatingToken: Boolean = false,

    // Repository
    val repo: RepoDetails? = null,
    val isInitializingRepo: Boolean = false,
    val repoBranches: List<BranchInfo> = emptyList(),
    val isFetchingBranches: Boolean = false,
    val selectedBranch: String = "",
    val commitMessage: String = "Initial commit via Zip2Git",

    // Source selection
    val sourceType: SourceType? = null,
    val sourceName: String = "",
    val totalZipFiles: Int = 0,
    val isProcessingZip: Boolean = false,
    val mirrorSync: Boolean = false,

    // Advanced Options
    val showAdvanced: Boolean = false,
    val filterRules: List<SmartFilterRule> = emptyList(),
    val keepImportantFiles: Boolean = true,
    val stripRootFolder: Boolean = true,

    // Diff + sync
    val comparison: FileComparison? = null,
    val syncProgress: SyncProgress? = null,
    val syncResult: SyncResult? = null,
    val syncError: SyncErrorInfo? = null,
    val isSyncing: Boolean = false,

    // History
    val recentPushes: List<RecentPushItem> = emptyList(),

    // Transient banner text
    val banner: String? = null,
)
