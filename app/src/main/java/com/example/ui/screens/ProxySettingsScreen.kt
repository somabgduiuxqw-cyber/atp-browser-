package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.network.ProxyState
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import com.example.ui.theme.SecurityAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxySettingsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val proxyState by viewModel.networkManager.proxyManager.proxyState.collectAsState()
    val activeProxy by viewModel.networkManager.proxyManager.activeProxy.collectAsState()
    val lastError by viewModel.networkManager.proxyManager.lastError.collectAsState()
    val isSupported = remember { viewModel.networkManager.proxyManager.isProxyFeatureSupported() }

    var endpointInput by remember { mutableStateOf(activeProxy?.endpoint ?: "") }
    var bypassRules by remember { mutableStateOf(activeProxy?.bypassRules ?: listOf("localhost", "127.0.0.1", "*.local")) }
    var newBypassRule by remember { mutableStateOf("") }

    val isTesting by viewModel.isTestingProxy.collectAsState()
    val testResult by viewModel.proxyTestResult.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User Proxy", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Feature support notice
            if (!isSupported) {
                item {
                    Surface(
                        color = SecurityRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = SecurityRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Proxy Not Supported", fontWeight = FontWeight.Bold, color = SecurityRed)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "This device's Android WebView version does not support the AndroidX WebKit PROXY_OVERRIDE feature. ATP Browser never fakes proxy connections.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Status Card
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
                            Text("Proxy Status", fontWeight = FontWeight.Bold)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = when (proxyState) {
                                    ProxyState.CONNECTED -> SecurityGreen.copy(alpha = 0.2f)
                                    ProxyState.CONFIGURING, ProxyState.TESTING -> SecurityAmber.copy(alpha = 0.2f)
                                    ProxyState.FAILED, ProxyState.UNSUPPORTED -> SecurityRed.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                }
                            ) {
                                Text(
                                    text = proxyState.name,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when (proxyState) {
                                        ProxyState.CONNECTED -> SecurityGreen
                                        ProxyState.CONFIGURING, ProxyState.TESTING -> SecurityAmber
                                        ProxyState.FAILED, ProxyState.UNSUPPORTED -> SecurityRed
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }

                        if (activeProxy != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Active: ${activeProxy?.endpoint}", style = MaterialTheme.typography.bodySmall, color = CyberCyan)
                            Text("Source: ${activeProxy?.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (lastError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Error: $lastError", style = MaterialTheme.typography.bodySmall, color = SecurityRed)
                        }
                    }
                }
            }

            // Endpoint Configuration
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Proxy Endpoint", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Enter your user-provided proxy (e.g. http://192.168.1.100:8080 or socks5://...)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = endpointInput,
                            onValueChange = { endpointInput = it },
                            placeholder = { Text("http://proxy.example.com:8080") },
                            singleLine = true,
                            enabled = isSupported,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Connection test feedback
                        if (isTesting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Testing socket reachability...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (testResult != null) {
                            val res = testResult!!
                            Surface(
                                color = if (res.success) SecurityGreen.copy(alpha = 0.15f) else SecurityRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (res.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (res.success) SecurityGreen else SecurityRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = res.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (res.success) SecurityGreen else SecurityRed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.testProxy(endpointInput) },
                                enabled = isSupported && endpointInput.isNotBlank() && !isTesting
                            ) {
                                Text("Test Connection")
                            }

                            Button(
                                onClick = {
                                    viewModel.applyUserProxy(endpointInput, bypassRules) { ok, err ->
                                        if (ok) {
                                            Toast.makeText(context, "Proxy applied successfully", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Proxy error: $err", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = isSupported && endpointInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text("Apply Proxy", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (proxyState == ProxyState.CONNECTED) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    viewModel.clearProxy {
                                        Toast.makeText(context, "Proxy disabled, direct network restored", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp), tint = SecurityRed)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Proxy / Direct Connection", color = SecurityRed)
                            }
                        }
                    }
                }
            }

            // Bypass Rules
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Bypass Rules", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Traffic to matching hosts will not route through proxy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newBypassRule,
                                onValueChange = { newBypassRule = it },
                                placeholder = { Text("e.g. *.local or 10.0.0.0/8") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newBypassRule.isNotBlank() && !bypassRules.contains(newBypassRule.trim())) {
                                        bypassRules = bypassRules + newBypassRule.trim()
                                        newBypassRule = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Rule", tint = CyberCyan)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        for (rule in bypassRules) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(rule, style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = { bypassRules = bypassRules.filter { it != rule } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Disclaimer & Limitations notice
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Proxy Scope Limitation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "ATP Browser does not supply public proxies. The proxy applies strictly to ATP Browser's supported WebView architecture. It is not a device-wide VPN and does not route other applications.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
