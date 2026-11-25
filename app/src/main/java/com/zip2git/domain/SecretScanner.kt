package com.zip2git.domain

import com.zip2git.data.model.ZipFileEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Flags files that look like credentials before they are pushed.
 *
 * Two passes run over the selection:
 *
 *  1. **Path pass** — cheap name/extension heuristics catch the files a
 *     `.gitignore` would normally keep out (`.env`, `*.pem`, `id_rsa`,
 *     `local.properties`, ...). This runs for every source type and costs
 *     nothing.
 *  2. **Content pass** — a bounded scan of small text files for live token
 *     shapes (`ghp_...`, `AKIA...`, `-----BEGIN ... PRIVATE KEY-----`, ...).
 *     Without it a token pasted into `config.json` would be pushed silently,
 *     because nothing about the file *name* gives it away.
 *
 * The content pass is deliberately limited by [MAX_SCANNED_FILES] and
 * [MAX_SCANNED_BYTES] so a large project cannot stall the picker: binary files
 * and big assets are skipped by extension and size rather than read.
 */
object SecretScanner {

    /** Exact file names (lower-cased) that almost always hold credentials. */
    private val SENSITIVE_NAMES = setOf(
        ".env",
        ".netrc",
        ".npmrc",
        ".git-credentials",
        "id_rsa",
        "id_dsa",
        "id_ecdsa",
        "id_ed25519",
        "credentials",
        "credentials.json",
        "secrets.json",
        "secret.json",
        "secrets.yml",
        "secrets.yaml",
        "secrets.properties",
        "secret.properties",
        "keystore.properties",
        "local.properties",
        "google-services.json",
        "service-account.json",
        "release.keystore",
        "debug.keystore",
    )

    /** Extensions that identify private keys / signing material. */
    private val SENSITIVE_EXTENSIONS = setOf(
        "pem",
        "key",
        "p12",
        "pfx",
        "jks",
        "keystore",
        "ppk",
        "der",
        "pkcs12",
        "mobileprovision",
    )

    /** Names that, anywhere in the filename, hint at a secret. */
    private val SENSITIVE_HINTS = listOf("secret", "credential", "apikey", "api_key")

    /**
     * `.env` suffixes that are safe templates, not secrets. They are committed
     * by every real project (`.env.example` ships in the repo on purpose), so
     * they must never be held back.
     */
    private val SAFE_ENV_SUFFIXES = setOf("example", "sample", "template", "dist")

    /** True when [path] looks like it should not be committed. */
    fun isSensitive(path: String): Boolean {
        val name = path.substringAfterLast('/').lowercase()
        if (name.isBlank()) return false
        if (name == ".env" || name.startsWith(".env.")) {
            val suffix = name.removePrefix(".env.").substringBefore('.')
            return suffix !in SAFE_ENV_SUFFIXES
        }
        if (SENSITIVE_NAMES.contains(name)) return true
        val ext = name.substringAfterLast('.', "")
        if (ext.isNotEmpty() && SENSITIVE_EXTENSIONS.contains(ext)) return true
        return SENSITIVE_HINTS.any { name.contains(it) }
    }

    /** Every path in [entries] that looks sensitive by name only, sorted. */
    fun detect(entries: List<ZipFileEntry>): List<String> =
        entries.map { it.path }.filter { isSensitive(it) }.sorted()

    // -- content pass ----------------------------------------------------------

    /** Files above this size are not opened for content scanning. */
    private const val MAX_SCANNED_BYTES = 256 * 1024L

    /** Upper bound on how many files the content pass will open. */
    private const val MAX_SCANNED_FILES = 300

    /** Only these extensions are treated as text worth scanning. */
    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "json", "yml", "yaml", "properties", "xml",
        "env", "cfg", "conf", "ini", "toml", "gradle", "kts", "kt", "java",
        "js", "mjs", "cjs", "ts", "tsx", "jsx", "py", "go", "rb", "php", "sh",
        "bash", "zsh", "sql", "html", "htm", "csv", "pem", "key", "crt", "ovpn",
    )

    /**
     * Live-token shapes. Each pattern is anchored on a vendor prefix or an
     * assignment form so ordinary prose does not trip it.
     */
    private val CONTENT_PATTERNS: List<Regex> = listOf(
        // GitHub personal access tokens and app tokens.
        Regex("""gh[pousr]_[A-Za-z0-9]{20,}"""),
        Regex("""github_pat_[A-Za-z0-9_]{20,}"""),
        // AWS access key id / secret assignment.
        Regex("""\bAKIA[0-9A-Z]{16}\b"""),
        Regex("""aws_?secret_?access_?key\s*[=:]\s*\S{16,}""", RegexOption.IGNORE_CASE),
        // Google API keys, Slack tokens, Stripe live keys.
        Regex("""\bAIza[0-9A-Za-z_\-]{30,}\b"""),
        Regex("""xox[baprs]-[0-9A-Za-z\-]{10,}"""),
        Regex("""\b(?:sk|rk)_live_[0-9A-Za-z]{10,}\b"""),
        // PEM private key blocks.
        Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----"""),
        // Generic "api_key = <something long>" assignments in config files.
        Regex(
            """(?:api[_-]?key|apikey|secret[_-]?key|access[_-]?token|auth[_-]?token|password)\s*[=:]\s*['"]?[A-Za-z0-9/+_\-]{16,}""",
            RegexOption.IGNORE_CASE,
        ),
    )

    /** True when [text] contains anything that looks like a live credential. */
    fun looksLikeSecretContent(text: String): Boolean =
        CONTENT_PATTERNS.any { it.containsMatchIn(text) }

    /** Files worth reading during the content pass. */
    private fun isScannableText(entry: ZipFileEntry): Boolean {
        if (entry.size <= 0L || entry.size > MAX_SCANNED_BYTES) return false
        val name = entry.path.substringAfterLast('/').lowercase()
        if (name.isBlank()) return false
        val ext = name.substringAfterLast('.', "")
        // Extension-less files are usually scripts or dotfiles: scan those too.
        return ext.isEmpty() || TEXT_EXTENSIONS.contains(ext)
    }

    /**
     * Path pass plus a bounded content pass, run on [Dispatchers.IO] so the
     * regex sweep never blocks the UI thread.
     *
     * @param open returns the raw bytes for one path; implementations may read
     *   from memory (ZIP) or from SAF. Failures are swallowed — an unreadable
     *   file simply is not flagged by content.
     */
    suspend fun detectWithContent(
        entries: List<ZipFileEntry>,
        open: suspend (String) -> ByteArray,
    ): List<String> = withContext(Dispatchers.IO) {
        val flagged = LinkedHashSet<String>()

        // Pass 1: cheap, runs over every entry.
        entries.forEach { if (isSensitive(it.path)) flagged.add(it.path) }

        // Pass 2: bounded, only small text files not already flagged.
        var scanned = 0
        for (entry in entries) {
            if (scanned >= MAX_SCANNED_FILES) break
            if (flagged.contains(entry.path) || !isScannableText(entry)) continue
            scanned++
            val text = runCatching { String(open(entry.path), Charsets.UTF_8) }
                .getOrNull() ?: continue
            if (looksLikeSecretContent(text)) flagged.add(entry.path)
        }

        flagged.toList().sorted()
    }
}