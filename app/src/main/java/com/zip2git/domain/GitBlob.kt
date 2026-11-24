package com.zip2git.domain

import java.security.MessageDigest

/** Git object helpers (SHA-1 based, matching git's on-disk object ids). */
object GitBlob {

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Computes the git blob id for [content]:
     * `sha1("blob " + content.size + "\u0000" + content)`.
     *
     * GitHub returns this same value from `POST /git/blobs`, so a locally computed
     * id can be compared against the remote tree without an extra round trip.
     */
    fun sha(content: ByteArray): String {
        val header = "blob ${content.size}".toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(header)
        digest.update(0)
        digest.update(content)
        return digest.digest().toHex()
    }

    fun sha1(data: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(data).toHex()

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (i in indices) {
            val v = this[i].toInt() and 0xFF
            out[i * 2] = HEX[v ushr 4]
            out[i * 2 + 1] = HEX[v and 0x0F]
        }
        return String(out)
    }
}
