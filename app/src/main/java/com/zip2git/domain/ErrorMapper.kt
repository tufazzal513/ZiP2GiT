package com.zip2git.domain

import com.zip2git.data.model.GitHubApiException
import com.zip2git.data.model.SyncError
import com.zip2git.data.model.SyncErrorInfo
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Maps low-level failures onto the titles and messages the app shows the user.
 * Strings match the recovered resources so the clone reads identically.
 */
object ErrorMapper {

    fun map(throwable: Throwable): SyncErrorInfo = when (throwable) {
        is RepositoryChangedException -> SyncErrorInfo(
            SyncError.REPO_CHANGED,
            "Repository Changed While Syncing",
            throwable.message.orEmpty(),
        )

        is UnsafeArchiveException -> SyncErrorInfo(
            SyncError.UNSAFE_PATH,
            "Unsafe ZIP File",
            "The ZIP file contains unsafe file paths (such as path traversal) and was blocked for security.",
        )

        is ArchiveTooLargeException -> SyncErrorInfo(
            SyncError.ZIP_LIMIT_EXCEEDED,
            "ZIP File Limit Exceeded",
            throwable.message ?: "The archive exceeds the supported size limits.",
        )

        is EmptyArchiveException -> SyncErrorInfo(
            SyncError.EMPTY_ARCHIVE,
            "No Files Found",
            throwable.message ?: "The selected ZIP does not contain any valid project files.",
        )

        is GitHubApiException -> mapApi(throwable)

        is UnknownHostException, is SocketTimeoutException -> SyncErrorInfo(
            SyncError.NETWORK_ERROR,
            "Connection Failed",
            "Could not reach GitHub. Check your internet connection and try again.",
        )

        is IOException -> SyncErrorInfo(
            SyncError.NETWORK_ERROR,
            "Network Error",
            throwable.message ?: "A network error occurred while contacting GitHub.",
        )

        else -> SyncErrorInfo(
            SyncError.UNKNOWN,
            "Synchronization Error",
            throwable.message ?: "An error occurred during GitHub synchronization.",
        )
    }

    private fun mapApi(error: GitHubApiException): SyncErrorInfo {
        val message = error.apiMessage
        return when (error.statusCode) {
            401 -> SyncErrorInfo(
                SyncError.INVALID_TOKEN,
                "GitHub Authentication",
                "GitHub rejected the Personal Access Token. Please verify the token is active and has repository permissions.",
            )

            403 -> when {
                message.contains("rate limit", ignoreCase = true) -> SyncErrorInfo(
                    SyncError.RATE_LIMITED,
                    "Rate Limit Reached",
                    "GitHub API rate limit has been exceeded. Please wait or use a token with available quota.",
                )

                message.contains("workflow", ignoreCase = true) -> workflowScope()

                else -> SyncErrorInfo(
                    SyncError.NO_PUSH_PERMISSION,
                    "Permission Denied",
                    "Your token does not have permission to push to this repository. " +
                        "Make sure the token has \"repo\" or contents write scope.",
                )
            }

            404 -> SyncErrorInfo(
                SyncError.REPO_OR_BRANCH_NOT_FOUND,
                "Repository or Branch Not Found",
                "The repository or specified branch could not be found on GitHub. Verify the URL and " +
                    "branch name. If this is a private repository, ensure your Personal Access Token " +
                    "has repository access permissions.",
            )

            409 -> SyncErrorInfo(
                SyncError.REPO_CHANGED,
                "Repository Changed While Syncing",
                "Repository changed while syncing. No changes were pushed. Please sync again.",
            )

            422 -> if (message.contains("workflow", ignoreCase = true)) {
                workflowScope()
            } else {
                SyncErrorInfo(
                    SyncError.UNKNOWN,
                    "GitHub API Error",
                    message.ifBlank { "GitHub rejected the request." },
                )
            }

            else -> SyncErrorInfo(
                SyncError.UNKNOWN,
                "GitHub API Error",
                "GitHub API error (${error.statusCode}): $message",
            )
        }
    }

    private fun workflowScope() = SyncErrorInfo(
        SyncError.WORKFLOW_SCOPE_REQUIRED,
        "GitHub Workflow Permission Required",
        "GitHub blocked this push because the project includes .github/workflows files. " +
            "Please edit your Personal Access Token on GitHub and check the \"workflow\" scope " +
            "(Update GitHub Action workflows).",
    )
}
