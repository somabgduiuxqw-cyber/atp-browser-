package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.Bookmark
import com.example.data.db.HistoryEntry
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityBlue
import com.example.ui.theme.SecurityRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBookmarksScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: History, 1: Bookmarks
    var searchQuery by remember { mutableStateOf("") }

    val history by viewModel.db.getAllHistory().collectAsState(initial = emptyList())
    val bookmarks by viewModel.db.getAllBookmarks().collectAsState(initial = emptyList())

    val filteredHistory = history.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
    }

    val filteredBookmarks = bookmarks.filter {
        searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
    }

    var showClearHistoryDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedTab == 0) "History" else "Bookmarks", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showClearHistoryDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear History")
                        }
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Switcher
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("History (${history.size})") },
                    icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Bookmarks (${bookmarks.size})") },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search ${if (selectedTab == 0) "history" else "bookmarks"}...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            )

            if (selectedTab == 0) {
                // History List
                if (filteredHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No browsing history found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredHistory, key = { it.id }) { item ->
                            val timeStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.visitedAt))
                            ListItem(
                                headlineContent = { Text(item.title.ifBlank { item.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${item.url} • $timeStr", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Default.Public, contentDescription = null, tint = CyberCyan) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        scope.launch { viewModel.db.deleteHistoryById(item.id) }
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onNavigate(item.url)
                                        onBack()
                                    }
                            )
                        }
                    }
                }
            } else {
                // Bookmarks List
                if (filteredBookmarks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No bookmarks saved yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredBookmarks, key = { it.id }) { bm ->
                            ListItem(
                                headlineContent = { Text(bm.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(bm.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = SecurityBlue) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        scope.launch { viewModel.db.deleteBookmarkById(bm.id) }
                                    }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Bookmark", modifier = Modifier.size(18.dp))
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onNavigate(bm.url)
                                        onBack()
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    // Range deletion dialog for History (Requirement 31: Last hour, Today, Everything)
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Browsing History") },
            text = { Text("Choose the time range of history you wish to delete:") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val oneHourAgo = System.currentTimeMillis() - 3600_000L
                                viewModel.db.deleteHistorySince(oneHourAgo)
                                showClearHistoryDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Last Hour")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val todayStart = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                }.timeInMillis
                                viewModel.db.deleteHistorySince(todayStart)
                                showClearHistoryDialog = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Today")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.db.clearAllHistory()
                                showClearHistoryDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SecurityRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Everything")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
