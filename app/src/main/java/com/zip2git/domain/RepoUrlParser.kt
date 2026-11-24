package com.zip2git.domain

/** Owner/name pair parsed from a GitHub repository URL. */
data class RepositoryCoordinates(val owner: String, val name: String)

/**
 * Parses the URL forms the app accepts:
 *  - `https://github.com/owner/repo`
 *  - `https://github.com/owner/repo.git`
 *  - `https://www.github.com/owner/repo`
 *  - `github.com/owner/repo`
 *  - `owner/repo`
 *  - an optional trailing `/tree/branch` or `/branches` is ignored
 */
object RepoUrlParser {

    private val URL_REGEX = Regex(
        """^(?:https?://)?(?:www\.)?github\.com/([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(?:\.git)?(?:/.*)?$""",
        RegexOption.IGNORE_CASE,
    )

    private val SHORT_REGEX = Regex(
        """^([A-Za-z0-9._-]+)/([A-Za-z0-9._-]+?)(?:\.git)?$""",
    )

    fun parseOrNull(input: String): RepositoryCoordinates? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null

        URL_REGEX.matchEntire(trimmed)?.let { match ->
            val owner = match.groupValues[1]
            val name = match.groupValues[2]
            if (owner.isNotBlank() && name.isNotBlank()) return RepositoryCoordinates(owner, name)
        }
        SHORT_REGEX.matchEntire(trimmed)?.let { match ->
            return RepositoryCoordinates(match.groupValues[1], match.groupValues[2])
        }
        return null
    }

    fun isValid(input: String): Boolean = parseOrNull(input) != null
}
