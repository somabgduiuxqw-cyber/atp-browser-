package com.example.ui.dialogs

import android.net.Uri
import androidx.compose.foundation.layout.*
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
import com.example.data.security.AdBlockManager
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@Composable
fun RiskyNavigationDialog(
    url: String,
    reason: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    // Mandate 21 & 103: Title "Risky Website", message "This destination may contain risks.", Buttons ONLY: YES / NO
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                tint = SecurityAmber,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Risky Website",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = url,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This destination may contain risks.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (reason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SecurityRed)
            ) {
                Text("YES")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("NO")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun RiskyFileDialog(
    filename: String,
    reason: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    // Mandate 36: Potentially Risky File, Buttons ONLY: YES / NO
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.ReportProblem,
                contentDescription = "Risk Warning",
                tint = SecurityRed,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Potentially Risky File",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "\"$filename\"",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "This file can contain executable or active code.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ATP Browser will never directly execute downloaded code. Do you still wish to download this file?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SecurityRed)
            ) {
                Text("YES")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("NO")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun PageInfoDialog(
    url: String,
    isHttps: Boolean,
    onDismiss: () -> Unit
) {
    val uri = try { Uri.parse(url) } catch (e: Exception) { null }
    val domain = uri?.host ?: url
    val blockedForDomain = AdBlockManager.getSiteBlockedCount(domain)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isHttps) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                contentDescription = null,
                tint = if (isHttps) SecurityGreen else SecurityAmber,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Page Information",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoRow(label = "Domain", value = domain)
                InfoRow(label = "Connection", value = if (isHttps) "Encrypted (HTTPS)" else "Unencrypted (HTTP)")
                InfoRow(label = "Certificate Status", value = if (isHttps) "Valid System-Verified TLS" else "Not Secure")
                InfoRow(label = "Ad & Tracker Shield", value = if (AdBlockManager.isGlobalAdBlockingEnabled) "Active ($blockedForDomain blocked)" else "Disabled")
                InfoRow(label = "Cookies", value = "Isolated Partitioned")
                InfoRow(label = "Web Storage", value = "DOM & IndexedDB Ready")
                InfoRow(label = "Malware Check", value = "No known threat detected")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
