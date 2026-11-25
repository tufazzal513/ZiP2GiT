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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The home page that sits behind the back arrow of the `New Git Push` form.
 *
 * The original app keeps the push form (repository, token, branch, files,
 * options, review, push) on its own page; this page is only the entry point:
 * a hero card, the "How Zip2Git Works" walkthrough, and the completed-push list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    onStartNewPush: () -> Unit,
    onOpenRecent: (RecentPushItem) -> Unit,
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
                    Text(
                        text = "Zip2Git",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                    com.zip2git.ui.components.MessageBanner(
                        title = "Notice",
                        message = banner,
                        isError = false,
                        onDismiss = onDismissBanner,
                    )
                }
            }

            if (!state.tokenIsSet && state.banner == null) {
                item {
                    com.zip2git.ui.components.MessageBanner(
                        title = "Not connected yet",
                        message = "Tap Start New Push to enter your GitHub Repository URL " +
                            "& Personal Access Token (PAT).",
                        isError = false,
                    )
                }
            }

            item { HeroCard(onStartNewPush = onStartNewPush) }

            item { HowItWorksCard() }

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

/** Gradient hero with the headline, blurb and the primary "Start New Push" action. */
@Composable
private fun HeroCard(onStartNewPush: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1E3FA8), Color(0xFF2F6BFF))),
                    RoundedCornerShape(18.dp),
                )
                .padding(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
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
                text = "Direct GitHub Push",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Upload code without PC or Git CLI",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "Upload ZIP archives, project folders, or selected files straight " +
                    "to your GitHub repository in seconds with automatic Git diff comparison.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f),
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onStartNewPush,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4E86FF),
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Start New Push", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The three-step "How Zip2Git Works" walkthrough under the hero. */
@Composable
private fun HowItWorksCard() {
    SectionCard(title = "\uD83D\uDCA1 How Zip2Git Works") {
        HowItWorksStep(
            number = "1",
            text = "Enter your GitHub Repository URL & Personal Access Token (PAT).",
        )
        Spacer(Modifier.height(12.dp))
        HowItWorksStep(
            number = "2",
            text = "Pick any ZIP archive, whole project folder, or code files from your device.",
        )
        Spacer(Modifier.height(12.dp))
        HowItWorksStep(
            number = "3",
            text = "Inspect local vs remote changes and push commits in one tap.",
        )
    }
}

@Composable
private fun HowItWorksStep(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
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