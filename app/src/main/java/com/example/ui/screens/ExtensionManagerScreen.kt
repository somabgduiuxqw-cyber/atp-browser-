package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.sp
import com.example.extensions.AtpExtension
import com.example.ui.BrowserViewModel
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityRed
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionManagerScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val extensions by viewModel.extensionManager.extensions.collectAsState()
    val logs by viewModel.extensionManager.logs.collectAsState()

    var selectedExtLogs by remember { mutableStateOf<AtpExtension?>(null) }
    var pendingExportExt by remember { mutableStateOf<AtpExtension?>(null) }

    // File launcher for importing .atpext
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val (success, msg) = viewModel.extensionManager.importExtension(stream)
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Export launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val ext = pendingExportExt
        if (uri != null && ext != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    val ok = viewModel.extensionManager.exportExtension(ext.id, stream)
                    if (ok) {
                        Toast.makeText(context, "Exported ${ext.name}.atpext", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            pendingExportExt = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ATP Extensions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Import Extension")
                    }
                    IconButton(onClick = { viewModel.openExtensionEditor(null) }) {
                        Icon(Icons.Default.Add, contentDescription = "Create Extension")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (extensions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Extensions Installed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create custom page scripts, styles, or import an .atpext package.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.openExtensionEditor(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Extension", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(extensions, key = { it.id }) { ext ->
                        ExtensionCard(
                            extension = ext,
                            onToggle = { enabled -> viewModel.extensionManager.setExtensionEnabled(ext.id, enabled) },
                            onEdit = { viewModel.openExtensionEditor(ext.id) },
                            onLogs = { selectedExtLogs = ext },
                            onExport = {
                                pendingExportExt = ext
                                exportLauncher.launch("${ext.name.replace(" ", "_")}.atpext")
                            },
                            onClearData = {
                                viewModel.extensionManager.clearExtensionData(ext.id)
                                Toast.makeText(context, "Storage cleared for ${ext.name}", Toast.LENGTH_SHORT).show()
                            },
                            onRemove = {
                                viewModel.extensionManager.removeExtension(ext.id)
                                Toast.makeText(context, "Removed ${ext.name}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Extension Logs Dialog
    selectedExtLogs?.let { ext ->
        val extLogs = logs.filter { it.extensionId == ext.id }
        AlertDialog(
            onDismissRequest = { selectedExtLogs = null },
            title = { Text("${ext.name} Logs") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (extLogs.isEmpty()) {
                        Text("No logs recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.height(220.dp)) {
                            items(extLogs) { log ->
                                Text(
                                    text = "[${log.level}] ${log.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (log.level == "ERROR") SecurityRed else MaterialTheme.colorScheme.onSurface
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.extensionManager.clearLogs(ext.id) }) {
                    Text("Clear Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedExtLogs = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun ExtensionCard(
    extension: AtpExtension,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onLogs: () -> Unit,
    onExport: () -> Unit,
    onClearData: () -> Unit,
    onRemove: () -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v${extension.version} • by ${extension.author}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = extension.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            if (extension.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = extension.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Error notice if auto-disabled
            if (extension.lastError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = SecurityRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = SecurityRed, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Error: ${extension.lastError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecurityRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Details tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text("${extension.matches.size} match rules", fontSize = 11.sp) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text("${extension.permissions.size} perms", fontSize = 11.sp) }
                )
                if (extension.proxyConfig?.enabled == true) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Proxy configured", fontSize = 11.sp, color = CyberCyan) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onLogs, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Article, contentDescription = "Logs", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onExport, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onClearData, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Clear Data", modifier = Modifier.size(18.dp))
                    }
                }

                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = SecurityRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
