package com.zip2git.domain

import android.util.Base64
import com.zip2git.data.model.CompareOptions
import com.zip2git.data.model.GhTreeEntryRequest
import com.zip2git.data.model.SyncProgress
import com.zip2git.data.model.SyncStep
import com.zip2git.data.remote.GitHubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Raised when the branch moved under us between reading it and updating it. */
class RepositoryChangedException : Exception(
    "Repository changed while syncing. No changes were pushed. Please sync again.",
)

/**
 * Applies a [ProjectSource] to a GitHub branch using the Git Data API:
 *
 *  1. resolve the branch tip (or fall back to the default branch / empty-repo path)
 *  2. upload changed files as blobs
 *  3. build one tree on top of the base tree (deletions send a null sha)
 *  4. create a commit and advance the branch ref
 *
 * A guard re-reads the branch tip immediately before the ref update so a push
 * that raced with another writer fails cleanly instead of losing commits.
 */
class SyncEngine(private val repo: GitHubRepository) {

    data class Plan(
        val baseCommitSha: String?,
        val baseTreeSha: String?,
        val baseParents: List<String>,
        val isNewBranch: Boolean,
        val isEmptyRepo: Boolean,
    )

    suspend fun resolvePlan(
        owner: String,
        repoName: String,
        branch: String,
        defaultBranch: String,
        isEmptyRepo: Boolean,
    ): Plan = withContext(Dispatchers.IO) {
        if (isEmptyRepo) {
            return@withContext Plan(
                baseCommitSha = null,
                baseTreeSha = null,
                baseParents = emptyList(),
                isNewBranch = true,
                isEmptyRepo = true,
            )
        }

        repo.getBranchOrNull(owner, repoName, branch)?.let { branchResp ->
            val treeSha = repo.getCommitTreeSha(owner, repoName, branchResp.commit.sha)
            return@withContext Plan(
                baseCommitSha = branchResp.commit.sha,
                baseTreeSha = treeSha,
                baseParents = listOf(branchResp.commit.sha),
                isNewBranch = false,
                isEmptyRepo = false,
            )
        }

        // Branch missing: fork it from the default branch tip when that exists.
        val defaultTip = repo.getBranchOrNull(owner, repoName, defaultBranch)
        if (defaultTip == null) {
            // Neither the target nor the default branch exists — a brand-new repo.
            // The first commit is created without parents and the ref is created.
            return@withContext Plan(
                baseCommitSha = null,
                baseTreeSha = null,
                baseParents = emptyList(),
                isNewBranch = true,
                isEmptyRepo = true,
            )
        }

        val treeSha = repo.getCommitTreeSha(owner, repoName, defaultTip.commit.sha)
        Plan(
            baseCommitSha = defaultTip.commit.sha,
            baseTreeSha = treeSha,
            baseParents = listOf(defaultTip.commit.sha),
            isNewBranch = true,
            isEmptyRepo = false,
        )
    }

