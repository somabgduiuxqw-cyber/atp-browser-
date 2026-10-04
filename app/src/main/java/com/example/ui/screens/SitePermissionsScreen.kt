package com.example.ui.screens

import android.net.Uri
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
import com.example.security.DomainPermissions
import com.example.security.PermissionValue
import com.example.security.SiteDataManager
import com.example.security.SitePermissionManager
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SitePermissionsScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUrl by viewModel.currentUrl.collectAsState()
    val activeDomain = remember(currentUrl) {
        try { Uri.parse(currentUrl).host ?: "example.com" } catch (e: Exception) { "example.com" }
    }

    var selectedDomain by remember { mutableStateOf(activeDomain) }
    var permissions by remember(selectedDomain) {
        mutableStateOf(SitePermissionManager.getPermissions(selectedDomain))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site Permissions", fontWeight = FontWeight.Bold) },
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
            // Target Domain Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Current Site", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(selectedDomain, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CyberCyan)
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    SiteDataManager.clearDataForDomain(context, selectedDomain) {
                                        Toast.makeText(context, "Cleared site data for $selectedDomain", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Site Data")
                            }

                            TextButton(
                                onClick = {
                                    SitePermissionManager.resetDomain(selectedDomain)
                                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                                    Toast.makeText(context, "Reset permissions to defaults", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Reset Defaults", color = SecurityRed)
                            }
                        }
                    }
                }
            }

            // Permissions list
            item {
                Text("Permissions for $selectedDomain", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }

            item {
                PermissionSelector("Camera", permissions.camera) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(camera = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }

            item {
                PermissionSelector("Microphone", permissions.microphone) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(microphone = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }

            item {
                PermissionSelector("Location", permissions.location) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(location = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }

            item {
                PermissionSelector("Notifications", permissions.notifications) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(notifications = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }

            item {
                PermissionSelector("Popups & Redirects", permissions.popups) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(popups = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }

            item {
                PermissionSelector("JavaScript", permissions.javascript) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(javascript = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }

            item {
                PermissionSelector("Cookies", permissions.cookies) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(cookies = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }

            item {
                PermissionSelector("Media Autoplay", permissions.autoplay) { value ->
                    SitePermissionManager.setPermission(selectedDomain) { it.copy(autoplay = value) }
                    permissions = SitePermissionManager.getPermissions(selectedDomain)
                }
            }
        }
    }
}

@Composable
private fun PermissionSelector(label: String, currentValue: PermissionValue, onSelect: (PermissionValue) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (v in PermissionValue.values()) {
                    FilterChip(
                        selected = currentValue == v,
                        onClick = { onSelect(v) },
                        label = { Text(v.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }
    }
}
