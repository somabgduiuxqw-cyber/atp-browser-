package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.example.data.db.DnsProfile
import com.example.data.dns.DnsManager
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsSettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val profiles by viewModel.db.getAllDnsProfiles().collectAsState(initial = emptyList())
    val dnsTestResult by viewModel.dnsTestResult.collectAsState()
    val isTesting by viewModel.isTestingDns.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom DNS Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add DNS Profile")
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
            // DNS Status Banner
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (DnsManager.isCustomDnsEnabled()) SecurityGreen else SecurityAmber,
                                    modifier = Modifier.size(10.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (DnsManager.isCustomDnsEnabled()) "Custom DNS Enabled" else "System DNS Active",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (DnsManager.isCustomDnsEnabled()) SecurityGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (DnsManager.isCustomDnsEnabled()) {
                                OutlinedButton(
                                    onClick = { viewModel.disableCustomDns() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Restore System DNS", fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (DnsManager.isCustomDnsEnabled()) {
                                "Active Resolver: ${DnsManager.activeProfileName} (${DnsManager.primaryServer}) [${DnsManager.protocol}]"
                            } else {
                                "Using Android's default system network DNS. No custom resolver active."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Note: ATP Browser never forces a hidden DNS. You must explicitly configure and test resolvers.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // DNS Test Tool Results (if available)
            if (dnsTestResult != null || isTesting) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "DNS Test Diagnostics",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            if (isTesting) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = CyberCyan)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Testing resolver reachability and latency...", style = MaterialTheme.typography.bodySmall)
                            } else dnsTestResult?.let { res ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Server: ${res.server} (${res.protocol})", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = if (res.isReachable) "Reachable" else "Failed",
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.isReachable) SecurityGreen else SecurityRed
                                    )
                                }
                                Text("Response Time: ${res.responseTimeMs} ms", style = MaterialTheme.typography.bodySmall)
                                res.resolvedIp?.let {
                                    Text("Resolution: $it", style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                                }
                                res.error?.let {
                                    Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = SecurityRed)
                                }
                            }
                        }
                    }
                }
            }

            // Configured DNS Profiles
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configured DNS Profiles",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Button(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add DNS")
                    }
                }
            }

            if (profiles.isEmpty()) {
                item {
                    Text(
                        text = "No custom DNS profiles entered yet. Tap 'Add DNS' to configure IPv4/IPv6 or DoH resolvers (e.g. 1.1.1.1, 8.8.8.8).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(profiles, key = { it.id }) { profile ->
                    DnsProfileCard(
                        profile = profile,
                        onEnable = { viewModel.enableCustomDns(profile) },
                        onDisable = { viewModel.disableCustomDns() },
                        onTest = { viewModel.testDns(profile.primaryServer, profile.protocol) },
                        onDelete = {
                            scope.launch {
                                if (profile.enabled) viewModel.disableCustomDns()
                                viewModel.db.deleteDnsProfile(profile)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var primaryServer by remember { mutableStateOf("") }
        var secondaryServer by remember { mutableStateOf("") }
        var protocol by remember { mutableStateOf("Plain") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add DNS Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Profile Name (e.g. My DNS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = primaryServer,
                        onValueChange = { primaryServer = it },
                        label = { Text("Primary DNS Server (e.g. 1.1.1.1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = secondaryServer,
                        onValueChange = { secondaryServer = it },
                        label = { Text("Secondary DNS Server (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Protocol:", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Plain", "DoH", "DoT").forEach { prot ->
                            FilterChip(
                                selected = protocol == prot,
                                onClick = { protocol = prot },
                                label = { Text(prot) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && primaryServer.isNotBlank()) {
                            scope.launch {
                                viewModel.db.insertDnsProfile(
                                    DnsProfile(
                                        name = name.trim(),
                                        primaryServer = primaryServer.trim(),
                                        secondaryServer = secondaryServer.trim(),
                                        protocol = protocol,
                                        enabled = false
                                    )
                                )
                            }
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DnsProfileCard(
    profile: DnsProfile,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Primary: ${profile.primaryServer} • Protocol: ${profile.protocol}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (profile.secondaryServer.isNotBlank()) {
                        Text(
                            text = "Secondary: ${profile.secondaryServer}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = SecurityRed)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onTest, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test DNS", fontSize = 12.sp)
                }

                if (profile.enabled) {
                    Button(
                        onClick = onDisable,
                        colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Active", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(onClick = onEnable, shape = RoundedCornerShape(10.dp)) {
                        Text("Enable", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
