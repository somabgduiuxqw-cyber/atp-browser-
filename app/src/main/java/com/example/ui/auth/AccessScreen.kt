package com.example.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.security.AccessManager
import com.example.data.security.ActivationResult
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.SecurityGreen
import com.example.ui.theme.SecurityRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AccessUiState {
    DEFAULT_BROWSER_SETUP,
    ACCESS_REQUIRED,
    CHECKING,
    INVALID,
    ACTIVE_SUCCESS,
    EXPIRED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessScreen(
    initialState: AccessUiState = AccessUiState.ACCESS_REQUIRED,
    onAccessGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentState by remember { mutableStateOf(initialState) }
    var keyInput by remember { mutableStateOf("") }
    var remainingTimeText by remember { mutableStateOf(AccessManager.getRemainingTimeFormatted(context)) }
    var errorMessage by remember { mutableStateOf("") }

    BackHandler(enabled = currentState == AccessUiState.ACTIVE_SUCCESS) {
        onAccessGranted()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (currentState) {
                    AccessUiState.DEFAULT_BROWSER_SETUP -> {
                        // Section 9 & 16: Welcome to ATP Browser, Set default browser
                        Surface(
                            shape = CircleShape,
                            color = CyberCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Welcome to ATP Browser",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Set ATP Browser as your default browser?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Use ATP Browser whenever you open web links.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = {
                                AccessManager.setDefaultBrowserSetupCompleted(context, true)
                                AccessManager.launchDefaultBrowserSelector(context)
                                // Move to access verification
                                if (AccessManager.isAccessValid(context)) {
                                    onAccessGranted()
                                } else {
                                    currentState = AccessUiState.ACCESS_REQUIRED
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("set_as_default_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Set as Default Browser", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                AccessManager.setDefaultBrowserSetupCompleted(context, true)
                                if (AccessManager.isAccessValid(context)) {
                                    onAccessGranted()
                                } else {
                                    currentState = AccessUiState.ACCESS_REQUIRED
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("not_now_default_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Not Now")
                        }
                    }

                    AccessUiState.ACCESS_REQUIRED -> {
                        // Section 3 & 4: Access Required
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "ATP Browser",
                            style = MaterialTheme.typography.labelLarge.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = CyberCyan
                        )
                        Text(
                            text = "Access Required",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your access session is not active.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Button 1: [ Get Access Key ]
                        OutlinedButton(
                            onClick = {
                                AccessManager.openKeyWebsiteExternally(context)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("get_access_key_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Get Access Key", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // Section 4: Key Input
                        Text(
                            text = "Enter Access Key",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it },
                            placeholder = { Text("Enter your access key") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("access_key_input"),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (keyInput.isNotBlank()) {
                                    currentState = AccessUiState.CHECKING
                                    scope.launch {
                                        delay(600) // Realistic check delay
                                        val result = AccessManager.validateAndActivateKey(context, keyInput)
                                        keyInput = "" // Instantly clear raw input from memory!
                                        when (result) {
                                            is ActivationResult.Success -> {
                                                remainingTimeText = result.remainingFormatted
                                                currentState = AccessUiState.ACTIVE_SUCCESS
                                            }
                                            is ActivationResult.Error -> {
                                                errorMessage = result.message
                                                currentState = AccessUiState.INVALID
                                            }
                                        }
                                    }
                                }
                            })
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Button 2: [ Verify & Activate ]
                        Button(
                            onClick = {
                                if (keyInput.isNotBlank()) {
                                    currentState = AccessUiState.CHECKING
                                    scope.launch {
                                        delay(600)
                                        val result = AccessManager.validateAndActivateKey(context, keyInput)
                                        keyInput = "" // Clear immediately
                                        when (result) {
                                            is ActivationResult.Success -> {
                                                remainingTimeText = result.remainingFormatted
                                                currentState = AccessUiState.ACTIVE_SUCCESS
                                            }
                                            is ActivationResult.Error -> {
                                                errorMessage = result.message
                                                currentState = AccessUiState.INVALID
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("verify_activate_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Verify & Activate", fontWeight = FontWeight.Bold)
                        }
                    }

                    AccessUiState.CHECKING -> {
                        // Section 16: Checking access...
                        CircularProgressIndicator(
                            color = CyberCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Checking access...",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Validating access credentials with key authority...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AccessUiState.INVALID -> {
                        // Section 16: Invalid Access Key
                        Surface(
                            shape = CircleShape,
                            color = SecurityRed.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = SecurityRed,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Invalid Access Key",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = SecurityRed
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "The access key could not be verified.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage.ifBlank { "Please enter a valid ATP Browser access key." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { currentState = AccessUiState.ACCESS_REQUIRED },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("try_again_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Try Again")
                        }
                    }

                    AccessUiState.ACTIVE_SUCCESS -> {
                        // Section 6 & 16: ✓ Access Activated, 1-Day Access Enabled
                        // (The actual key is NEVER displayed)
                        Surface(
                            shape = CircleShape,
                            color = SecurityGreen.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SecurityGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "✓ Access Activated",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = SecurityGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1-Day Access Enabled",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = remainingTimeText.ifBlank { "Expires in 24h 00m" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                        Button(
                            onClick = onAccessGranted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("continue_to_browser_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Open ATP Browser", fontWeight = FontWeight.Bold)
                        }
                    }

                    AccessUiState.EXPIRED -> {
                        // Section 7 & 16: Access Expired
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.TimerOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Access Expired",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your access period has ended.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                AccessManager.openKeyWebsiteExternally(context)
                                currentState = AccessUiState.ACCESS_REQUIRED
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("get_new_key_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Get New Access Key")
                        }
                    }
                }
            }
        }
    }
}
