package com.example.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.AccessManager
import com.example.data.security.AdBlockManager
import com.example.ui.BrowserViewModel
import com.example.ui.ScreenState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by viewModel.config.collectAsState()

    var settingsSearchQuery by remember { mutableStateOf("") }

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showSearchEngineDialog by remember { mutableStateOf(false) }

    val isDefault = remember { AccessManager.isDefaultBrowser(context) }
    val remainingAccess = remember { AccessManager.getRemainingTimeFormatted(context) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Settings Search Bar (Requirement 94)
            item {
                OutlinedTextField(
                    value = settingsSearchQuery,
                    onValueChange = { settingsSearchQuery = it },
                    placeholder = { Text("Search settings (DNS, Security, Tabs, Battery)...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (settingsSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { settingsSearchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Default Browser Card (Requirements 10 & 11)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Default Browser", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isDefault) "✓ ATP Browser is your default browser" else "ATP Browser is not your default browser",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (isDefault) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { AccessManager.launchDefaultBrowserSelector(context) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("set_default_browser_button")
                        ) {
                            Text("Set as Default Browser", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Access Session Status Card (Requirements 6, 7, 11)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.navigateToScreen(ScreenState.ACCESS_KEY) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Access Key Session", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(
                                text = "✓ Access Activated • 1-Day Access ($remainingAccess)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // General & Search Engine
            item {
                SettingsSectionTitle("GENERAL & SEARCH")
            }

            item {
                SettingsNavigationItem(
                    title = "Search Engine",
                    subtitle = "Current: ${config.searchEngine}",
                    icon = Icons.Default.Search,
                    onClick = { showSearchEngineDialog = true }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Keep Alive Background Sites",
                    subtitle = "Manage background web sessions and policies",
                    icon = Icons.Default.Sync,
                    onClick = { viewModel.navigateToScreen(ScreenState.BACKGROUND_SITES) }
                )
            }

            // Extensions
            item {
                SettingsSectionTitle("ATP EXTENSIONS")
            }

            item {
                SettingsNavigationItem(
                    title = "Extension Manager",
                    subtitle = "Manage custom scripts, themes, and extensions",
                    icon = Icons.Default.Extension,
                    onClick = { viewModel.navigateToScreen(ScreenState.EXTENSIONS) }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Create Extension",
                    subtitle = "Write or edit config.js, script.js, and styles.css",
                    icon = Icons.Default.Code,
                    onClick = { viewModel.openExtensionEditor(null) }
                )
            }

            // Network & Proxy
            item {
                SettingsSectionTitle("NETWORK & PROXY")
            }

            item {
                val proxyState by viewModel.networkManager.proxyManager.proxyState.collectAsState()
                SettingsNavigationItem(
                    title = "User Proxy",
                    subtitle = "Status: ${proxyState.name} • AndroidX WebKit ProxyController",
                    icon = Icons.Default.VpnLock,
                    onClick = { viewModel.navigateToScreen(ScreenState.PROXY_SETTINGS) }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Custom DNS Resolver",
                    subtitle = "Configure private IPv4, IPv6, DoH, DoT",
                    icon = Icons.Default.Dns,
                    onClick = { viewModel.navigateToScreen(ScreenState.DNS_SETTINGS) }
                )
            }

            // Privacy & Content Protection
            item {
                SettingsSectionTitle("PRIVACY & PROTECTION")
            }

            item {
                SettingsNavigationItem(
                    title = "ATP Security Center",
                    subtitle = "View connection, certificate, and safe browsing posture",
                    icon = Icons.Default.Security,
                    onClick = { viewModel.navigateToScreen(ScreenState.SECURITY_CENTER) }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Privacy Dashboard",
                    subtitle = "Real-time statistics, site exceptions, and blocked logs",
                    icon = Icons.Default.Shield,
                    onClick = { viewModel.navigateToScreen(ScreenState.PRIVACY_DASHBOARD) }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Site Permissions & Data",
                    subtitle = "Manage camera, microphone, cookies, and clear site data",
                    icon = Icons.Default.PermDeviceInformation,
                    onClick = { viewModel.navigateToScreen(ScreenState.SITE_PERMISSIONS) }
                )
            }

            item {
                SettingsSwitchItem(
                    title = "Ad & Banner Blocking",
                    subtitle = "Block annoying ads and popup networks",
                    icon = Icons.Default.Block,
                    checked = config.adBlockingEnabled,
                    onCheckedChange = { checked ->
                        viewModel.preferences.updateConfig { it.copy(adBlockingEnabled = checked) }
                        AdBlockManager.isGlobalAdBlockingEnabled = checked
                    }
                )
            }

            item {
                SettingsSwitchItem(
                    title = "Tracking Protection",
                    subtitle = "Block third-party tracking scripts & telemetry",
                    icon = Icons.Default.VisibilityOff,
                    checked = config.trackingProtectionEnabled,
                    onCheckedChange = { checked ->
                        viewModel.preferences.updateConfig { it.copy(trackingProtectionEnabled = checked) }
                        AdBlockManager.isGlobalTrackingProtectionEnabled = checked
                    }
                )
            }

            item {
                SettingsSwitchItem(
                    title = "HTTPS-Only Mode",
                    subtitle = "Enforce secure encrypted TLS on all connections",
                    icon = Icons.Default.Lock,
                    checked = config.httpsOnlyMode,
                    onCheckedChange = { checked ->
                        viewModel.preferences.updateConfig { it.copy(httpsOnlyMode = checked) }
                    }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Clear Browsing Data",
                    subtitle = "Cookies, cache, history, temporary download cache",
                    icon = Icons.Default.DeleteSweep,
                    onClick = { showClearDataDialog = true }
                )
            }

            // Performance & Memory
            item {
                SettingsSectionTitle("PERFORMANCE & BATTERY")
            }

            item {
                SettingsNavigationItem(
                    title = "Performance Mode",
                    subtitle = "Mode: ${config.performanceMode}",
                    icon = Icons.Default.Speed,
                    onClick = {
                        val nextMode = when (config.performanceMode) {
                            "Balanced" -> "Performance"
                            "Performance" -> "Battery Saver"
                            else -> "Balanced"
                        }
                        viewModel.preferences.updateConfig { it.copy(performanceMode = nextMode) }
                        Toast.makeText(context, "Performance mode set to $nextMode", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Automatic Tab Suspension",
                    subtitle = if (config.autoSuspendMinutes > 0) "After ${config.autoSuspendMinutes} minutes" else "Disabled",
                    icon = Icons.Default.Bedtime,
                    onClick = {
                        val nextSuspend = when (config.autoSuspendMinutes) {
                            0 -> 15
                            15 -> 30
                            30 -> 60
                            60 -> 360
                            else -> 0
                        }
                        viewModel.preferences.updateConfig { it.copy(autoSuspendMinutes = nextSuspend) }
                        val label = if (nextSuspend > 0) "After $nextSuspend min" else "Disabled"
                        Toast.makeText(context, "Tab suspension: $label", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Interface Customization
            item {
                SettingsSectionTitle("LAYOUT & APPEARANCE")
            }

            item {
                SettingsSwitchItem(
                    title = "Address Bar at Bottom",
                    subtitle = "Place address bar at the bottom for easier reach",
                    icon = Icons.Default.VerticalAlignBottom,
                    checked = config.addressBarPosition == "BOTTOM",
                    onCheckedChange = { isBottom ->
                        viewModel.preferences.updateConfig { it.copy(addressBarPosition = if (isBottom) "BOTTOM" else "TOP") }
                    }
                )
            }

            item {
                SettingsSwitchItem(
                    title = "Always Show Full URL",
                    subtitle = "Display full path and query in the address bar",
                    icon = Icons.Default.Link,
                    checked = config.alwaysShowFullUrl,
                    onCheckedChange = { checked ->
                        viewModel.preferences.updateConfig { it.copy(alwaysShowFullUrl = checked) }
                    }
                )
            }

            // Advanced & Developer
            item {
                SettingsSectionTitle("ADVANCED & ABOUT")
            }

            item {
                SettingsNavigationItem(
                    title = "Developer Mode & Console",
                    subtitle = "Inspect console logs, page source, and runtime info",
                    icon = Icons.Default.Code,
                    onClick = { viewModel.navigateToScreen(ScreenState.DEVELOPER_MODE) }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Privacy Policy",
                    subtitle = "Local offline on-device privacy guarantee",
                    icon = Icons.Default.Policy,
                    onClick = { showPrivacyDialog = true }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "About ATP Browser",
                    subtitle = "Version 1.0 • System WebView",
                    icon = Icons.Default.Info,
                    onClick = { showAboutDialog = true }
                )
            }

            item {
                SettingsNavigationItem(
                    title = "Reset Browser Settings",
                    subtitle = "Restore default settings without deleting data",
                    icon = Icons.Default.Restore,
                    onClick = { showResetDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Search Engine Selection Dialog
    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Select Search Engine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("DuckDuckGo", "Google", "Bing", "Brave Search").forEach { engine ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.preferences.updateConfig { it.copy(searchEngine = engine) }
                                    showSearchEngineDialog = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = config.searchEngine == engine,
                                onClick = {
                                    viewModel.preferences.updateConfig { it.copy(searchEngine = engine) }
                                    showSearchEngineDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(engine, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Clear Browsing Data Dialog (Requirements 28, 67, 96)
    if (showClearDataDialog) {
        var clearHistory by remember { mutableStateOf(true) }
        var clearCookies by remember { mutableStateOf(true) }
        var clearCache by remember { mutableStateOf(true) }
        var clearPartials by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Browsing Data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearHistory, onCheckedChange = { clearHistory = it })
                        Text("Browsing history")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearCookies, onCheckedChange = { clearCookies = it })
                        Text("Cookies & site data")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearCache, onCheckedChange = { clearCache = it })
                        Text("Cached web images & files")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearPartials, onCheckedChange = { clearPartials = it })
                        Text("Incomplete partial download cache")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearBrowsingData(clearHistory, clearCookies, clearCache, clearPartials)
                        showClearDataDialog = false
                        Toast.makeText(context, "Selected data cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityRed)
                ) {
                    Text("Clear Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Browser Settings?") },
            text = { Text("This will reset all toolbar, appearance, and network preferences back to defaults. Your bookmarks, downloads, and history will NOT be deleted.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.preferences.resetPreferences()
                        showResetDialog = false
                        Toast.makeText(context, "Preferences reset", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityRed)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // About Dialog (Requirement 113)
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About ATP Browser") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ATP Browser", fontWeight = FontWeight.Black, fontSize = 18.sp, color = CyberCyan)
                    Text("Version: 1.0.0 (Production Release)")
                    Text("Core Engine: Android System WebView")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Built-in Core Technologies:", fontWeight = FontWeight.Bold)
                    Text("• Keep Alive Background Sites Architecture")
                    Text("• Zero-Trust User Configured Custom DNS (DoH/DoT)")
                    Text("• Ad & Tracking Shield with Local Rules")
                    Text("• Resumable Range-based Download Manager")
                    Text("• Heuristic Code & Risky File Security Scanner")
                    Text("• Android Keystore AES-256 Storage Protection")
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Privacy Policy Dialog (Requirement 114)
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Local Privacy Policy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("ATP Browser Privacy Guarantee:", fontWeight = FontWeight.Bold)
                    Text("• 100% On-Device: Your history, bookmarks, tabs, and cookies never leave your device.")
                    Text("• No Hidden Telemetry: ATP Browser has zero analytics or hidden developer trackers.")
                    Text("• User Controlled DNS: Custom DNS queries go strictly to the resolvers you configure.")
                    Text("• No Silent Remote Suggestions: Address bar suggestions default to local bookmarks and history.")
                    Text("• Sandboxed Downloads: Executable files are never run directly.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = CyberCyan,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun SettingsNavigationItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
