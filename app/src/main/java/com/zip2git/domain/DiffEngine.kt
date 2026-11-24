package com.zip2git.domain

import com.zip2git.data.model.ChangeStatus
import com.zip2git.data.model.CompareOptions
import com.zip2git.data.model.FileChange
import com.zip2git.data.model.FileComparison
import com.zip2git.data.model.GitHubTreeItem
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
 */
object DiffEngine {

    fun compare(
        localFiles: List<ZipFileEntry>,
        remoteTree: List<GitHubTreeItem>,
        options: CompareOptions,
    ): FileComparison {
        val remoteByPath = remoteTree.associateBy { it.path }
        val localPaths = HashSet<String>(localFiles.size)

        val added = ArrayList<ZipFileEntry>()
        val modified = ArrayList<ZipFileEntry>()
        val unchanged = ArrayList<ZipFileEntry>()

        for (file in localFiles) {
            localPaths.add(file.path)
            val remote = remoteByPath[file.path]
            when {
                remote == null -> added.add(file)
                remote.sha != file.sha -> modified.add(file)
                else -> unchanged.add(file)
            }
        }

        val deleted = if (options.mirrorSync) {
            remoteByPath.keys.filterNot { localPaths.contains(it) }.sorted()
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
