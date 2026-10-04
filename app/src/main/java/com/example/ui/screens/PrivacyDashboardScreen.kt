package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.protection.BlockStatsManager
import com.example.protection.FilterListManager
import com.example.protection.SiteExceptionManager
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val totalAds by BlockStatsManager.totalAdsBlocked.collectAsState()
    val totalTrackers by BlockStatsManager.totalTrackersBlocked.collectAsState()
    val totalRequests by BlockStatsManager.totalRequestsBlocked.collectAsState()
    val blockedLog by BlockStatsManager.blockedLog.collectAsState()

    var customRuleInput by remember { mutableStateOf("") }
    var customRuleType by remember { mutableStateOf("Block") } // "Block" or "Allow"
    var exceptionsList by remember { mutableStateOf(SiteExceptionManager.getAllExceptions()) }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { BlockStatsManager.resetStats() }) {
                        Text("Reset Stats")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Stats Cards
            item {
                Text("Real-Time Protection Metrics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricBox("Ads Blocked", "$totalAds", CyberCyan, Modifier.weight(1f))
                    MetricBox("Trackers Blocked", "$totalTrackers", SecurityGreen, Modifier.weight(1f))
                    MetricBox("Total Requests", "$totalRequests", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }

            // Site Exceptions Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Site Exceptions (${exceptionsList.size})", fontWeight = FontWeight.Bold)
                            if (exceptionsList.isNotEmpty()) {
                                TextButton(onClick = {
                                    SiteExceptionManager.clearExceptions()
                                    exceptionsList = emptyList()
                                }) {
                                    Text("Clear All")
                                }
                            }
                        }
                        Text(
                            "Shield protection is explicitly disabled for these sites.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (exceptionsList.isEmpty()) {
                            Text("No exceptions configured. Shield is active on all websites.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            for (domain in exceptionsList) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(domain, style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = {
                                            SiteExceptionManager.enableShieldForSite(domain)
                                            exceptionsList = SiteExceptionManager.getAllExceptions()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = SecurityRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom Blocking Rules
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Custom Rules", fontWeight = FontWeight.Bold)
                        Text(
                            "Add custom domains to always block or always allow.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customRuleInput,
                                onValueChange = { customRuleInput = it },
                                placeholder = { Text("e.g. adserver.net") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val rule = customRuleInput.trim()
                                    if (rule.isNotBlank()) {
                                        if (customRuleType == "Block") {
                                            FilterListManager.addCustomBlock(rule)
                                        } else {
                                            FilterListManager.addCustomAllow(rule)
                                        }
                                        customRuleInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text("Add")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = customRuleType == "Block",
                                onClick = { customRuleType = "Block" },
                                label = { Text("Block Rule") }
                            )
                            FilterChip(
                                selected = customRuleType == "Allow",
                                onClick = { customRuleType = "Allow" },
                                label = { Text("Allowlist Rule") }
                            )
                        }
                    }
                }
            }

            // Blocked Request Log
            item {
                Text("Blocked Requests Log (${blockedLog.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (blockedLog.isEmpty()) {
                item {
                    Text("No requests blocked yet in this session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(blockedLog.reversed().take(50)) { entry ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.domain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${entry.resourceType} • ${entry.reason}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = dateFormat.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
