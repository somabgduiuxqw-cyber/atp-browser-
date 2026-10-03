package com.example.ui.screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.SecurityEvent
import com.example.data.security.AdBlockManager
import com.example.data.security.SecurityScanner
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCenterScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val securityEvents by viewModel.db.getAllSecurityEvents().collectAsState(initial = emptyList())
    val totalBlocked by AdBlockManager.totalBlockedCount.collectAsState()
    val totalTrackers by AdBlockManager.totalTrackersBlockedCount.collectAsState()

    var codeScannerInput by remember { mutableStateOf("") }
    var codeScannerFilename by remember { mutableStateOf("script.js") }
    var scanResult by remember { mutableStateOf<SecurityScanner.ScanResult?>(null) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Center", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch { viewModel.db.clearAllSecurityEvents() }
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Security Events")
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
            // Main Protection Shield Overview
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Shield",
                            tint = SecurityGreen,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No known threat detected", // NEVER "100% Safe"
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = SecurityGreen
                        )
                        Text(
                            text = "Active multi-layered shield running on device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatBox(title = "Blocked Ads", count = "$totalBlocked", icon = Icons.Default.Block, color = CyberCyan)
                            StatBox(title = "Trackers", count = "$totalTrackers", icon = Icons.Default.VisibilityOff, color = SecurityGreen)
                            StatBox(title = "TLS / HTTPS", count = "Enforced", icon = Icons.Default.Lock, color = SecurityGreen)
                        }
                    }
                }
            }

            // Heuristic Code Scanner Section
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = CyberCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Heuristic Code & Script Scanner",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Text(
                            "Inspects JS, Python, HTML, shell scripts for malicious code patterns without executing them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = codeScannerFilename,
                            onValueChange = { codeScannerFilename = it },
                            label = { Text("Filename (.apk, .html, .htm, .js, .py, .sh)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = codeScannerInput,
                            onValueChange = { codeScannerInput = it },
                            label = { Text("Paste code snippet to inspect") },
                            placeholder = { Text("e.g. eval(atob('...')) or document.cookie") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            maxLines = 6
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                scanResult = SecurityScanner.scanCodeContent(codeScannerFilename, codeScannerInput)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Script")
                        }

                        scanResult?.let { res ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (res.isThreatDetected) SecurityAmber.copy(alpha = 0.15f) else SecurityGreen.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Status: ${res.status}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.isThreatDetected) SecurityAmber else SecurityGreen
                                    )
                                    res.threatReason?.let {
                                        Text(text = it, style = MaterialTheme.typography.bodySmall)
                                    }
                                    for (d in res.details) {
                                        Text(text = "• $d", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Security Event History
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Security Events Log",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${securityEvents.size} logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (securityEvents.isEmpty()) {
                item {
                    Text(
                        text = "No security events recorded yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(securityEvents.take(50)) { event ->
                    SecurityEventItem(event)
                }
            }
        }
    }
}

@Composable
fun StatBox(title: String, count: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = count, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SecurityEventItem(event: SecurityEvent) {
    val dateStr = remember(event.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp))
    }
    val icon = when {
        event.eventType.contains("Certificate") -> Icons.Default.GppMaybe
        event.eventType.contains("Tracker") -> Icons.Default.VisibilityOff
        event.eventType.contains("Ad") -> Icons.Default.Block
        else -> Icons.Default.Warning
    }
    val color = when {
        event.eventType.contains("Certificate") -> SecurityRed
        event.eventType.contains("Tracker") -> SecurityGreen
        event.eventType.contains("Ad") -> CyberCyan
        else -> SecurityAmber
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = event.eventType, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(text = dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(text = event.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = event.domain, style = MaterialTheme.typography.labelSmall, color = CyberCyan)
            }
        }
    }
}
