package com.zip2git.data.remote

import com.zip2git.data.model.GhBlobResponse
import com.zip2git.data.model.GhBranchResponse
import com.zip2git.data.model.GhCommitDetail
import com.zip2git.data.model.GhCommitResponse
import com.zip2git.data.model.GhCreateBlobRequest
import com.zip2git.data.model.GhCreateCommitRequest
import com.zip2git.data.model.GhCreateRefRequest
import com.zip2git.data.model.GhRepoResponse
import com.zip2git.data.model.GhRefResponse
import com.zip2git.data.model.GhTreeResponse
import com.zip2git.data.model.GhUpdateRefRequest
import com.zip2git.data.model.GhUserResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit mirror of the GitHub Git Data + Repos REST API surface the app uses.
 * Endpoint paths match the recovered DEX constants exactly.
 */
interface GitHubApi {

    /** `GET /user` — validates the PAT and returns the signed-in account. */
    @GET("user")
    suspend fun getUser(): Response<GhUserResponse>

    /** `GET /repos/{owner}/{repo}` — repository metadata, permissions and default branch. */
    @GET("repos/{owner}/{repo}")
    suspend fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<GhRepoResponse>

    /** `GET /repos/{owner}/{repo}/branches/{branch}` — tip commit for a branch. */
    @GET("repos/{owner}/{repo}/branches/{branch}")
    suspend fun getBranch(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
    ): Response<GhBranchResponse>

    /** `GET /repos/{owner}/{repo}/git/trees/{sha}` — full recursive tree. */
    @GET("repos/{owner}/{repo}/git/trees/{sha}")
    suspend fun getTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String,
        @Query("recursive") recursive: String = "1",
    ): Response<GhTreeResponse>

    /** `GET /repos/{owner}/{repo}/git/commits/{sha}` — resolve a commit's tree. */
    @GET("repos/{owner}/{repo}/git/commits/{sha}")
    suspend fun getCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("sha") sha: String,
    ): Response<GhCommitDetail>

    /** `POST /repos/{owner}/{repo}/git/blobs` — upload one file's base64 content. */
    @POST("repos/{owner}/{repo}/git/blobs")
    suspend fun createBlob(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GhCreateBlobRequest,
    ): Response<GhBlobResponse>

    /**
     * `POST /repos/{owner}/{repo}/git/trees` — build a tree, overriding [baseTree].
     *
     * The body is a pre-serialised [RequestBody] because a deletion entry must send
     * `"sha": null`; a @Serializable DTO with `explicitNulls = false` would drop the
     * key entirely and GitHub would then reject the entry.
     */
    @POST("repos/{owner}/{repo}/git/trees")
    suspend fun createTree(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: RequestBody,
    ): Response<GhTreeResponse>

    /** `POST /repos/{owner}/{repo}/git/commits` — create a commit on top of [parents]. */
    @POST("repos/{owner}/{repo}/git/commits")
    suspend fun createCommit(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GhCreateCommitRequest,
    ): Response<GhCommitResponse>

    /** `POST /repos/{owner}/{repo}/git/refs` — create a branch; returns the new ref. */
    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: GhCreateRefRequest,
    ): Response<GhRefResponse>

    /** `PATCH /repos/{owner}/{repo}/git/refs/heads/{branch}` — advance a branch. */
    @PATCH("repos/{owner}/{repo}/git/refs/heads/{branch}")
    suspend fun updateRef(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("branch") branch: String,
        @Body body: GhUpdateRefRequest,
    ): Response<GhRefResponse>
}
