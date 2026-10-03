package com.example.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.BrowserTab
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsTray(
    viewModel: BrowserViewModel,
    onCloseTray: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allTabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val config by viewModel.config.collectAsState()

    var isIncognitoFilter by remember { mutableStateOf(false) }
    var tabSearchQuery by remember { mutableStateOf("") }
    var isGridView by remember { mutableStateOf(config.tabLayoutMode == "GRID") }

    val filteredTabs = allTabs.filter {
        it.incognito == isIncognitoFilter &&
        (tabSearchQuery.isBlank() || it.title.contains(tabSearchQuery, ignoreCase = true) || it.url.contains(tabSearchQuery, ignoreCase = true))
    }

    BackHandler {
        onCloseTray()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isIncognitoFilter) "Incognito Tabs" else "Open Tabs",
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(containerColor = if (isIncognitoFilter) MaterialTheme.colorScheme.tertiary else CyberCyan) {
                            Text(
                                text = "${filteredTabs.size}",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    // Layout Toggle (Grid / List)
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = "Toggle Grid/List"
                        )
                    }
                    // Restore Closed Tab
                    IconButton(onClick = { viewModel.restoreLastClosedTab() }) {
                        Icon(imageVector = Icons.Default.Restore, contentDescription = "Restore Tab")
                    }
                    // Close Tray
                    IconButton(onClick = onCloseTray) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Normal / Incognito Switcher
                    TabRow(
                        selectedTabIndex = if (isIncognitoFilter) 1 else 0,
                        modifier = Modifier.width(220.dp),
                        containerColor = Color.Transparent
                    ) {
                        Tab(
                            selected = !isIncognitoFilter,
                            onClick = { isIncognitoFilter = false },
                            text = { Text("Standard") },
                            icon = { Icon(Icons.Default.Tab, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = isIncognitoFilter,
                            onClick = { isIncognitoFilter = true },
                            text = { Text("Incognito") },
                            icon = { Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    // New Tab Button
                    FloatingActionButton(
                        onClick = {
                            viewModel.createNewTab("about:home", incognito = isIncognitoFilter)
                            onCloseTray()
                        },
                        containerColor = CyberCyan,
                        contentColor = Color.Black,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("new_tab_fab")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New Tab")
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Tab Search Field
            OutlinedTextField(
                value = tabSearchQuery,
                onValueChange = { tabSearchQuery = it },
                placeholder = { Text("Search tabs...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (tabSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { tabSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            )

            if (filteredTabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isIncognitoFilter) Icons.Default.VpnKey else Icons.Default.Tab,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isIncognitoFilter) "No Incognito tabs open" else "No open tabs",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTabs, key = { it.id }) { tab ->
                        TabCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            onSelect = {
                                viewModel.switchTab(tab.id)
                                onCloseTray()
                            },
                            onClose = { viewModel.closeTab(tab.id) },
                            onDuplicate = { viewModel.duplicateTab(tab.id) },
                            onCloseOthers = { viewModel.closeOtherTabs(tab.id, tab.incognito) }
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTabs, key = { it.id }) { tab ->
                        TabListItem(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            onSelect = {
                                viewModel.switchTab(tab.id)
                                onCloseTray()
                            },
                            onClose = { viewModel.closeTab(tab.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabCard(
    tab: BrowserTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onDuplicate: () -> Unit,
    onCloseOthers: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) CyberCyan else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Card Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (tab.incognito) Icons.Default.VpnKey else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (tab.incognito) MaterialTheme.colorScheme.tertiary else CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.title.ifBlank { tab.url },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Tab",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Card Body / Simulated preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = tab.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (tab.keepAliveMode != "None") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = SecurityGreen,
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Keep Alive (${tab.keepAliveMode})",
                                style = MaterialTheme.typography.labelSmall,
                                color = SecurityGreen
                            )
                        }
                    }
                }

                // Tab actions menu trigger
                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Tab options", modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Duplicate Tab") },
                            onClick = {
                                showMenu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Close Other Tabs") },
                            onClick = {
                                showMenu = false
                                onCloseOthers()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabListItem(
    tab: BrowserTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 1.5.dp else 0.5.dp,
                color = if (isActive) CyberCyan else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (tab.incognito) Icons.Default.VpnKey else Icons.Default.Language,
                contentDescription = null,
                tint = if (tab.incognito) MaterialTheme.colorScheme.tertiary else CyberCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tab.title.ifBlank { tab.url },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tab.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Tab", modifier = Modifier.size(18.dp))
            }
        }
    }
}
