package com.zip2git.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zip2git.data.model.ChangeStatus
import com.zip2git.data.model.FileComparison
import com.zip2git.data.model.RecentPushItem
import com.zip2git.data.model.SourceType
import com.zip2git.data.model.SyncErrorInfo
import com.zip2git.ui.MainUiState
import com.zip2git.ui.components.ChangeColors
import com.zip2git.ui.components.CountPill
import com.zip2git.ui.components.MessageBanner
import com.zip2git.ui.components.SectionCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main working screen: pick a source, choose the branch, inspect the diff against
 * the remote branch and start the push.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: MainUiState,
    onPickZip: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
    onBranchChange: (String) -> Unit,
    onRefreshBranches: () -> Unit,
    onCommitMessageChange: (String) -> Unit,
    onMirrorSyncChange: (Boolean) -> Unit,
    onRefreshComparison: () -> Unit,
    onStartSync: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleTheme: () -> Unit,
    onDismissBanner: () -> Unit,
    onDismissError: () -> Unit,
    onOpenRecent: (RecentPushItem) -> Unit,
    onToggleAdvanced: () -> Unit,
    onKeepImportantChange: (Boolean) -> Unit,
    onStripRootChange: (Boolean) -> Unit,
    onAddFilterRule: (String) -> Unit,
    onRemoveFilterRule: (String) -> Unit,
    onToggleFilterRule: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Zip2Git",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = state.repo?.let { "Connected as @${state.connectedLogin}" }
                                ?: "Connecting…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Filled.Logout, contentDescription = "Disconnect")
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
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 28.dp,
            ),
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

            item {
                RepositoryCard(
                    state = state,
                    onBranchChange = onBranchChange,
                    onRefreshBranches = onRefreshBranches,
                )
            }

            item {
                SourceCard(
                    state = state,
                    onPickZip = onPickZip,
                    onPickFolder = onPickFolder,
                    onPickFiles = onPickFiles,
                )
            }

            item {
                ComparisonCard(
                    comparison = state.comparison,
                    mirrorSync = state.mirrorSync,
                    onMirrorSyncChange = onMirrorSyncChange,
                    onRefresh = onRefreshComparison,
                )
            }

            item {
                AdvancedOptionsCard(
                    state = state,
                    onToggle = onToggleAdvanced,
                    onKeepImportantChange = onKeepImportantChange,
                    onStripRootChange = onStripRootChange,
                    onAddFilterRule = onAddFilterRule,
                    onRemoveFilterRule = onRemoveFilterRule,
                    onToggleFilterRule = onToggleFilterRule,
                )
            }

            item {
                CommitCard(
                    state = state,
                    onCommitMessageChange = onCommitMessageChange,
                    onStartSync = onStartSync,
                )
            }

            if (state.recentPushes.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently completed work:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                items(state.recentPushes, key = { it.repoOwner + it.repoName + it.branch + it.timestamp }) {
                    RecentPushRow(item = it, onClick = { onOpenRecent(it) })
                }
            }

            item { AboutCard() }
        }
    }
}

@Composable
private fun AboutCard() {
    SectionCard(title = "About Zip2Git", subtitle = "Sync project files via Zip2Git") {
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
    }
}

