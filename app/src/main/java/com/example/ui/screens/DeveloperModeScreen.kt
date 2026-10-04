package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.BrowserViewModel
import com.example.ui.ConsoleLogEntry
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperModeScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Console, 1: WebView Info, 2: Page Source, 3: User-Agent
    val logs by viewModel.consoleLogs.collectAsState()
    val pageSource by viewModel.pageSource.collectAsState()
    val config by viewModel.config.collectAsState()

    var customUaInput by remember { mutableStateOf(config.customUserAgent) }
    var sourceSearchQuery by remember { mutableStateOf("") }

    val defaultUa = remember {
        try {
            WebView(context).settings.userAgentString
        } catch (e: Exception) {
            "System Android WebView"
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer Tools", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { viewModel.clearConsoleLogs() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Console")
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
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                edgePadding = 16.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Diagnostics") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Console (${logs.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("WebView Info") }
                )
                Tab(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    text = { Text("Page Source") }
                )
                Tab(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    text = { Text("User-Agent") }
                )
            }

            when (selectedTab) {
                0 -> {
                    // Diagnostics Tab with real measurements and copy
                    val tabsList by viewModel.tabs.collectAsState()
                    val proxyState by viewModel.networkManager.proxyManager.proxyState.collectAsState()
                    val totalAds by com.example.protection.BlockStatsManager.totalAdsBlocked.collectAsState()
                    val totalTrackers by com.example.protection.BlockStatsManager.totalTrackersBlocked.collectAsState()
                    val extensionsList by viewModel.extensionManager.extensions.collectAsState()

                    val activeWebViewsCount = tabsList.size
                    val suspendedCount = tabsList.count { it.isSuspended }

                    val report = remember(tabsList, proxyState, totalAds, totalTrackers, extensionsList, config.performanceMode) {
                        com.example.performance.DiagnosticsCollector.generateReport(
                            context = context,
                            activeWebViews = activeWebViewsCount,
                            suspendedTabs = suspendedCount,
                            performanceMode = config.performanceMode,
                            extensionCount = extensionsList.size,
                            proxyState = proxyState,
                            adsBlocked = totalAds,
                            trackersBlocked = totalTrackers
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("System Diagnostics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Button(
                                            onClick = {
                                                val text = com.example.performance.DiagnosticsCollector.toFormattedString(report)
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("ATP Diagnostics", text))
                                                Toast.makeText(context, "Diagnostics copied (redacted)", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.surface)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Diagnostics")
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                    InfoPair("Device", report.deviceModel)
                                    InfoPair("Android OS", report.androidVersion)
                                    InfoPair("Chromium WebView", report.webViewVersion)
                                    InfoPair("JVM Memory Used", report.memoryUsageMb)
                                    InfoPair("Performance Mode", report.performanceMode)
                                    InfoPair("Active WebViews", "${report.activeWebViews}")
                                    InfoPair("Suspended Tabs", "${report.suspendedTabs}")
                                    InfoPair("Extensions Loaded", "${report.extensionCount}")
                                    InfoPair("Proxy State", report.proxyState)
                                    InfoPair("Real Ads Blocked", "${report.adsBlocked}")
                                    InfoPair("Real Trackers Blocked", "${report.trackersBlocked}")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Console Logs
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No console logs captured yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(logs) { log ->
                                ConsoleLogCard(log)
                            }
                        }
                    }
                }
                2 -> {
                    // WebView Details (Requirement 91)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("WebView Runtime Information", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                    Divider()
                                    InfoPair("Default User-Agent", defaultUa)
                                    InfoPair("JavaScript Engine", "V8 / Android WebKit")
                                    InfoPair("DOM Storage", "Enabled (LocalStorage & IndexedDB)")
                                    InfoPair("Cookie Engine", "Android CookieManager Active")
                                    InfoPair("Safe Browsing", "Active System Client")
                                    InfoPair("Hardware Acceleration", if (config.hardwareAcceleration) "Enabled (GPU)" else "Disabled")
                                    InfoPair("Multi-Window Support", "Enabled")
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // View Page Source (Requirement 89)
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = sourceSearchQuery,
                                onValueChange = { sourceSearchQuery = it },
                                placeholder = { Text("Search source...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Source", pageSource ?: "No source loaded"))
                                Toast.makeText(context, "Page source copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val displayText = pageSource ?: "<!DOCTYPE html>\n<html>\n<head><title>ATP Browser</title></head>\n<body>\n  <h1>ATP Browser Local Engine</h1>\n  <p>Secure DOM Sandbox initialized.</p>\n</body>\n</html>"
                            LazyColumn(modifier = Modifier.padding(12.dp)) {
                                item {
                                    Text(
                                        text = displayText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // User-Agent Editor (Requirement 49, 126)
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            "Custom User-Agent Editor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Override the User-Agent sent by the browser to websites. Plain configuration string; code execution is forbidden.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = customUaInput,
                            onValueChange = { customUaInput = it },
                            label = { Text("Custom User-Agent String") },
                            placeholder = { Text("Leave blank for default Android WebView") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    customUaInput = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Desktop Presets", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { customUaInput = "" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset Default", fontSize = 11.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                viewModel.preferences.updateConfig { it.copy(customUserAgent = customUaInput.trim()) }
                                Toast.makeText(context, "User-Agent updated", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save User-Agent")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConsoleLogCard(log: ConsoleLogEntry) {
    val (color, label) = when (log.level) {
        ConsoleMessage.MessageLevel.ERROR -> Pair(SecurityRed, "ERROR")
        ConsoleMessage.MessageLevel.WARNING -> Pair(SecurityAmber, "WARN")
        ConsoleMessage.MessageLevel.LOG -> Pair(CyberCyan, "LOG")
        else -> Pair(MaterialTheme.colorScheme.onSurface, "INFO")
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "[$label]",
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${log.sourceId.substringAfterLast('/')}:${log.lineNumber}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = log.message,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun InfoPair(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
    }
}
