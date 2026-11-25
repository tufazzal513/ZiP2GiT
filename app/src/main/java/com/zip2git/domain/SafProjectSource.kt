package com.zip2git.domain

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.zip2git.data.model.SourceType
import com.zip2git.data.model.ZipFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A project assembled from SAF documents: either a whole folder tree picked with
 * `ACTION_OPEN_DOCUMENT_TREE`, or a flat selection of files picked with
 * `ACTION_OPEN_DOCUMENT` / `ACTION_GET_CONTENT`.
 *
 * Bytes are read on demand so a large folder does not need to fit in memory all
 * at once, but every file is size-checked up front against the same limits the
 * ZIP path uses.
 */
class SafProjectSource private constructor(
    private val context: Context,
    override val sourceType: SourceType,
    override val entries: List<ZipFileEntry>,
    /** Maps the normalised repo path back to the SAF document it came from. */
    private val urisByPath: Map<String, Uri>,
    override val totalUncompressedSize: Long,
) : ProjectSource {

    override suspend fun open(path: String): ByteArray = withContext(Dispatchers.IO) {
        val uri = urisByPath[path] ?: throw NoSuchElementException("No document for $path")
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to open selected file: $path")
    }

    override fun close() = Unit

    companion object {
        suspend fun fromTree(context: Context, treeUri: Uri): SafProjectSource =
            withContext(Dispatchers.IO) {
                val root = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw EmptyArchiveException("The selected folder could not be opened.")
                val collected = ArrayList<Pair<String, Uri>>()
                walk(root, root.name.orEmpty(), collected)

                if (collected.isEmpty()) {
                    throw EmptyArchiveException("The selected folder does not contain any readable files.")
                }
                if (collected.size > ArchiveLimits.MAX_FILE_COUNT) {
                    throw ArchiveTooLargeException(
                        "Folder exceeds maximum file count limit of " +
                            "${ArchiveLimits.MAX_FILE_COUNT} files.",
                    )
                }
                build(context, SourceType.FOLDER, collected)
            }

        suspend fun fromDocuments(context: Context, uris: List<Uri>): SafProjectSource =
            withContext(Dispatchers.IO) {
                if (uris.isEmpty()) {
                    throw EmptyArchiveException(
                        "Please choose a ZIP file or individual files to synchronize.",
                    )
                }
                val collected = uris.mapNotNull { uri ->
                    val doc = DocumentFile.fromSingleUri(context, uri) ?: return@mapNotNull null
                    val name = doc.name ?: uri.lastPathSegment?.substringAfterLast('/') ?: return@mapNotNull null
                    name to uri
                }
                build(context, SourceType.FILES, collected)
            }

        private fun walk(dir: DocumentFile, basePath: String, out: MutableList<Pair<String, Uri>>) {
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                if (name.startsWith(".")) continue
                val path = if (basePath.isEmpty()) name else "$basePath/$name"
                if (child.isDirectory) {
                    walk(child, path, out)
                } else if (child.isFile) {
                    out.add(path to child.uri)
                }
            }
        }

        private suspend fun build(
            context: Context,
            type: SourceType,
            collected: List<Pair<String, Uri>>,
        ): SafProjectSource {
            val entries = ArrayList<ZipFileEntry>(collected.size)
            val urisByPath = HashMap<String, Uri>(collected.size)
            var total = 0L

            for ((rawPath, uri) in collected) {
                val source = if (type == SourceType.FOLDER) rawPath else uri.lastPathSegment ?: rawPath
                val normalized = PathSafety.normalizeOrNull(
                    if (type == SourceType.FOLDER) rawPath else (source.substringAfterLast('/')),
                ) ?: continue

                val size = DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
                if (size > ArchiveLimits.MAX_FILE_BYTES) {
                    throw ArchiveTooLargeException("\"$source\" exceeds 25 MB limit.")
                }
                total += size
                if (total > ArchiveLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    throw ArchiveTooLargeException("Total folder size exceeds 120MB limit.")
                }
                val sha = runCatching { GitBlob.sha(read(context, uri)) }.getOrDefault("")
                entries.add(ZipFileEntry(path = normalized, sha = sha, size = size))
                urisByPath[normalized] = uri
            }

            if (entries.isEmpty()) {
                throw EmptyArchiveException("The selected folder does not contain any readable files.")
            }
            return SafProjectSource(
                context = context,
                sourceType = type,
                entries = entries.sortedBy { it.path },
                urisByPath = urisByPath,
                totalUncompressedSize = total,
            )
        }

        private fun read(context: Context, uri: Uri): ByteArray =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalStateException("Unable to read $uri")
    }
}
