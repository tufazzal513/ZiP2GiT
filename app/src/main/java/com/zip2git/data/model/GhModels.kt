package com.zip2git.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHub REST v3 payloads. Field names follow the GitHub API exactly; unknown
 * keys are ignored by the Json instance built in [com.zip2git.data.remote.NetworkModule],
 * and optional fields default to null so partial fixtures still parse.
 */

@Serializable
data class GhUserResponse(
    @SerialName("login") val login: String,
    @SerialName("name") val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("total_private_repos") val totalPrivateRepos: Int = 0,
)

@Serializable
data class GhPermissions(
    @SerialName("admin") val admin: Boolean = false,
    @SerialName("maintain") val maintain: Boolean = false,
    @SerialName("push") val push: Boolean = false,
    @SerialName("triage") val triage: Boolean = false,
    @SerialName("pull") val pull: Boolean = false,
)

@Serializable
data class GhRepoResponse(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("owner") val owner: GhUserResponse,
    @SerialName("private") val isPrivate: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    @SerialName("empty") val isEmpty: Boolean = false,
    @SerialName("permissions") val permissions: GhPermissions? = null,
)

@Serializable
data class GhBranchCommit(
    @SerialName("sha") val sha: String,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class GhBranchResponse(
    @SerialName("name") val name: String,
    @SerialName("commit") val commit: GhBranchCommit,
    @SerialName("protected") val isProtected: Boolean = false,
)

@Serializable
data class GhBranchSummary(
    @SerialName("name") val name: String,
    @SerialName("sha") val sha: String,
)

@Serializable
data class GhTreeRef(
    @SerialName("sha") val sha: String,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class GhCommitDetail(
    @SerialName("sha") val sha: String,
    @SerialName("tree") val tree: GhTreeRef,
    @SerialName("message") val message: String? = null,
)

@Serializable
data class GhTreeItem(
    @SerialName("path") val path: String,
    @SerialName("mode") val mode: String,
    @SerialName("type") val type: String,
    @SerialName("sha") val sha: String,
    @SerialName("size") val size: Long? = null,
)

@Serializable
data class GhTreeResponse(
    @SerialName("sha") val sha: String,
    @SerialName("tree") val tree: List<GhTreeItem> = emptyList(),
    @SerialName("truncated") val truncated: Boolean = false,
)

/**
 * One entry of a `POST /git/trees` request.
 *
 * A null [sha] means "delete this path from the base tree". The request body is
 * assembled by hand in [com.zip2git.data.remote.GitHubRepository.createTree]
 * because that null has to survive serialisation — see the note there.
 */
data class GhTreeEntryRequest(
    val path: String,
    val mode: String,
    val type: String,
    val sha: String? = null,
)

@Serializable
data class GhCreateBlobRequest(
    @SerialName("content") val content: String,
    @SerialName("encoding") val encoding: String = "base64",
)

@Serializable
data class GhBlobResponse(
    @SerialName("sha") val sha: String,
    @SerialName("size") val size: Long? = null,
)

@Serializable
data class GhCreateCommitRequest(
    @SerialName("message") val message: String,
    @SerialName("tree") val tree: String,
    @SerialName("parents") val parents: List<String> = emptyList(),
)

@Serializable
data class GhCommitResponse(
    @SerialName("sha") val sha: String,
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class GhCreateRefRequest(
    @SerialName("ref") val ref: String,
    @SerialName("sha") val sha: String,
)

@Serializable
data class GhUpdateRefRequest(
    @SerialName("sha") val sha: String,
    @SerialName("force") val force: Boolean = false,
)

/**
 * `GET|POST|PATCH /git/refs...` returns a **ref object**, not a commit: the new
 * tip lives at `object.sha` and there is no top-level `sha` field.
 */
@Serializable
data class GhRefObject(
    @SerialName("type") val type: String = "commit",
    @SerialName("sha") val sha: String,
    @SerialName("url") val url: String? = null,
)

@Serializable
data class GhRefResponse(
    @SerialName("ref") val ref: String,
    @SerialName("node_id") val nodeId: String? = null,
    @SerialName("url") val url: String? = null,
    @SerialName("object") val `object`: GhRefObject,
)

@Serializable
data class GhContentResponse(
    @SerialName("name") val name: String,
    @SerialName("path") val path: String,
    @SerialName("sha") val sha: String,
    @SerialName("size") val size: Long = 0,
    @SerialName("download_url") val downloadUrl: String? = null,
)

@Serializable
data class GhErrorResponse(
    @SerialName("message") val message: String = "",
    @SerialName("documentation_url") val documentationUrl: String? = null,
    @SerialName("status") val status: String? = null,
)

/** Non-serialisable error thrown by [com.zip2git.data.remote.GitHubRepository] on API failure. */
class GitHubApiException(
    val statusCode: Int,
    val apiMessage: String,
    val documentationUrl: String? = null,
) : Exception("GitHub API error ($statusCode): $apiMessage")