@Composable
private fun RepositoryCard(
    state: MainUiState,
    onBranchChange: (String) -> Unit,
    onRefreshBranches: () -> Unit,
) {
    val repo = state.repo
    SectionCard(
        title = repo?.let { "${it.owner}/${it.name}" } ?: "Loading repository…",
        subtitle = repo?.let { if (it.isPrivate) "Private repository" else "Public repository" },
        trailing = {
            IconButton(onClick = onRefreshBranches, enabled = !state.isFetchingBranches) {
                if (state.isFetchingBranches) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Fetch branches")
                }
            }
        },
    ) {
        if (state.isInitializingRepo) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        BranchSelector(
            branches = state.repoBranches.map { it.name },
            selected = state.selectedBranch,
            enabled = !state.isSyncing,
            onSelect = onBranchChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchSelector(
    branches: List<String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (branches.isEmpty()) "Available branches: none" else "Available branches: ${branches.size}"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled && branches.isNotEmpty()) expanded = it },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(14.dp),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            branches.forEach { branch ->
                DropdownMenuItem(
                    text = { Text(branch) },
                    onClick = {
                        onSelect(branch)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SourceCard(
    state: MainUiState,
    onPickZip: () -> Unit,
    onPickFolder: () -> Unit,
    onPickFiles: () -> Unit,
) {
    SectionCard(
        title = "Project source",
        subtitle = state.sourceType?.let {
            when (it) {
                SourceType.ZIP -> "From a ZIP archive"
                SourceType.FOLDER -> "From a folder"
                SourceType.FILES -> "From selected files"
            }
        },
    ) {
        if (state.isProcessingZip) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("Reading project files…", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
        }

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
                            text = "${state.totalZipFiles} files ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

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

@Composable
private fun PickButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(62.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    comparison: FileComparison?,
    mirrorSync: Boolean,
    onMirrorSyncChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    SectionCard(
        title = "Inspect Changes & Validate",
        subtitle = if (comparison == null) {
            "Choose a source to compare with the branch"
        } else {
            "${comparison.added.size + comparison.modified.size + comparison.deleted.size} changes, " +
                "${comparison.unchanged.size} unchanged"
        },
        trailing = {
            IconButton(onClick = onRefresh, enabled = comparison != null) {
                Icon(Icons.Filled.Refresh, contentDescription = "Recompare")
            }
        },
    ) {
        if (comparison == null) {
            Text(
                text = "No source selected yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CountPill("added", comparison.added.size, ChangeColors.Added)
                CountPill("modified", comparison.modified.size, ChangeColors.Modified)
                CountPill("deleted", comparison.deleted.size, ChangeColors.Deleted)
            }
            if (mirrorSync && comparison.deleted.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Mirror sync will delete ${comparison.deleted.size} remote file(s).",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChangeColors.Deleted,
                )
            }
            if (comparison.added.isNotEmpty() || comparison.modified.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                ChangePreview(
                    paths = (comparison.added.map { it.path to ChangeStatus.ADDED } +
                        comparison.modified.map { it.path to ChangeStatus.MODIFIED })
                        .sortedBy { it.first },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Mirror Sync (exact match)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Delete remote files that are missing locally",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = mirrorSync, onCheckedChange = onMirrorSyncChange)
        }
    }
}

@Composable
private fun AdvancedOptionsCard(
    state: MainUiState,
    onToggle: () -> Unit,
    onKeepImportantChange: (Boolean) -> Unit,
    onStripRootChange: (Boolean) -> Unit,
    onAddFilterRule: (String) -> Unit,
    onRemoveFilterRule: (String) -> Unit,
    onToggleFilterRule: (String) -> Unit,
) {
    SectionCard(
        title = "Advanced Options",
        trailing = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (state.showAdvanced) {
                        Icons.Filled.ExpandLess
                    } else {
                        Icons.Filled.ExpandMore
                    },
                    contentDescription = if (state.showAdvanced) "Collapse" else "Expand",
                )
            }
        },
    ) {
        if (!state.showAdvanced) {
            Text(
                "Smart File Filter, Keep important files, Strip root folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Keep important files",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Preserve README, LICENSE, .github/ and .gitignore in Mirror Sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.keepImportantFiles,
                    onCheckedChange = onKeepImportantChange,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Strip root folder", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Remove the single top-level folder when importing a ZIP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.stripRootFolder,
                    onCheckedChange = onStripRootChange,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Smart File Filter rules",
                style = MaterialTheme.typography.titleSmall,
            )
            if (state.filterRules.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "No rules yet. Add one below, e.g. build/ or *.log",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(8.dp))
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
                            modifier = Modifier.scale(0.85f),
                        )
                        Text(
                            text = rule.pattern,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rule.enabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = { onRemoveFilterRule(rule.pattern) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove ${rule.pattern}",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            AddRuleRow(onAdd = onAddFilterRule)
        }
    }
}

@Composable
private fun AddRuleRow(onAdd: (String) -> Unit) {
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
private fun ChangePreview(paths: List<Pair<String, ChangeStatus>>) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        paths.take(6).forEach { (path, status) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ChangeColors.label(status),
                    style = MaterialTheme.typography.labelSmall,
                    color = ChangeColors.of(status),
                )
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (paths.size > 6) {
            Text(
                text = "+${paths.size - 6} more",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommitCard(
    state: MainUiState,
    onCommitMessageChange: (String) -> Unit,
    onStartSync: () -> Unit,
) {
    val canSync = !state.isSyncing &&
        state.sourceName.isNotBlank() &&
        state.selectedBranch.isNotBlank()

    SectionCard(title = "Commit & push") {
        OutlinedTextField(
            value = state.commitMessage,
            onValueChange = onCommitMessageChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Commit message") },
            singleLine = true,
            enabled = !state.isSyncing,
            shape = RoundedCornerShape(14.dp),
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onStartSync,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = canSync,
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.isSyncing) "Pushing…" else "Push to GitHub",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (!canSync && !state.isSyncing) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Pick a project source and a branch first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun RecentPushRow(item: RecentPushItem, onClick: () -> Unit) {
    val formatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${item.repoOwner}/${item.repoName}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.branch} • ${item.fileName} • ${formatter.format(Date(item.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
