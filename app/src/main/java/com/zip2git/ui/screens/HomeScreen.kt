package com.zip2git.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zip2git.data.model.RecentPushItem
import com.zip2git.ui.MainUiState
import com.zip2git.ui.components.SectionCard
import com.zip2git.ui.theme.HeroIndigo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home page that sits behind the back arrow of the `New Git Push` form.
 *
 * The original app keeps the push form (repository, token, branch, files,
 * options, review, push) on its own page; completed work belongs here instead of
 * at the bottom of that form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    onStartNewPush: () -> Unit,
    onOpenRecent: (RecentPushItem) -> Unit,
    onDisconnect: () -> Unit,
    onToggleTheme: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAbout by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Zip2Git",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                            contentDescription = if (state.darkTheme) {
                                "Switch to Light Mode"
                            } else {
                                "Switch to Dark Mode"
                            },
                        )
                    }
                    IconButton(onClick = { showAbout = !showAbout }) {
                        Icon(Icons.Filled.Info, contentDescription = "About Zip2Git")
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Disconnect and remove the saved token",
                        )
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.banner?.let { banner ->
                item {
                    MessageBannerRow(text = banner, onDismiss = onDismissBanner)
                }
            }

            item { HeroCard(onStartNewPush = onStartNewPush) }

            if (showAbout) {
                item { AboutCard(onClose = { showAbout = false }) }
            }

            item {
                Text(
                    text = "Recently completed work:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            if (state.recentPushes.isEmpty()) {
                item {
                    SectionCard(subtitle = "No pushes yet") {
                        Text(
                            text = "Successful pushes appear here so you can repeat them " +
                                "without re-entering the repository.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(
                    state.recentPushes,
                    key = { it.repoOwner + it.repoName + it.branch + it.timestamp },
                ) {
                    RecentPushRow(item = it, onClick = { onOpenRecent(it) })
                }
            }
        }
    }
}

@Composable
private fun HeroCard(onStartNewPush: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
    ) {
        Column(Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary, HeroIndigo),
                        ),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Push a project to GitHub without a laptop",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Upload ZIP archives, project folders, or selected files straight " +
                    "to your GitHub repository in seconds with automatic Git diff comparison.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onStartNewPush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Start New Push", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** The green `@username` pill shared by the home and push app bars. */
@Composable
internal fun ConnectedPill(login: String) {
    Surface(shape = RoundedCornerShape(50), color = ConnectedGreen) {
        Text(
            text = "@$login",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
internal fun RecentPushRow(item: RecentPushItem, onClick: () -> Unit) {
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

@Composable
private fun MessageBannerRow(text: String, onDismiss: () -> Unit) {
    com.zip2git.ui.components.MessageBanner(
        title = "Notice",
        message = text,
        isError = false,
        onDismiss = onDismiss,
    )
}