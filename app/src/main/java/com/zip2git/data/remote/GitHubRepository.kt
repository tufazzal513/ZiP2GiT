package com.zip2git.data.remote

import com.zip2git.data.model.GhBranchResponse
import com.zip2git.data.model.GhBranchSummary
import com.zip2git.data.model.GhBlobResponse
import com.zip2git.data.model.GhCommitDetail
import com.zip2git.data.model.GhCommitResponse
import com.zip2git.data.model.GhCreateBlobRequest
import com.zip2git.data.model.GhCreateCommitRequest
import com.zip2git.data.model.GhCreateRefRequest
import com.zip2git.data.model.GhErrorResponse
import com.zip2git.data.model.GhPermissions
import com.zip2git.data.model.GhRefResponse
import com.zip2git.data.model.GhRepoResponse
import com.zip2git.data.model.GhTreeEntryRequest
import com.zip2git.data.model.GhTreeResponse
import com.zip2git.data.model.GhUpdateRefRequest
import com.zip2git.data.model.GhUserResponse
import com.zip2git.data.model.GitHubApiException
import com.zip2git.data.model.GitHubTreeItem
import com.zip2git.data.model.GitEntryType
import com.zip2git.data.model.RepoDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response

/**
 * Thin typed wrapper over [GitHubApi] that unwraps Retrofit responses, turns any
 * non-2xx response into a [GitHubApiException] carrying the API message, and maps
 * DTOs onto the domain models.
 */
class GitHubRepository(private val api: GitHubApi = NetworkModule.gitHubApi) {

    suspend fun getUser(): GhUserResponse = call { api.getUser() }

    suspend fun getRepoDetails(owner: String, repo: String): RepoDetails {
        val dto = call { api.getRepo(owner, repo) }
        return dto.toRepoDetails()
    }

    suspend fun listBranches(owner: String, repo: String, branchNames: List<String>): List<GhBranchSummary> =
        withContext(Dispatchers.IO) {
            branchNames.mapNotNull { name ->
                runCatching { getBranch(owner, repo, name) }
                    .getOrNull()
                    ?.let { GhBranchSummary(it.name, it.commit.sha) }
            }
        }

    suspend fun getBranch(owner: String, repo: String, branch: String): GhBranchResponse =
        call { api.getBranch(owner, repo, branch) }

    /** Returns the branch tip, or null when the repository has no such branch yet. */
    suspend fun getBranchOrNull(owner: String, repo: String, branch: String): GhBranchResponse? =
        withContext(Dispatchers.IO) {
            val response = api.getBranch(owner, repo, branch)
            if (response.isSuccessful) response.body() else null
        }

    suspend fun getCommitTreeSha(owner: String, repo: String, commitSha: String): String =
        call { api.getCommit(owner, repo, commitSha) }.tree.sha

    suspend fun getTree(owner: String, repo: String, treeSha: String): GhTreeResponse =
        call { api.getTree(owner, repo, treeSha) }

    /** Flattens a recursive tree into the domain [GitHubTreeItem] list (blobs only). */
    suspend fun listTreeFiles(owner: String, repo: String, treeSha: String): List<GitHubTreeItem> {
        val tree = getTree(owner, repo, treeSha)
        if (tree.truncated) {
            throw GitHubApiException(
                statusCode = 0,
                apiMessage = "Repository tree is too large to list in one request.",
            )
        }
        return tree.tree
            .filter { it.type == "blob" }
            .map {
                GitHubTreeItem(
                    path = it.path,
                    mode = it.mode,
                    type = GitEntryType.BLOB,
                    sha = it.sha,
                    size = it.size,
                )
            }
    }

    suspend fun createBlob(owner: String, repo: String, base64Content: String): GhBlobResponse =
        call { api.createBlob(owner, repo, GhCreateBlobRequest(content = base64Content)) }

    /**
     * `POST /git/trees` with a hand-built body.
     *
     * Serialising a DTO would drop `"sha": null` (the Json instance has
     * `explicitNulls = false`), and that key is exactly what marks a deletion, so
     * the JSON is written directly here.
     */
    suspend fun createTree(
        owner: String,
        repo: String,
        baseTree: String?,
        entries: List<GhTreeEntryRequest>,
    ): GhTreeResponse {
        val body = buildString {
            append('{')
            if (baseTree != null) {
                append("\"base_tree\":")
                append(NetworkModule.json.encodeToString(String.serializer(), baseTree))
                append(',')
            }
            append("\"tree\":[")
            entries.forEachIndexed { index, entry ->
                if (index > 0) append(',')
                append('{')
                append("\"path\":")
                append(NetworkModule.json.encodeToString(String.serializer(), entry.path))
                append(",\"mode\":")
                append(NetworkModule.json.encodeToString(String.serializer(), entry.mode))
                append(",\"type\":")
                append(NetworkModule.json.encodeToString(String.serializer(), entry.type))
                // A null sha is meaningful (delete the path) so it is always written.
                append(",\"sha\":")
                append(
                    entry.sha?.let { NetworkModule.json.encodeToString(String.serializer(), it) }
                        ?: "null",
                )
                append('}')
            }
            append("]}")
        }
        val requestBody = body.toRequestBody(JSON_MEDIA_TYPE)
        return call { api.createTree(owner, repo, requestBody) }
    }

    suspend fun createCommit(
        owner: String,
        repo: String,
        message: String,
        treeSha: String,
        parents: List<String>,
    ): GhCommitResponse =
        call { api.createCommit(owner, repo, GhCreateCommitRequest(message, treeSha, parents)) }

    suspend fun createRef(owner: String, repo: String, branch: String, sha: String): GhRefResponse =
        call { api.createRef(owner, repo, GhCreateRefRequest(ref = "refs/heads/$branch", sha = sha)) }

    suspend fun updateRef(
        owner: String,
        repo: String,
        branch: String,
        sha: String,
        force: Boolean = false,
    ): GhRefResponse =
        call { api.updateRef(owner, repo, branch, GhUpdateRefRequest(sha = sha, force = force)) }

    // -- helpers ---------------------------------------------------------------

    private suspend fun <T> call(block: () -> Response<T>): T {
        val response = withContext(Dispatchers.IO) { block() }
        if (response.isSuccessful) {
            return response.body() ?: throw GitHubApiException(
                statusCode = response.code(),
                apiMessage = "Empty response from GitHub.",
            )
        }
        throw response.toApiException()
    }

    private fun <T> Response<T>.toApiException(): GitHubApiException {
        val raw = runCatching { errorBody()?.string() }.getOrNull().orEmpty()
        val parsed = runCatching {
            NetworkModule.json.decodeFromString(GhErrorResponse.serializer(), raw)
        }.getOrNull()
        return GitHubApiException(
            statusCode = code(),
            apiMessage = parsed?.message?.takeIf { it.isNotBlank() }
                ?: "HTTP error ${code()}",
            documentationUrl = parsed?.documentationUrl,
        )
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

private fun GhRepoResponse.toRepoDetails(): RepoDetails {
    val perms: GhPermissions? = permissions
    return RepoDetails(
        owner = owner.login,
        name = name,
        defaultBranch = defaultBranch.ifBlank { "main" },
        isPrivate = isPrivate,
        permissions = perms,
    )
}

/** Convenience: the tip sha for a branch response. */
val GhBranchResponse.tipSha: String get() = commit.sha
