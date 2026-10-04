package com.example.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Bookmark
import com.example.data.db.HistoryEntry
import com.example.ui.BrowserViewModel
import com.example.ui.ScreenState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityBlue

data class QuickLinkItem(val title: String, val url: String, val icon: ImageVector)

@Composable
fun HomePage(
    viewModel: BrowserViewModel,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf("") }
    val history by viewModel.db.getAllHistory().collectAsState(initial = emptyList())
    val bookmarks by viewModel.db.getAllBookmarks().collectAsState(initial = emptyList())
    val backgroundSites by viewModel.db.getAllBackgroundSites().collectAsState(initial = emptyList())
    val activeDownloads by viewModel.db.getAllDownloads().collectAsState(initial = emptyList())

    val defaultQuickLinks = listOf(
        QuickLinkItem("DuckDuckGo", "https://duckduckgo.com", Icons.Default.Search),
        QuickLinkItem("Wikipedia", "https://wikipedia.org", Icons.Default.MenuBook),
        QuickLinkItem("GitHub", "https://github.com", Icons.Default.Code),
        QuickLinkItem("Hacker News", "https://news.ycombinator.com", Icons.Default.Public),
        QuickLinkItem("Reddit", "https://reddit.com", Icons.Default.Forum),
        QuickLinkItem("OpenStreetMap", "https://openstreetmap.org", Icons.Default.Map)
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(36.dp))

            // Brand Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = CyberCyan.copy(alpha = 0.15f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "ATP Shield",
                            tint = CyberCyan,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "ATP BROWSER",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real Web. Keep Alive. Total Privacy.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Search Bar
            OutlinedTextField(
                value = searchInput,
                onValueChange = { searchInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input"),
                placeholder = { Text("Search the web or type a URL...") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = CyberCyan)
                },
                trailingIcon = {
                    if (searchInput.isNotEmpty()) {
                        IconButton(onClick = {
                            onNavigate(searchInput)
                        }) {
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = "Go", tint = CyberCyan)
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        if (searchInput.isNotBlank()) {
                            onNavigate(searchInput)
                        }
                    }
                ),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ActionChip(
                    icon = Icons.Default.VpnKey,
                    label = "Incognito",
                    onClick = { viewModel.createNewTab("about:home", incognito = true) }
                )
                ActionChip(
                    icon = Icons.Default.Sync,
                    label = "Keep Alive",
                    badge = if (backgroundSites.isNotEmpty()) "${backgroundSites.size}" else null,
                    onClick = { viewModel.navigateToScreen(ScreenState.BACKGROUND_SITES) }
                )
                ActionChip(
                    icon = Icons.Default.Dns,
                    label = "DNS",
                    onClick = { viewModel.navigateToScreen(ScreenState.DNS_SETTINGS) }
                )
                ActionChip(
                    icon = Icons.Default.Security,
                    label = "Security",
                    onClick = { viewModel.navigateToScreen(ScreenState.SECURITY_CENTER) }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Quick Links Section
            SectionHeader(title = "QUICK LINKS", icon = Icons.Default.Star)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Quick Links Grid
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                val chunks = defaultQuickLinks.chunked(3)
                for (row in chunks) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (item in row) {
                            Card(
                                onClick = { onNavigate(item.url) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.title,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Background Sites Section (If Any Active)
        if (backgroundSites.isNotEmpty()) {
            item {
                SectionHeader(title = "KEEP ALIVE BACKGROUND SITES", icon = Icons.Default.Sync)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(backgroundSites) { site ->
                        Card(
                            onClick = { onNavigate(site.url) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (site.status == "Running") SecurityGreen else Color.Gray,
                                    modifier = Modifier.size(8.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = site.title.ifBlank { site.url },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${site.mode} • ${site.status}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Bookmarks Section
        if (bookmarks.isNotEmpty()) {
            item {
                SectionHeader(title = "BOOKMARKS", icon = Icons.Default.Bookmark)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(bookmarks.take(4)) { bm ->
                ListItem(
                    headlineContent = { Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(bm.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = SecurityBlue)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(bm.url) }
                )
            }
            item { Spacer(modifier = Modifier.height(20.dp)) }
        }

        // Recently Visited History
        if (history.isNotEmpty()) {
            item {
                SectionHeader(title = "RECENTLY VISITED", icon = Icons.Default.History)
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(history.take(5)) { entry ->
                ListItem(
                    headlineContent = { Text(entry.title.ifBlank { entry.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(entry.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(entry.url) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ActionChip(
    icon: ImageVector,
    label: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = CyberCyan,
                    modifier = Modifier.size(22.dp)
                )
                if (badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = SecurityGreen,
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
