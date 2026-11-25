package com.zip2git.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zip2git.data.model.SyncProgress
import com.zip2git.data.model.SyncStep
import com.zip2git.ui.MainUiState

/**
 * Live progress while the Git Data API work runs: blobs, tree, commit, ref.
 * Cancel is only offered before the first commit is created, because once the
 * commit exists the ref update must be allowed to finish.
 */
@Composable
fun SyncScreen(
    state: MainUiState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = state.syncProgress ?: SyncProgress(SyncStep.IDLE, "Starting…")
    val steps = listOf(
        SyncStep.VALIDATING_REPO to "Validate source & repository",
        SyncStep.READING_ZIP to "Read project files",
        SyncStep.FETCHING_TREE to "Compare with remote tree",
        SyncStep.SYNCING_BLOBS to "Upload changed files",
        SyncStep.BUILDING_TREE to "Build new tree",
        SyncStep.CREATING_COMMIT to "Create commit & update branch",
        SyncStep.DONE to "Push to GitHub",
    )
    val currentIndex = steps.indexOfFirst { it.first == progress.step }
    val fraction = when {
        progress.total > 0 -> progress.processed.toFloat() / progress.total.toFloat()
        progress.step == SyncStep.DONE -> 1f
        currentIndex <= 0 -> 0.08f
        else -> (currentIndex + 0.5f) / steps.size.toFloat()
    }.coerceIn(0.02f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Pushing to GitHub",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = progress.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(26.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                )
                Spacer(Modifier.height(18.dp))
                steps.forEachIndexed { index, (step, label) ->
                    val done = index < currentIndex || progress.step == SyncStep.DONE
                    val active = index == currentIndex && progress.step != SyncStep.DONE
                    StepRow(
                        label = label,
                        done = done,
                        active = active,
                        detail = if (active && progress.total > 0) {
                            "${progress.processed}/${progress.total}"
                        } else {
                            null
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = "${state.comparison?.let { it.added.size + it.modified.size } ?: 0} file(s) to upload",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Cancel push")
        }
    }
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean, detail: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            done -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )

            active -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )

            else -> Icon(
                imageVector = Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp),
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                done || active -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )

        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Success screen shown after a completed push. */
@Composable
fun ResultScreen(
    state: MainUiState,
    onSyncAnother: () -> Unit,
    onBackToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val result = state.syncResult
    val error = state.syncError
    val success = result != null && error == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    if (success) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (success) Icons.Filled.Done else Icons.Filled.CloudUpload,
                contentDescription = null,
                tint = if (success) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (success) "Successfully Pushed to GitHub!" else (error?.title ?: "Push failed"),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (success) {
                "${result?.filesSynchronized ?: 0} file(s) synchronized to " +
                    "${state.repo?.owner}/${state.repo?.name}@${state.selectedBranch}"
            } else {
                error?.message.orEmpty()
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailRow("Repository", "${state.repo?.owner}/${state.repo?.name}")
                DetailRow("Branch", state.selectedBranch, highlight = result?.isNewBranch == true)
                if (success) {
                    DetailRow("Files", "${result?.filesSynchronized ?: 0}")
                    DetailRow(
                        label = "Commit",
                        value = result?.commitSha?.take(10).orEmpty(),
                    )
                }
                if (result?.isEmptyRepo == true) {
                    Text(
                        text = "The repository was empty, so the first commit was created for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(26.dp))

        androidx.compose.material3.Button(
            onClick = onSyncAnother,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (success) "Sync Another" else "Try Again", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onBackToDashboard,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Back to Dashboard")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = Modifier.weight(1f),
        )
    }
}
