package com.example.ui.dialogs

import android.net.Uri
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.protection.BlockStatsManager
import com.example.protection.SiteExceptionManager
import com.example.ui.BrowserViewModel
import com.example.ui.ScreenState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@Composable
fun ShieldDialog(
    url: String,
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    val host = remember(url) {
        try { Uri.parse(url).host ?: "This Site" } catch (e: Exception) { "This Site" }
    }
    var isShieldDisabled by remember(url) {
        mutableStateOf(SiteExceptionManager.isShieldDisabled(host))
    }
    var showLogs by remember { mutableStateOf(false) }

    val siteBlockedCount = remember(url, isShieldDisabled) {
        BlockStatsManager.getSiteBlockedCount(host)
    }
    val totalAds by BlockStatsManager.totalAdsBlocked.collectAsState()
    val totalTrackers by BlockStatsManager.totalTrackersBlocked.collectAsState()
    val totalBlocked by BlockStatsManager.totalRequestsBlocked.collectAsState()
    val blockedLogs by BlockStatsManager.blockedLog.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = if (isShieldDisabled) SecurityRed else CyberCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("ATP Shield", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = host,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Protection Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (isShieldDisabled) "Disabled for this site" else "Shield Active",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isShieldDisabled) SecurityRed else SecurityGreen
                            )
                        }
                        Switch(
                            checked = !isShieldDisabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    SiteExceptionManager.enableShieldForSite(host)
                                    isShieldDisabled = false
                                } else {
                                    SiteExceptionManager.disableShieldForSite(host)
                                    isShieldDisabled = true
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCard("Blocked Here", "$siteBlockedCount", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    StatCard("Total Trackers", "$totalTrackers", Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    StatCard("Total Ads", "$totalAds", Modifier.weight(1f))
                }

                if (showLogs) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Recent Blocked Requests", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        val siteLogs = blockedLogs.filter { it.domain.contains(host, ignoreCase = true) || host.contains(it.domain, ignoreCase = true) }
                        if (siteLogs.isEmpty()) {
                            item {
                                Text("No requests blocked on this site yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(siteLogs.takeLast(15).reversed()) { entry ->
                                Text(
                                    text = "• [${entry.resourceType}] ${entry.domain} (${entry.reason})",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showLogs = !showLogs }) {
                Text(if (showLogs) "Hide Log" else "Blocked Requests")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                viewModel.navigateToScreen(ScreenState.PRIVACY_DASHBOARD)
            }) {
                Text("Privacy Dashboard")
            }
        }
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = CyberCyan)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