    suspend fun sync(
        owner: String,
        repoName: String,
        branch: String,
        commitMessage: String,
        source: ProjectSource,
        options: CompareOptions,
        defaultBranch: String,
        isEmptyRepo: Boolean,
        onProgress: suspend (SyncProgress) -> Unit,
    ): SyncOutcome = withContext(Dispatchers.IO) {
        val plan = resolvePlan(owner, repoName, branch, defaultBranch, isEmptyRepo)

        onProgress(
            SyncProgress(
                step = SyncStep.READING_ZIP,
                message = when (source.sourceType) {
                    com.zip2git.data.model.SourceType.ZIP -> "Extracting files from ZIP..."
                    com.zip2git.data.model.SourceType.FOLDER -> "Scanning folder files..."
                    com.zip2git.data.model.SourceType.FILES -> "Reading selected files..."
                },
            ),
        )

        onProgress(
            SyncProgress(
                step = SyncStep.FETCHING_TREE,
                message = "Calculating file hashes & comparing tree...",
            ),
        )

        val remoteTree = plan.baseTreeSha
            ?.let { repo.listTreeFiles(owner, repoName, it) }
            ?: emptyList()

        val comparison = DiffEngine.compare(
            localFiles = source.entries,
            remoteTree = remoteTree,
            options = options,
        )

        if (!comparison.hasChanges) {
            return@withContext SyncOutcome(
                commitSha = plan.baseCommitSha.orEmpty(),
                commitUrl = null,
                filesSynchronized = 0,
                isNewBranch = plan.isNewBranch,
                isEmptyRepo = plan.isEmptyRepo,
                nothingToDo = true,
            )
        }

        val toUpload = comparison.added + comparison.modified
        val total = toUpload.size
        val blobShas = HashMap<String, String>(total)

        onProgress(
            SyncProgress(
                step = SyncStep.SYNCING_BLOBS,
                message = "Uploading files (0/$total)...",
                processed = 0,
                total = total,
            ),
        )

        toUpload.forEachIndexed { index, file ->
            val bytes = source.open(file.path)
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val blob = repo.createBlob(owner, repoName, encoded)
            blobShas[file.path] = blob.sha
            onProgress(
                SyncProgress(
                    step = SyncStep.SYNCING_BLOBS,
                    message = "Uploading files (${index + 1}/$total)...",
                    processed = index + 1,
                    total = total,
                ),
            )
        }

        onProgress(SyncProgress(step = SyncStep.BUILDING_TREE, message = "Building Git tree..."))

        val treeEntries = ArrayList<GhTreeEntryRequest>()
        for (file in toUpload) {
            val sha = blobShas[file.path] ?: continue
            treeEntries.add(
                GhTreeEntryRequest(
                    path = file.path,
                    mode = GitTreeMode.FILE,
                    type = GitTreeMode.BLOB,
                    sha = sha,
                ),
            )
        }
        for (deleted in comparison.deleted) {
            treeEntries.add(
                GhTreeEntryRequest(
                    path = deleted,
                    mode = GitTreeMode.FILE,
                    type = GitTreeMode.BLOB,
                    sha = null, // null sha deletes the path from the base tree
                ),
            )
        }

        val newTree = repo.createTree(owner, repoName, plan.baseTreeSha, treeEntries)

        onProgress(SyncProgress(step = SyncStep.CREATING_COMMIT, message = "Creating commit..."))

        val commit = repo.createCommit(
            owner = owner,
            repo = repoName,
            message = commitMessage.ifBlank { DEFAULT_COMMIT_MESSAGE },
            treeSha = newTree.sha,
            parents = plan.baseParents,
        )

        // Concurrency guard: the branch must still point where we last saw it.
        if (!plan.isEmptyRepo && !plan.isNewBranch) {
            val currentTip = repo.getBranchOrNull(owner, repoName, branch)?.commit?.sha
            if (currentTip != plan.baseCommitSha) {
                throw RepositoryChangedException()
            }
            onProgress(
                SyncProgress(
                    step = SyncStep.CREATING_COMMIT,
                    message = "Updating branch \"$branch\"...",
                ),
            )
            repo.updateRef(owner, repoName, branch, commit.sha, force = false)
        } else {
            onProgress(
                SyncProgress(
                    step = SyncStep.CREATING_COMMIT,
                    message = "Creating branch \"$branch\"...",
                ),
            )
            repo.createRef(owner, repoName, branch, commit.sha)
        }

        onProgress(SyncProgress(step = SyncStep.DONE, message = "Successfully Pushed to GitHub!"))

        SyncOutcome(
            commitSha = commit.sha,
            commitUrl = commit.htmlUrl,
            filesSynchronized = total + comparison.deleted.size,
            isNewBranch = plan.isNewBranch,
            isEmptyRepo = plan.isEmptyRepo,
            nothingToDo = false,
        )
    }

    private companion object {
        const val DEFAULT_COMMIT_MESSAGE = "Initial commit via Zip2Git"
    }
}

/** Result of a completed sync run. */
data class SyncOutcome(
    val commitSha: String,
    val commitUrl: String?,
    val filesSynchronized: Int,
    val isNewBranch: Boolean,
    val isEmptyRepo: Boolean,
    val nothingToDo: Boolean,
)
