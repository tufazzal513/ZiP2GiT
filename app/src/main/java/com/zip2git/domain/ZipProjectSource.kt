package com.zip2git.domain

import com.zip2git.data.model.SourceType
import com.zip2git.data.model.ZipFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

/**
 * The limits the original app enforces before it will touch GitHub. Kept as
 * named constants so the guards and their messages stay in one place.
 */
object ArchiveLimits {
    const val MAX_FILE_COUNT = 3000
    /** Per-file guard the original app enforces before it will touch GitHub. */
    const val MAX_FILE_BYTES = 25L * 1024 * 1024 // 25 MB
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 120L * 1024 * 1024 // 120 MB
}

class ArchiveTooLargeException(message: String) : Exception(message)
class UnsafeArchiveException(message: String) : Exception(message)
class EmptyArchiveException(message: String) : Exception(message)

/** ZIP archives, extracted in-memory (no working copy is written to disk). */
class ZipProjectSource private constructor(
    override val entries: List<ZipFileEntry>,
    private val blobs: Map<String, ByteArray>,
    override val totalUncompressedSize: Long,
) : ProjectSource {

    override val sourceType: SourceType = SourceType.ZIP

    override suspend fun open(path: String): ByteArray =
        blobs[path] ?: throw NoSuchElementException("No entry for $path")

    override fun close() = Unit

    companion object {
        private val IGNORED_PREFIXES = listOf("__MACOSX/")
        private val IGNORED_NAMES = setOf(".DS_Store", "Thumbs.db")

        /**
         * Reads [bytes] as a ZIP and returns a source holding every regular file.
         *
         * Directory entries are skipped. Unsafe paths abort the whole import, and
         * the file-count / total-size guards mirror the app's UI messages.
         *
         * @param stripRoot when true, a single common top-level folder is removed so
         *   "myproject-main/src/Main.kt" becomes "src/Main.kt".
         */
        suspend fun from(bytes: ByteArray, stripRoot: Boolean = true): ZipProjectSource =
            withContext(Dispatchers.IO) {
                val raw = LinkedHashMap<String, ByteArray>()
                var totalSize = 0L

                ZipInputStream(bytes.inputStream().buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && isRelevant(name)) {
                            val normalized = PathSafety.normalizeOrNull(name)
                                ?: throw UnsafeArchiveException(
                                    "ZIP contains unsafe path traversal sequence (../): $name",
                                )
                            val data = zin.readBytes()
                            totalSize += data.size
                            if (data.size > ArchiveLimits.MAX_FILE_BYTES) {
                                throw ArchiveTooLargeException(
                                    "\"$name\" exceeds 25 MB limit.",
                                )
                            }
                            if (raw.size + 1 > ArchiveLimits.MAX_FILE_COUNT) {
                                throw ArchiveTooLargeException(
                                    "ZIP exceeds maximum file count limit of " +
                                        "${ArchiveLimits.MAX_FILE_COUNT} files (found ${raw.size + 1})",
                                )
                            }
                            if (totalSize > ArchiveLimits.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                throw ArchiveTooLargeException(
                                    "Total uncompressed ZIP size exceeds 120MB limit.",
                                )
                            }
                            raw[normalized] = data
                        }
                        zin.closeEntry()
                        entry = zin.nextEntry
                    }
                }

                if (raw.isEmpty()) {
                    throw EmptyArchiveException(
                        "The selected ZIP does not contain any valid project files.",
                    )
                }

                val stripped = if (stripRoot) stripCommonRoot(raw) else raw
                val entries = stripped.entries.map { (path, data) ->
                    ZipFileEntry(path = path, sha = GitBlob.sha(data), size = data.size.toLong())
                }.sortedBy { it.path }

                ZipProjectSource(entries, stripped, totalSize)
            }

        private fun isRelevant(name: String): Boolean {
            if (name.endsWith("/")) return false
            if (IGNORED_PREFIXES.any { name.startsWith(it) }) return false
            if (IGNORED_NAMES.contains(name.substringAfterLast('/'))) return false
            return true
        }

        private fun stripCommonRoot(raw: Map<String, ByteArray>): Map<String, ByteArray> {
            val firstSegment = raw.keys.map { it.substringBefore('/') }.distinct()
            val hasNested = raw.keys.all { it.contains('/') }
            if (firstSegment.size != 1 || !hasNested) return raw
            val prefix = firstSegment.first() + "/"
            return raw.entries.associate { (path, data) -> path.removePrefix(prefix) to data }
        }
    }
}
