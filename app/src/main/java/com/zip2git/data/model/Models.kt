package com.zip2git.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Where the uploaded content came from.
 *
 * Mirrors the three entry points the app exposes: a ZIP archive, a whole project
 * folder, or a hand-picked set of individual files.
 */
enum class SourceType {
    ZIP,
    FOLDER,
    FILES,
}

/** The kind of entry a path refers to inside the archive. */
enum class GitEntryType {
    BLOB,
    TREE,
}

/** Result of diffing a single path between the local upload and the remote branch. */
enum class ChangeStatus {
    ADDED,
    MODIFIED,
    DELETED,
    UNCHANGED,
}

/**
 * One file taken out of the ZIP / folder / selection, already normalised to a
 * repository-relative path with forward slashes.
 *
 * @param path repository-relative path, e.g. `app/src/main/AndroidManifest.xml`
 * @param size uncompressed byte length
 * @param sha Git blob SHA-1 (`sha1("blob $size\0" + bytes)`)
 */
data class ZipFileEntry(
    val path: String,
    val size: Long,
    val sha: String,
)

/** A single entry in the remote tree, as returned by `GET /git/trees/{sha}`. */
data class GitHubTreeItem(
    val path: String,
    val mode: String,
    val type: GitEntryType,
    val sha: String,
    val size: Long?,
)

/** A parsed archive, ready to be diffed. */
data class ParsedZip(
    val sourceType: SourceType,
    val files: List<ZipFileEntry>,
    val totalUncompressedSize: Long,
)

/** The outcome of comparing local files against the remote branch tip. */
data class FileComparison(
    val added: List<ZipFileEntry>,
    val modified: List<ZipFileEntry>,
    val deleted: List<String>,
    val unchanged: List<ZipFileEntry>,
) {
    val hasChanges: Boolean
        get() = added.isNotEmpty() || modified.isNotEmpty() || deleted.isNotEmpty()
}

/** One differing path, used to render the inspection list. */
data class FileChange(
    val path: String,
    val status: ChangeStatus,
)

/** Repository summary used by the dashboard. */
data class RepoDetails(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
    val permissions: GhPermissions?,
)

/** A branch of the target repository. */
data class BranchInfo(
    val name: String,
    val sha: String,
)

/** A previously pushed project, shown on the dashboard for one-tap re-sync. */
@Serializable
data class RecentPushItem(
    @SerialName("repoOwner") val repoOwner: String,
    @SerialName("repoName") val repoName: String,
    @SerialName("branch") val branch: String,
    @SerialName("fileName") val fileName: String,
    @SerialName("timestamp") val timestamp: Long,
)

/** How locally-touched paths are applied to the remote tree. */
enum class PushMode {
    /** Only add/update; never remove anything that already exists remotely. */
    PREVIEW,

    /** Make the branch an exact copy: missing locally means deleted remotely. */
    MIRROR,
}

/** The reviewer's verdict for one path in the Smart File Filter list. */
enum class FilterDecision {
    KEEP,
    SKIP,
}

/**
 * One Smart File Filter rule. Rules are glob-ish suffixes matched against the
 * path, e.g. `.md`, `node_modules/`, `*.log`.
 */
@Serializable
data class SmartFilterRule(
    val pattern: String,
    val decision: FilterDecision = FilterDecision.SKIP,
    val enabled: Boolean = true,
) {
    /**
     * A rule matches when the path equals the pattern, ends with it, or (for
     * patterns wrapped in `*`) matches the trailing extension.
     */
    fun matches(path: String): Boolean {
        if (pattern.isBlank()) return false
        val p = pattern.trim()
        return when {
            p.startsWith("*") -> path.endsWith(p.removePrefix("*"))
            p.endsWith("/") -> path.startsWith(p) || path.contains("/$p")
            else -> path == p || path.endsWith("/$p") || path.endsWith(p)
        }
    }
}

/** Options that control how the local tree is applied to the remote tree. */
data class CompareOptions(
    /**
     * When true the remote branch is made to match the archive exactly: files
     * missing from the archive are deleted. When false only new/changed files
     * are pushed and remote-only files are left alone.
     */
    val mirrorSync: Boolean,
    /** Paths matched by an enabled Smart File Filter rule are not pushed. */
    val filterRules: List<SmartFilterRule> = emptyList(),
    /**
     * When true, files such as README, LICENSE, .gitignore and `.github/` are
     * kept on the remote even when Mirror Sync would otherwise delete them.
     */
    val keepImportantFiles: Boolean = true,
)

/** Request payload for a sync run. */
data class SyncParams(
    val repo: RepoDetails,
    val branch: String,
    val isNewBranch: Boolean,
    val commitMessage: String,
    val sourceType: SourceType,
    val options: CompareOptions,
)

/** Ordered stages of a sync, used to drive the progress UI and status text. */
enum class SyncStep {
    IDLE,
    VALIDATING_REPO,
    READING_ZIP,
    FETCHING_TREE,
    BUILDING_TREE,
    SYNCING_BLOBS,
    CREATING_COMMIT,
    DONE,
    FAILED,
}

/**
 * Classification of a failed sync. The message strings attached to each case are
 * the ones the original app surfaces to the user.
 */
enum class SyncError {
    PAT_MISSING,
    INVALID_TOKEN,
    INVALID_URL,
    REPO_OR_BRANCH_NOT_FOUND,
    NO_PUSH_PERMISSION,
    WORKFLOW_SCOPE_REQUIRED,
    RATE_LIMITED,
    UNSAFE_PATH,
    ZIP_LIMIT_EXCEEDED,
    EMPTY_ARCHIVE,
    NETWORK_ERROR,
    REPO_CHANGED,
    UNKNOWN,
}

/** Emitted on the progress flow while a sync runs. */
data class SyncProgress(
    val step: SyncStep,
    val message: String,
    val processed: Int = 0,
    val total: Int = 0,
)

/** Emitted when a sync fails, with a human-readable title and detail. */
data class SyncErrorInfo(
    val error: SyncError,
    val title: String,
    val message: String,
)

/** Outcome of a successful sync. */
data class SyncResult(
    val commitSha: String,
    val commitUrl: String?,
    val filesSynchronized: Int,
    val isNewBranch: Boolean,
    val isEmptyRepo: Boolean,
)

