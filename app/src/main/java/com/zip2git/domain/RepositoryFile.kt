package com.zip2git.domain

import com.zip2git.data.model.SourceType
import com.zip2git.data.model.ZipFileEntry

/**
 * A selected project, independent of whether it originated from a ZIP, a SAF
 * folder tree, or individual SAF documents.
 *
 * [entries] describes every regular file with its normalised path and git blob
 * sha. [open] returns the raw bytes for one [ZipFileEntry.path]; implementations
 * may read eagerly (ZIP, held in memory) or on demand (SAF).
 */
interface ProjectSource {
    val sourceType: SourceType
    val entries: List<ZipFileEntry>
    val totalUncompressedSize: Long
    suspend fun open(path: String): ByteArray
    fun close()
}
