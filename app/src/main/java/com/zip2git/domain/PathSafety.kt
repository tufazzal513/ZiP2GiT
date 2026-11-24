package com.zip2git.domain

/**
 * Path normalisation and safety checks for content taken out of an archive.
 *
 * Rules (mirrored from the original app's messages):
 *  - backslashes are converted to forward slashes
 *  - leading `./` and `/` are stripped
 *  - empty segments and `.` segments are dropped
 *  - any `..` segment is rejected (ZIP slip)
 *  - absolute paths (Windows drive letters or a leading slash) are rejected
 */
object PathSafety {

    class UnsafePathException(val offendingPath: String) :
        IllegalArgumentException("Unsafe path: $offendingPath")

    fun normalize(raw: String): String {
        if (raw.isEmpty()) throw UnsafePathException(raw)
        if (raw.startsWith("/") || raw.startsWith("\\")) throw UnsafePathException(raw)
        // Windows drive letter, e.g. C:\foo
        if (raw.length >= 2 && raw[1] == ':') throw UnsafePathException(raw)

        val segments = raw.replace('\\', '/').split('/')
        val out = ArrayList<String>(segments.size)
        for (segment in segments) {
            when (segment) {
                "", "." -> continue
                ".." -> throw UnsafePathException(raw)
                else -> out.add(segment)
            }
        }
        if (out.isEmpty()) throw UnsafePathException(raw)
        return out.joinToString("/")
    }

    /** Applies [normalize], returning null instead of throwing for bulk filtering. */
    fun normalizeOrNull(raw: String): String? = runCatching { normalize(raw) }.getOrNull()
}
