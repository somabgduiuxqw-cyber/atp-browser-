package com.example.ui.browser

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BrowserViewModel
import com.example.ui.ScreenState
import com.example.ui.dialogs.PageInfoDialog
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    onFileChooser: (ValueCallback<Array<Uri>>?, WebChromeClient.FileChooserParams?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val currentUrl by viewModel.currentUrl.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val canGoBack by viewModel.canGoBack.collectAsState()
    val canGoForward by viewModel.canGoForward.collectAsState()
    val isSecureHttps by viewModel.isSecureHttps.collectAsState()
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val config by viewModel.config.collectAsState()

    val activeTab = tabs.find { it.id == activeTabId }

    var isEditingUrl by remember { mutableStateOf(false) }
    var urlInputText by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showPageInfo by remember { mutableStateOf(false) }

    // Find in Page
    val isFindActive by viewModel.findInPageActive.collectAsState()
    val findQuery by viewModel.findQuery.collectAsState()
    val findMatches by viewModel.findMatches.collectAsState()

    BackHandler(enabled = true) {
        if (isFindActive) {
            viewModel.closeFindInPage()
        } else if (isEditingUrl) {
            isEditingUrl = false
            keyboardController?.hide()
        } else {
            val activeWebView = TabWebViewManager.getWebView(activeTabId ?: -1)
            if (activeWebView != null && activeWebView.canGoBack()) {
                activeWebView.goBack()
            } else if (currentUrl != "about:home" && currentUrl.isNotBlank()) {
                viewModel.loadUrl("about:home")
            } else if (tabs.size > 1) {
                viewModel.navigateToScreen(ScreenState.TABS_TRAY)
            } else {
                (context as? Activity)?.moveTaskToBack(true)
            }
        }
    }

    Scaffold(
        topBar = {
            if (config.addressBarPosition == "TOP") {
                BrowserTopBar(
                    url = currentUrl,
                    isEditing = isEditingUrl,
                    urlInput = urlInputText,
                    onUrlInputChange = {
                        urlInputText = it
                        viewModel.updateSuggestions(it)
                    },
                    onStartEdit = {
                        isEditingUrl = true
                        urlInputText = if (currentUrl == "about:home") "" else currentUrl
                    },
                    onSubmit = {
                        isEditingUrl = false
                        keyboardController?.hide()
                        viewModel.onUrlSubmitted(urlInputText)
                    },
                    onCancelEdit = {
                        isEditingUrl = false
                        keyboardController?.hide()
                    },
                    loadingProgress = loadingProgress,
                    isSecure = isSecureHttps,
                    tabCount = tabs.size,
                    isIncognito = activeTab?.incognito == true,
                    onTabsClick = {
                        isEditingUrl = false
                        keyboardController?.hide()
                        viewModel.navigateToScreen(ScreenState.TABS_TRAY)
                    },
                    onSecurityClick = { showPageInfo = true },
                    onMenuClick = { showMenu = true }
                )
            }
        },
        bottomBar = {
            Column {
                // Find in page bar if active
                if (isFindActive) {
                    FindInPageBar(
                        query = findQuery,
                        onQueryChange = { viewModel.setFindQuery(it) },
                        currentMatch = findMatches.first,
                        totalMatches = findMatches.second,
                        onClose = { viewModel.closeFindInPage() }
                    )
                }

                // Main Bottom Toolbar
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isBackEnabled = canGoBack || (currentUrl != "about:home" && currentUrl.isNotBlank())
                        IconButton(
                            onClick = { viewModel.triggerGoBack() },
                            enabled = isBackEnabled,
                            modifier = Modifier.testTag("nav_back")
                        ) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = if (isBackEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.loadUrl("about:home") },
                            modifier = Modifier.testTag("nav_home")
                        ) {
                            Icon(Icons.Default.Home, contentDescription = "Home")
                        }

                        // Tab count button
                        IconButton(
                            onClick = { viewModel.navigateToScreen(ScreenState.TABS_TRAY) },
                            modifier = Modifier.testTag("nav_tabs")
                        ) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = if (activeTab?.incognito == true) MaterialTheme.colorScheme.tertiary else CyberCyan
                                    ) {
                                        Text(
                                            text = "${tabs.size}",
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Tab, contentDescription = "Tabs")
                            }
                        }

                        IconButton(
                            onClick = { viewModel.triggerGoForward() },
                            enabled = canGoForward,
                            modifier = Modifier.testTag("nav_forward")
                        ) {
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (loadingProgress in 1..99) {
                                    viewModel.triggerStopLoading()
                                } else {
                                    viewModel.triggerReload()
                                }
                            },
                            modifier = Modifier.testTag("nav_reload")
                        ) {
                            Icon(
                                imageVector = if (loadingProgress in 1..99) Icons.Default.Close else Icons.Default.Refresh,
                                contentDescription = if (loadingProgress in 1..99) "Stop" else "Reload"
                            )
                        }

                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.testTag("nav_menu")
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Persistent WebContainer for active tab
            if (activeTab != null) {
                WebContainer(
                    tab = activeTab,
                    viewModel = viewModel,
                    onFileChooser = onFileChooser
                )
            }

            // Display HomePage over WebView when on about:home or when tab is empty
            if (currentUrl == "about:home" || currentUrl.isBlank() || activeTab == null) {
                HomePage(
                    viewModel = viewModel,
                    onNavigate = { url ->
                        isEditingUrl = false
                        keyboardController?.hide()
                        viewModel.onUrlSubmitted(url)
                    }
                )
            }

            // URL Search Suggestions dropdown overlay when typing
            if (isEditingUrl) {
                val suggestions by viewModel.suggestions.collectAsState()
                if (suggestions.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 8.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Suggestions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            for (sug in suggestions) {
                                ListItem(
                                    headlineContent = { Text(sug, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    leadingContent = { Icon(Icons.Default.History, contentDescription = null, tint = CyberCyan) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            isEditingUrl = false
                                            keyboardController?.hide()
                                            viewModel.onUrlSubmitted(sug)
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Main Browser Overflow Menu
    if (showMenu) {
        BrowserOverflowMenu(
            isExpanded = showMenu,
            onDismiss = { showMenu = false },
            isDesktopSite = activeTab?.desktopMode ?: false,
            onToggleDesktop = {
                showMenu = false
                viewModel.toggleDesktopMode()
            },
            onKeepAlive = {
                showMenu = false
                viewModel.toggleKeepAliveForCurrentSite()
                Toast.makeText(context, "Website added to Keep Alive Background Sites", Toast.LENGTH_SHORT).show()
            },
            onBookmark = {
                showMenu = false
                viewModel.toggleBookmarkCurrentPage()
                Toast.makeText(context, "Bookmark updated", Toast.LENGTH_SHORT).show()
            },
            onFindInPage = {
                showMenu = false
                viewModel.openFindInPage()
            },
            onShare = {
                showMenu = false
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, currentUrl)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share URL"))
            },
            onCopyUrl = {
                showMenu = false
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onOpenDownloads = {
                showMenu = false
                viewModel.navigateToScreen(ScreenState.DOWNLOADS)
            },
            onOpenHistory = {
                showMenu = false
                viewModel.navigateToScreen(ScreenState.HISTORY_BOOKMARKS)
            },
            onOpenDns = {
                showMenu = false
                viewModel.navigateToScreen(ScreenState.DNS_SETTINGS)
            },
            onOpenSecurity = {
                showMenu = false
                viewModel.navigateToScreen(ScreenState.SECURITY_CENTER)
            },
            onOpenBackgroundSites = {
                showMenu = false
                viewModel.navigateToScreen(ScreenState.BACKGROUND_SITES)
            },
            onOpenSettings = {
                showMenu = false
                viewModel.navigateToScreen(ScreenState.SETTINGS)
            },
            onOpenDeveloperMode = {
                showMenu = false
                viewModel.navigateToScreen(ScreenState.DEVELOPER_MODE)
            }
        )
    }

    // Page Info Dialog
    if (showPageInfo) {
        PageInfoDialog(
            url = currentUrl,
            isHttps = isSecureHttps,
            onDismiss = { showPageInfo = false }
        )
    }
}

@Composable
fun BrowserTopBar(
    url: String,
    isEditing: Boolean,
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
    loadingProgress: Int,
    isSecure: Boolean,
    tabCount: Int,
    isIncognito: Boolean,
    onTabsClick: () -> Unit,
    onSecurityClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Security Lock Icon
            IconButton(
                onClick = onSecurityClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isSecure) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = "Security State",
                    tint = if (isSecure) SecurityGreen else SecurityAmber,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Address Bar
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable(enabled = !isEditing, onClick = onStartEdit)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    if (isIncognito) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = "Incognito",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 6.dp)
                        )
                    }

                    if (isEditing) {
                        BasicTextField(
                            value = urlInput,
                            onValueChange = onUrlInputChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(CyberCyan),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go,
                                autoCorrect = false
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { onSubmit() }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            decorationBox = { innerTextField ->
                                Box(
                                    modifier = Modifier.fillMaxWidth(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (urlInput.isEmpty()) {
                                        Text(
                                            text = "Search or type URL...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )

                        if (urlInput.isNotEmpty()) {
                            IconButton(
                                onClick = { onUrlInputChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }

                        IconButton(
                            onClick = onSubmit,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Go", tint = CyberCyan, modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = onCancelEdit,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Text(
                            text = if (url == "about:home" || url.isBlank()) "Search or enter URL" else url,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            color = if (url == "about:home" || url.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Tabs Tray Button
            IconButton(onClick = onTabsClick, modifier = Modifier.size(36.dp)) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = if (isIncognito) MaterialTheme.colorScheme.tertiary else CyberCyan) {
                            Text(
                                text = "$tabCount",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                ) {
                    Icon(Icons.Default.Tab, contentDescription = "Tabs", modifier = Modifier.size(20.dp))
                }
            }

            // Quick Menu Button
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", modifier = Modifier.size(20.dp))
            }
        }

        // Loading Progress Indicator
        if (loadingProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadingProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = CyberCyan,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun FindInPageBar(
    query: String,
    onQueryChange: (String) -> Unit,
    currentMatch: Int,
    totalMatches: Int,
    onClose: () -> Unit
) {
    Surface(
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find in page...") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (totalMatches > 0) "$currentMatch of $totalMatches" else "0 matches",
                style = MaterialTheme.typography.labelSmall
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close Find")
            }
        }
    }
}

@Composable
fun BrowserOverflowMenu(
    isExpanded: Boolean,
    onDismiss: () -> Unit,
    isDesktopSite: Boolean,
    onToggleDesktop: () -> Unit,
    onKeepAlive: () -> Unit,
    onBookmark: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenBackgroundSites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeveloperMode: () -> Unit
) {
    DropdownMenu(
        expanded = isExpanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (isDesktopSite) "Desktop site (Enabled)" else "Desktop site") },
            leadingIcon = { Icon(Icons.Default.DesktopWindows, contentDescription = null) },
            trailingIcon = {
                Checkbox(checked = isDesktopSite, onCheckedChange = { onToggleDesktop() })
            },
            onClick = onToggleDesktop
        )
        DropdownMenuItem(
            text = { Text("Keep Alive / Background") },
            leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) },
            onClick = onKeepAlive
        )
        DropdownMenuItem(
            text = { Text("Bookmark Page") },
            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
            onClick = onBookmark
        )
        DropdownMenuItem(
            text = { Text("Find in Page") },
            leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
            onClick = onFindInPage
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Share") },
            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
            onClick = onShare
        )
        DropdownMenuItem(
            text = { Text("Copy URL") },
            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
            onClick = onCopyUrl
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Downloads") },
            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
            onClick = onOpenDownloads
        )
        DropdownMenuItem(
            text = { Text("History & Bookmarks") },
            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
            onClick = onOpenHistory
        )
        DropdownMenuItem(
            text = { Text("DNS Settings") },
            leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null) },
            onClick = onOpenDns
        )
        DropdownMenuItem(
            text = { Text("Security Center") },
            leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
            onClick = onOpenSecurity
        )
        DropdownMenuItem(
            text = { Text("Background Sites") },
            leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null) },
            onClick = onOpenBackgroundSites
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = onOpenSettings
        )
        DropdownMenuItem(
            text = { Text("Developer Mode") },
            leadingIcon = { Icon(Icons.Default.DeveloperMode, contentDescription = null) },
            onClick = onOpenDeveloperMode
        )
    }
}
