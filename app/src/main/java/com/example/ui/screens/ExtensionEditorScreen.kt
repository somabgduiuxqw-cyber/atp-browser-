package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.extensions.ExtensionValidator
import com.example.ui.BrowserViewModel
import com.example.ui.ScreenState
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionEditorScreen(
    viewModel: BrowserViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val extensionId by viewModel.editingExtensionId.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: config.js, 1: script.js, 2: styles.css

    var configContent by remember { mutableStateOf("") }
    var scriptContent by remember { mutableStateOf("") }
    var stylesContent by remember { mutableStateOf("") }

    var validationError by remember { mutableStateOf<String?>(null) }
    var validationSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(extensionId) {
        val id = extensionId
        if (id != null) {
            val files = viewModel.extensionManager.getExtensionFiles(id)
            if (files != null) {
                configContent = files.first
                scriptContent = files.second
                stylesContent = files.third
            }
        } else {
            // Default template for new extension
            configContent = """
export default {
    name: "Custom Theme & Script",
    version: "1.0.0",
    description: "Custom user script and stylesheet for matching websites",
    author: "User",

    matches: [
        "https://example.com/*",
        "*://*.wikipedia.org/*"
    ],

    permissions: [
        "storage",
        "page-script",
        "page-style"
    ],

    script: "script.js",
    styles: "styles.css"
};
            """.trimIndent()

            scriptContent = """
// ATP Extension custom script
console.log("ATP Extension running on: " + window.location.href);

document.addEventListener("DOMContentLoaded", function() {
    if (window.ATPBrowser) {
        window.ATPBrowser.log("DOM ready");
    }
});
            """.trimIndent()

            stylesContent = """
/* ATP Extension custom styles */
body {
    /* Custom font or dark accent */
}
            """.trimIndent()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (extensionId != null) "Edit Extension" else "New Extension", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Validate button
                    IconButton(onClick = {
                        val result = ExtensionValidator.parseConfigJs(configContent, extensionId ?: "new_ext")
                        if (result.isValid) {
                            validationError = null
                            validationSuccess = true
                            Toast.makeText(context, "Manifest valid!", Toast.LENGTH_SHORT).show()
                        } else {
                            validationError = result.errorMessage
                            validationSuccess = false
                        }
                    }) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Validate", tint = CyberCyan)
                    }

                    // Save button
                    Button(
                        onClick = {
                            val id = extensionId ?: "ext_${System.currentTimeMillis()}"
                            val validation = ExtensionValidator.parseConfigJs(configContent, id)
                            if (!validation.isValid) {
                                validationError = validation.errorMessage
                                return@Button
                            }

                            if (extensionId == null) {
                                val ext = validation.extension!!
                                viewModel.extensionManager.createExtension(
                                    name = ext.name,
                                    version = ext.version,
                                    description = ext.description,
                                    author = ext.author,
                                    matches = ext.matches,
                                    permissions = ext.permissions,
                                    proxyConfig = ext.proxyConfig
                                )
                                Toast.makeText(context, "Extension created!", Toast.LENGTH_SHORT).show()
                            } else {
                                val ok = viewModel.extensionManager.updateExtensionFiles(
                                    id = extensionId!!,
                                    configJs = configContent,
                                    scriptJs = scriptContent,
                                    stylesCss = stylesContent
                                )
                                if (ok) {
                                    Toast.makeText(context, "Extension saved!", Toast.LENGTH_SHORT).show()
                                }
                            }
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
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
            // Editor tabs: config.js, script.js, styles.css
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("config.js") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("script.js") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("styles.css") }
                )
            }

            // Validation status alert
            if (validationError != null) {
                Surface(
                    color = SecurityRed.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = SecurityRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = validationError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecurityRed
                        )
                    }
                }
            } else if (validationSuccess) {
                Surface(
                    color = SecurityGreen.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Validation successful. Configuration complies with ATP Extension standard.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecurityGreen
                        )
                    }
                }
            }

            // Monospace Code Editor
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        OutlinedTextField(
                            value = configContent,
                            onValueChange = {
                                configContent = it
                                validationError = null
                                validationSuccess = false
                            },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE2E8F0)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    1 -> {
                        OutlinedTextField(
                            value = scriptContent,
                            onValueChange = { scriptContent = it },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE2E8F0)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    2 -> {
                        OutlinedTextField(
                            value = stylesContent,
                            onValueChange = { stylesContent = it },
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = Color(0xFFE2E8F0)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
