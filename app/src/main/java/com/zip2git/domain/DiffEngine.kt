package com.zip2git.domain

import com.zip2git.data.model.ChangeStatus
import com.zip2git.data.model.CompareOptions
import com.zip2git.data.model.FileChange
import com.zip2git.data.model.FileComparison
import com.zip2git.data.model.FilterDecision
import com.zip2git.data.model.GitHubTreeItem
import com.zip2git.data.model.SmartFilterRule
import com.zip2git.data.model.ZipFileEntry

/**
 * Git tree entry modes used when building `POST /git/trees` requests.
 *
 * This app only ever writes regular files and deletions, so the mode is always
 * `100644`; deletion entries are marked by a null sha, not by a different mode.
 */
object GitTreeMode {
    const val FILE = "100644"
    const val BLOB = "blob"
}

/**
 * Compares the files a user selected against the current remote tree.
 *
 * A file is "modified" when its blob sha differs from the remote entry, which is
 * content based: re-uploading identical bytes is a no-op. Remote-only paths are
 * reported as deletions, and [CompareOptions.mirrorSync] decides whether those
 * deletions are actually pushed (exact mirror) or ignored.
 *
 * Two extra rules narrow the result:
 *  - enabled [SmartFilterRule]s drop matching local files before diffing, so a
 *    skipped path looks "not selected" to every later stage;
 *  - when [CompareOptions.keepImportantFiles] is set, README/LICENSE/.gitignore
 *    and everything under `.github/` survive Mirror Sync even if the selection
 *    does not contain them.
 */
object DiffEngine {

    /** Remote paths that Mirror Sync never deletes while "keep important" is on. */
    private val PROTECTED_PREFIXES = listOf(".github/")
    private val PROTECTED_NAMES = setOf(
        "README",
        "README.md",
        "README.txt",
        "LICENSE",
        "LICENSE.md",
        "LICENSE.txt",
        ".gitignore",
    )

    /** True when [path] must be preserved even though it is missing locally. */
    fun isProtected(path: String): Boolean {
        if (PROTECTED_PREFIXES.any { path.startsWith(it) }) return true
        return PROTECTED_NAMES.contains(path)
    }

    /** Applies every enabled rule; a path is dropped as soon as one rule matches. */
    fun applyFilters(
        localFiles: List<ZipFileEntry>,
        rules: List<SmartFilterRule>,
    ): List<ZipFileEntry> {
        val active = rules.filter { it.enabled && it.decision == FilterDecision.SKIP && it.pattern.isNotBlank() }
        if (active.isEmpty()) return localFiles
        return localFiles.filterNot { file -> active.any { it.matches(file.path) } }
    }

    /** Paths that [applyFilters] would drop, so the UI can list them. */
    fun filteredOut(
        localFiles: List<ZipFileEntry>,
        rules: List<SmartFilterRule>,
    ): List<String> {
        val kept = applyFilters(localFiles, rules).mapTo(HashSet()) { it.path }
        return localFiles.map { it.path }.filterNot { kept.contains(it) }
    }

    fun compare(
        localFiles: List<ZipFileEntry>,
        remoteTree: List<GitHubTreeItem>,
        options: CompareOptions,
    ): FileComparison {
        val filtered = applyFilters(localFiles, options.filterRules)
        val remoteByPath = remoteTree.associateBy { it.path }
        val localPaths = HashSet<String>(filtered.size)

        val added = ArrayList<ZipFileEntry>()
        val modified = ArrayList<ZipFileEntry>()
        val unchanged = ArrayList<ZipFileEntry>()

        for (file in filtered) {
            localPaths.add(file.path)
            val remote = remoteByPath[file.path]
            when {
                remote == null -> added.add(file)
                remote.sha != file.sha -> modified.add(file)
                else -> unchanged.add(file)
            }
        }

        val deleted = if (options.mirrorSync) {
            remoteByPath.keys
                .filterNot { localPaths.contains(it) }
                .filterNot { options.keepImportantFiles && isProtected(it) }
                .sorted()
        } else {
            emptyList()
        }

        return FileComparison(
            added = added,
            modified = modified,
            deleted = deleted,
            unchanged = unchanged,
        )
    }

    /** Flattens a [FileComparison] into a display list of per-path changes. */
    fun toChangeList(comparison: FileComparison): List<FileChange> {
        val out = ArrayList<FileChange>(
            comparison.added.size + comparison.modified.size + comparison.deleted.size,
        )
        comparison.added.forEach { out.add(FileChange(it.path, ChangeStatus.ADDED)) }
        comparison.modified.forEach { out.add(FileChange(it.path, ChangeStatus.MODIFIED)) }
        comparison.deleted.forEach { out.add(FileChange(it, ChangeStatus.DELETED)) }
        return out.sortedBy { it.path }
    }
}
