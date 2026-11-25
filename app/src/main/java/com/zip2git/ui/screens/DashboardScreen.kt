package com.zip2git.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.zip2git.data.model.ChangeStatus
import com.zip2git.data.model.FileComparison
import com.zip2git.data.model.RepoSummary
import com.zip2git.data.model.SourceType
import com.zip2git.data.model.SyncErrorInfo
import com.zip2git.ui.MainUiState
import com.zip2git.ui.components.ChangeColors
import com.zip2git.ui.components.CountPill
import com.zip2git.ui.components.MessageBanner
import com.zip2git.ui.components.SectionCard
import java.util.Locale

/** Success green used by the connected badge and checkmark (original app). */
internal val ConnectedGreen = Color(0xFF22C55E)

/** The deep blue banner behind the "Connected as @..." line (original app). */
internal val ConnectedBlue = Color(0xFF0B3FB5)
/**
 * The single scrolling "New Git Push" form, matching the original app: one card
 * for repository + token + branch + commit message, then the project files card,
 * the advanced options card, the "Inspect Changes & Validate" action, the change
 * review that appears after inspecting, and finally the push action and history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: MainUiState,
    onTokenChange: (String) -> Unit,
    onTestToken: () -> Unit,
    onSelectRepo: (RepoSummary) -> Unit,
    onRefreshRepos: () -> Unit,
    onRepoUrlChange: (String) -> Unit,
    onTargetFolderChange: (String) -> Unit,
    onPickZip: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onClearSource: () -> Unit,
    onBranchChange: (String) -> Unit,
    onRefreshBranches: () -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onMirrorSyncChange: (Boolean) -> Unit,
    onInspectChanges: () -> Unit,
    onRefreshComparison: () -> Unit,
    onTogglePathExcluded: (String) -> Unit,
    onClearExcluded: () -> Unit,
    onStartSync: () -> Unit,
    onToggleTheme: () -> Unit,
    onDismissBanner: () -> Unit,
    onDismissError: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onKeepImportantChange: (Boolean) -> Unit,
    onStripRootChange: (Boolean) -> Unit,
    onAddFilterRule: (String) -> Unit,
    onRemoveFilterRule: (String) -> Unit,
    onToggleFilterRule: (String) -> Unit,
    onHideSecretsChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "New Git Push",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (state.connectedLogin.isNotBlank()) {
                            ConnectedPill(login = state.connectedLogin)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (state.darkTheme) {
                                Icons.Filled.LightMode
                            } else {
                                Icons.Filled.DarkMode
                            },
                            contentDescription = "Switch to Dark/Light Mode",
                        )
                    }
                    IconButton(onClick = { showAbout = !showAbout }) {
                        Icon(Icons.Filled.Info, contentDescription = "About Zip2Git")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.banner?.let { banner ->
                item {
                    MessageBanner(
                        title = "Notice",
                        message = banner,
                        isError = false,
                        onDismiss = onDismissBanner,
                    )
                }
            }

            state.syncError?.let { error ->
                item { ErrorCard(error = error, onDismiss = onDismissError) }
            }

            if (showAbout) {
                item { AboutCard(onClose = { showAbout = false }) }
            }

            // 1. Target Repository
            item {
                TargetRepositoryCard(
                    state = state,
                    onSelectRepo = onSelectRepo,
                    onRefreshRepos = onRefreshRepos,
                    onRepoUrlChange = onRepoUrlChange,
                    onTargetFolderChange = onTargetFolderChange,
                )
            }

            // 2. GitHub Authentication
            item {
                AuthenticationCard(
                    state = state,
                    onTokenChange = onTokenChange,
                    onTestToken = onTestToken,
                )
            }

            // 3. Branch & Commit Details
            item {
                BranchAndCommitCard(
                    state = state,
                    onBranchChange = onBranchChange,
                    onRefreshBranches = onRefreshBranches,
                    onCommitMessageChange = onCommitMessageChange,
                )
            }

            // 4. Project Files
            item {
                ProjectFilesCard(
                    state = state,
                    onPickZip = onPickZip,
                    onPickFolder = onPickFolder,
                    onPickFiles = onPickFiles,
                    onClearSource = onClearSource,
                )
            }

            // 5. Advanced Options
            item {
                AdvancedOptionsCard(
                    state = state,
                    onToggle = onToggleAdvanced,
                    onMirrorSyncChange = onMirrorSyncChange,
                    onKeepImportantChange = onKeepImportantChange,
                    onStripRootChange = onStripRootChange,
                    onAddFilterRule = onAddFilterRule,
                    onRemoveFilterRule = onRemoveFilterRule,
                    onToggleFilterRule = onToggleFilterRule,
                    onHideSecretsChange = onHideSecretsChange,
                )
            }

            // Inline progress card while the comparison / push validation runs.
            if (state.isProcessingZip || (state.syncProgress != null && state.comparison == null)) {
                item { InlineProgressCard(state = state) }
            }

            // 6. Inspect Changes & Validate
            item { InspectButton(state = state, onInspectChanges = onInspectChanges) }

            // 7. Change Summary, revealed after inspecting; hosts the push action.
            if (state.comparison != null ||
                (state.showReview && state.syncProgress == null && !state.isProcessingZip)
            ) {
                item {
                    ReviewCard(
                        comparison = state.comparison,
                        progress = state.syncProgress,
                        excludedPaths = state.excludedPaths,
                        secretPaths = state.secretPaths,
                        onRefresh = onRefreshComparison,
                        onClearExcluded = onClearExcluded,
                        onStartSync = onStartSync,
                        isSyncing = state.isSyncing,
                    )
                }
            }
        }
    }
}

/** The small card that appears while a comparison/validation stage is running. */
@Composable
private fun InlineProgressCard(state: MainUiState) {
    val message = when {
        state.isProcessingZip && state.sourceType == SourceType.FOLDER -> "Scanning folder files..."
        state.isProcessingZip -> "Extracting files from ZIP..."
        else -> state.syncProgress?.message ?: "Checking GitHub repository & permissions..."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TargetRepositoryCard(
    state: MainUiState,
    onSelectRepo: (RepoSummary) -> Unit,
    onRefreshRepos: () -> Unit,
    onRepoUrlChange: (String) -> Unit,
    onTargetFolderChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val repos = state.availableRepos
    val label = if (repos.isEmpty()) {
        "Select from your repos"
    } else {
        "Select from your repos (${repos.size})"
    }

    SectionCard(
        title = "Target Repository",
        trailing = {
            IconButton(onClick = onRefreshRepos, enabled = !state.isFetchingRepos) {
                if (state.isFetchingRepos) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh repositories list")
                }
            }
        },
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (repos.isNotEmpty()) expanded = it },
        ) {
            OutlinedTextField(
                value = state.repo?.let { "${it.owner}/${it.name}" }.orEmpty(),
                onValueChange = {},
                readOnly = true,
                placeholder = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                shape = RoundedCornerShape(14.dp),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                repos.forEach { summary ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = if (summary.isPrivate) {
                                        "${summary.fullName}  🔒"
                                    } else {
                                        summary.fullName
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = summary.defaultBranch,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            onSelectRepo(summary)
                            expanded = false
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.repoUrlInput,
            onValueChange = onRepoUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GitHub Repository URL") },
            placeholder = { Text("https://github.com/owner/repository") },
            singleLine = true,
            enabled = !state.isInitializingRepo,
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.targetFolder,
            onValueChange = onTargetFolderChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Target folder in repo (optional)") },
            placeholder = { Text("e.g. android  (uploads land in android/)") },
            singleLine = true,
            enabled = !state.isInitializingRepo,
            shape = RoundedCornerShape(14.dp),
        )

        if (state.isInitializingRepo) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun AuthenticationCard(
    state: MainUiState,
    onTokenChange: (String) -> Unit,
    onTestToken: () -> Unit,
) {
    var tokenVisible by remember { mutableStateOf(false) }

    SectionCard(title = "GitHub Authentication") {
        OutlinedTextField(
            value = state.tokenInput,
            onValueChange = onTokenChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Personal Access Token (PAT)") },
            placeholder = {
                Text(if (state.tokenIsSet) "Token saved — leave blank to reuse" else "ghp_...")
            },
            singleLine = true,
            enabled = !state.isValidatingToken,
            visualTransformation = if (tokenVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        imageVector = if (tokenVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = if (tokenVisible) "Hide token" else "Show token",
                    )
                }
            },
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onTestToken,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !state.isValidatingToken,
            shape = RoundedCornerShape(50),
        ) {
            if (state.isValidatingToken) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Verifying Token...")
            } else {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Test Token & Load Repos",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        if (state.tokenIsSet && state.connectedLogin.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = ConnectedBlue,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = ConnectedGreen,
                        modifier = Modifier.size(17.dp),
                    )
                    Column {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Connected as ")
                                }
                                append("@${state.connectedLogin}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                        if (state.availableRepos.isNotEmpty()) {
                            Text(
                                text = "@${state.connectedLogin} (${state.availableRepos.size} repos found)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                        }
                    }
                }
            }
        }

        (state.repoValidationMessage ?: state.tokenValidationMessage)?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BranchAndCommitCard(
    state: MainUiState,
    onBranchChange: (String) -> Unit,
    onRefreshBranches: () -> Unit,
    onCommitMessageChange: (String) -> Unit,
) {
    SectionCard(title = "Branch & Commit Details") {
        OutlinedTextField(
            value = state.selectedBranch,
            onValueChange = onBranchChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Branch") },
            placeholder = { Text("main") },
            singleLine = true,
            enabled = !state.isSyncing,
            trailingIcon = {
                IconButton(onClick = onRefreshBranches, enabled = !state.isFetchingBranches) {
                    if (state.isFetchingBranches) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Fetch branches")
                    }
                }
            },
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Available branches:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))

        if (state.repoBranches.isEmpty()) {
            Text(
                text = "None loaded yet — type a new name to create one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.repoBranches.forEach { branch ->
                    BranchChip(
                        name = branch.name,
                        selected = branch.name == state.selectedBranch,
                        onClick = { onBranchChange(branch.name) },
                    )
                }
            }
        }

        if (state.repoBranches.isNotEmpty() &&
            state.repoBranches.none { it.name == state.selectedBranch }
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Creating branch \"${state.selectedBranch}\" — it does not exist yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.commitMessage,
            onValueChange = onCommitMessageChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Commit Message") },
            placeholder = { Text("Sync project files via Zip2Git") },
            singleLine = true,
            enabled = !state.isSyncing,
            shape = RoundedCornerShape(14.dp),
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Examples: \"Initial project upload\", \"Update frontend UI\", \"Fix login bug\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BranchChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = if (selected) {
            null
        } else {
            androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        },
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
internal fun ProjectFilesCard(
    state: MainUiState,
    onPickZip: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onClearSource: () -> Unit,
) {
    SectionCard(title = "Project Files") {
        if (state.sourceName.isNotBlank()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = when (state.sourceType) {
                            SourceType.FOLDER -> Icons.Filled.FolderOpen
                            SourceType.FILES -> Icons.Filled.Description
                            else -> Icons.Filled.FolderZip
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = state.sourceName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${state.totalZipFiles} files • ${formatBytes(state.totalZipBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClearSource) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear selection",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClearSource) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text("Choose a different source")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Filled.UploadFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pick a ZIP, Folder, or individual files to upload",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickButton(
                    text = "ZIP",
                    icon = Icons.Filled.FolderZip,
                    enabled = !state.isProcessingZip,
                    onClick = onPickZip,
                    modifier = Modifier.weight(1f),
                )
                PickButton(
                    text = "Folder",
                    icon = Icons.Filled.FolderOpen,
                    enabled = !state.isProcessingZip,
                    onClick = onPickFolder,
                    modifier = Modifier.weight(1f),
                )
                PickButton(
                    text = "Files",
                    icon = Icons.Filled.Description,
                    enabled = !state.isProcessingZip,
                    onClick = onPickFiles,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun PickButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = text == "ZIP"
    Surface(
        modifier = modifier
            .height(44.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (primary) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (primary) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/** Renders a byte count the way the original app does: "6.1 MB", "12 files". */
internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    val rounded = if (index == 0) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return "$rounded ${units[index]}"
}

@Composable
internal fun AdvancedOptionsCard(
    state: MainUiState,
    onToggle: () -> Unit,
    onMirrorSyncChange: (Boolean) -> Unit,
    onKeepImportantChange: (Boolean) -> Unit,
    onStripRootChange: (Boolean) -> Unit,
    onAddFilterRule: (String) -> Unit,
    onRemoveFilterRule: (String) -> Unit,
    onToggleFilterRule: (String) -> Unit,
    onHideSecretsChange: (Boolean) -> Unit,
) {
    SectionCard(
        title = "Advanced Options",
    ) {
        OptionSwitch(
            title = "Smart File Filter",
            detail = "Auto-excludes build files, node_modules, .gradle, and OS junk " +
                "(.DS_Store, Thumbs.db).",
            checked = state.filterRules.any { it.enabled },
            onCheckedChange = { enabled ->
                state.filterRules.forEach { rule ->
                    if (rule.enabled != enabled) onToggleFilterRule(rule.pattern)
                }
            },
        )

        Spacer(Modifier.height(10.dp))

        OptionSwitch(
            title = "Mirror Sync (exact match)",
            detail = "Deletes files in the repo that aren't present in the ZIP.",
            checked = state.mirrorSync,
            onCheckedChange = onMirrorSyncChange,
        )

        Spacer(Modifier.height(10.dp))

        OptionSwitch(
            title = "Preserve Metadata",
            detail = "Keeps README, LICENSE, .github/, .gitignore even if missing from the ZIP.",
            checked = state.keepImportantFiles,
            onCheckedChange = onKeepImportantChange,
        )

        Spacer(Modifier.height(10.dp))

        // A secret-file detection hit is a safety signal, so the switch stays
        // visible in the always-open part of the card.
        if (state.secretPaths.isNotEmpty()) {
            OptionSwitch(
                title = "Exclude secret files",
                detail = "${state.secretPaths.size} credential-looking file(s) detected " +
                    "(.env, *.pem, *.key, ...). Kept off the push while on.",
                checked = state.hideSecrets,
                onCheckedChange = onHideSecretsChange,
            )
            Spacer(Modifier.height(10.dp))
        }

        MoreOptionsSection(expanded = state.showAdvanced, onToggle = onToggle) {
            OptionSwitch(
                title = "Strip root folder",
                detail = "Remove the single top-level folder when importing a ZIP " +
                    "(ZIP root: no folder stripped).",
                checked = state.stripRootFolder,
                onCheckedChange = onStripRootChange,
            )

            Spacer(Modifier.height(16.dp))
            Text("Filter rules", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "KEEP keeps a match; SKIP drops it from the push.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            if (state.filterRules.isEmpty()) {
                Text(
                    text = "No rules yet. Add one below, e.g. build/ or *.log",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.filterRules.forEach { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggleFilterRule(rule.pattern) },
                            modifier = Modifier.scale(0.8f),
                        )
                        Text(
                            text = rule.pattern,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (rule.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = rule.decision.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(
                            onClick = { onRemoveFilterRule(rule.pattern) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove ${rule.pattern}",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            AddRuleRow(onAdd = onAddFilterRule)
        }
    }
}

/**
 * Reveals the secondary options (strip root folder, filter rules). The three
 * primary switches above stay always-open; secrets stay visible too, so this only
 * hides the rarely-touched extras.
 */
@Composable
private fun MoreOptionsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "More options",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
        )
    }
    if (expanded) {
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun OptionSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun AddRuleRow(onAdd: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            label = { Text("Pattern (e.g. *.log)") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
        )
        Button(
            onClick = {
                onAdd(input)
                input = ""
            },
            enabled = input.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }
}

@Composable
internal fun InspectButton(state: MainUiState, onInspectChanges: () -> Unit) {
    val isInspecting = state.syncProgress != null && !state.isSyncing
    val enabled = state.sourceName.isNotBlank() &&
        state.selectedBranch.isNotBlank() &&
        !state.isProcessingZip &&
        !isInspecting
    // The inline progress card already covers the first inspect (no comparison
    // yet); the button only owns the spinner when re-inspecting an existing diff.
    val showSpinner = isInspecting && state.comparison != null

    Button(
        onClick = onInspectChanges,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        enabled = enabled,
        shape = RoundedCornerShape(50),
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.size(19.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = "Inspect Changes & Validate",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
internal fun ReviewCard(
    comparison: FileComparison?,
    progress: com.zip2git.data.model.SyncProgress?,
    excludedPaths: Set<String>,
    secretPaths: List<String>,
    onRefresh: () -> Unit,
    onClearExcluded: () -> Unit,
    onStartSync: (() -> Unit)?,
    isSyncing: Boolean,
) {
    SectionCard(
        title = "Change Summary",
        trailing = {
            IconButton(onClick = onRefresh, enabled = comparison != null) {
                Icon(Icons.Filled.Refresh, contentDescription = "Recompare")
            }
        },
    ) {
        if (progress != null && comparison == null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(progress.message, style = MaterialTheme.typography.bodyMedium)
            }
            return@SectionCard
        }

        if (comparison == null) {
            Text(
                text = "Please re-run file comparison before synchronizing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CountPill("Added", comparison.added.size, ChangeColors.Added, sign = "+")
            CountPill("Modified", comparison.modified.size, ChangeColors.Modified, sign = "~")
            CountPill("Deleted", comparison.deleted.size, ChangeColors.Deleted, sign = "-")
            Text(
                text = "=${comparison.unchanged.size} Unchanged",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val changes = com.zip2git.domain.DiffEngine.toChangeList(comparison)
        if (changes.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Everything is already up to date.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        if (secretPaths.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = "Secret files held back",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "These look like credentials and will NOT be pushed while " +
                            "\"Exclude secret files\" is on:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    secretPaths.take(6).forEach { path ->
                        Text(
                            text = "•  $path",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (secretPaths.size > 6) {
                        Text(
                            text = "…and ${secretPaths.size - 6} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        if (excludedPaths.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${excludedPaths.size} file(s) skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClearExcluded) {
                    Text("Clear selection")
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // The original shows a plain path list and truncates it, so a large project
        // never pushes the push button below the fold.
        val visible = changes.take(MAX_VISIBLE_CHANGES)
        visible.forEach { change ->
            PlainChangeRow(change = change)
        }
        val hidden = changes.size - visible.size
        if (hidden > 0) {
            Text(
                text = "+ $hidden more files...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (onStartSync != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onStartSync,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isSyncing,
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isSyncing) {
                        "Pushing to GitHub..."
                    } else {
                        "Push Commit (${changes.size} ${if (changes.size == 1) "change" else "changes"})"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** How many changed paths the Change Summary lists before truncating. */
private const val MAX_VISIBLE_CHANGES = 12

/** One plain change line: green `+`, amber `~`, or red `×` followed by the path. */
@Composable
private fun PlainChangeRow(change: com.zip2git.data.model.FileChange) {
    val (symbol, color) = when (change.status) {
        ChangeStatus.ADDED -> "+" to ChangeColors.Added
        ChangeStatus.MODIFIED -> "~" to ChangeColors.Modified
        ChangeStatus.DELETED -> "\u00D7" to ChangeColors.Deleted
        ChangeStatus.UNCHANGED -> "=" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = change.path,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun AboutCard(onClose: () -> Unit) {
    SectionCard(
        title = "About Zip2Git",
        subtitle = "Sync project files via Zip2Git",
        trailing = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
            }
        },
    ) {
        Text(
            text = "Zip2Git is an on-device Android tool to upload ZIP archives and project " +
                "directories directly to GitHub repositories.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Your privacy and code security are our top priorities:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        listOf(
            "1. Direct Connection: All network requests communicate directly between your device and api.github.com.",
            "2. No Cloud Relay: No code, tokens, or analytics are transmitted to any third-party or intermediate server.",
            "3. Secure Credential Storage: Personal Access Tokens are encrypted and stored solely within private device storage. Tokens are never uploaded anywhere except to authenticate directly with GitHub.",
            "4. Local File Processing: Archive extraction, blob hashing, and tree diffing happen entirely in-memory on your phone.",
        ).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "How Zip2Git Works",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        listOf(
            "Full Git-compatible SHA-1 hashing on-device",
            "Atomic commit creation via GitHub REST API",
            "Zero intermediate servers: direct phone-to-GitHub",
            "Token securely stored only on-device",
        ).forEach { line ->
            Text(
                text = "•  $line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun ErrorCard(error: SyncErrorInfo, onDismiss: () -> Unit) {
    MessageBanner(
        title = error.title,
        message = error.message,
        isError = true,
        onDismiss = onDismiss,
    )
}