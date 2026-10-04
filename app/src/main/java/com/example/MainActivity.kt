package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.security.AccessManager
import com.example.ui.BrowserViewModel
import com.example.ui.ScreenState
import com.example.ui.auth.AccessScreen
import com.example.ui.auth.AccessUiState
import com.example.ui.browser.BrowserScreen
import com.example.ui.browser.TabsTray
import com.example.ui.dialogs.RiskyFileDialog
import com.example.ui.dialogs.RiskyNavigationDialog
import com.example.ui.screens.*
import com.example.ui.theme.AtpBrowserTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleWebIntent(intent)

        setContent {
            AtpBrowserTheme {
                val context = LocalContext.current
                var isAccessActive by remember {
                    mutableStateOf(AccessManager.isAccessValid(context))
                }
                var isDefaultBrowserSetupDone by remember {
                    mutableStateOf(AccessManager.hasCompletedDefaultBrowserSetup(context))
                }

                val currentScreen by viewModel.currentScreen.collectAsState()
                val pendingRiskyPrompt by viewModel.pendingRiskyPrompt.collectAsState()
                val pendingRiskyFilePrompt by viewModel.pendingRiskyFilePrompt.collectAsState()

                // File Chooser Launcher for <input type="file">
                val filePickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val clipData = result.data?.clipData
                        val uri = result.data?.data
                        val uris = when {
                            clipData != null -> Array(clipData.itemCount) { clipData.getItemAt(it).uri }
                            uri != null -> arrayOf(uri)
                            else -> null
                        }
                        fileUploadCallback?.onReceiveValue(uris)
                    } else {
                        fileUploadCallback?.onReceiveValue(null)
                    }
                    fileUploadCallback = null
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isDefaultBrowserSetupDone) {
                            // Step 2 & 9: First Launch Default Browser Selection Flow
                            AccessScreen(
                                initialState = AccessUiState.DEFAULT_BROWSER_SETUP,
                                onAccessGranted = {
                                    isDefaultBrowserSetupDone = true
                                    isAccessActive = AccessManager.isAccessValid(context)
                                }
                            )
                        } else if (!isAccessActive) {
                            // Step 4: Access Verification (Access Required / Expired)
                            val isExpired = AccessManager.getExpirationTimestamp(context) > 0
                            AccessScreen(
                                initialState = if (isExpired) AccessUiState.EXPIRED else AccessUiState.ACCESS_REQUIRED,
                                onAccessGranted = {
                                    isAccessActive = true
                                }
                            )
                        } else {
                            // Main Browser Navigation Router
                            AnimatedContent(
                                targetState = currentScreen,
                                label = "ScreenTransition"
                            ) { screen ->
                                when (screen) {
                                    ScreenState.BROWSER -> {
                                        BrowserScreen(
                                            viewModel = viewModel,
                                            onFileChooser = { callback, params ->
                                                fileUploadCallback?.onReceiveValue(null)
                                                fileUploadCallback = callback
                                                val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                                    type = "*/*"
                                                    addCategory(Intent.CATEGORY_OPENABLE)
                                                }
                                                try {
                                                    filePickerLauncher.launch(intent)
                                                } catch (e: Exception) {
                                                    fileUploadCallback?.onReceiveValue(null)
                                                    fileUploadCallback = null
                                                }
                                            }
                                        )
                                    }
                                    ScreenState.TABS_TRAY -> {
                                        TabsTray(
                                            viewModel = viewModel,
                                            onCloseTray = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.BACKGROUND_SITES -> {
                                        BackgroundSitesScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.SECURITY_CENTER -> {
                                        SecurityCenterScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.DNS_SETTINGS -> {
                                        DnsSettingsScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.DOWNLOADS -> {
                                        DownloadsScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.HISTORY_BOOKMARKS -> {
                                        HistoryBookmarksScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() },
                                            onNavigate = { url -> viewModel.onUrlSubmitted(url) }
                                        )
                                    }
                                    ScreenState.SETTINGS -> {
                                        SettingsScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.DEVELOPER_MODE -> {
                                        DeveloperModeScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.ACCESS_KEY -> {
                                        AccessScreen(
                                            initialState = AccessUiState.ACTIVE_SUCCESS,
                                            onAccessGranted = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.EXTENSIONS -> {
                                        ExtensionManagerScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.EXTENSION_EDITOR -> {
                                        ExtensionEditorScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateToScreen(ScreenState.EXTENSIONS) }
                                        )
                                    }
                                    ScreenState.PROXY_SETTINGS -> {
                                        ProxySettingsScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.PRIVACY_DASHBOARD -> {
                                        PrivacyDashboardScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                    ScreenState.SITE_PERMISSIONS -> {
                                        SitePermissionsScreen(
                                            viewModel = viewModel,
                                            onBack = { viewModel.navigateBackToBrowser() }
                                        )
                                    }
                                }
                            }
                        }

                        // Risky Website Navigation Dialog (YES / NO)
                        pendingRiskyPrompt?.let { prompt ->
                            RiskyNavigationDialog(
                                url = prompt.url,
                                reason = prompt.reason,
                                onConfirm = prompt.onConfirm,
                                onCancel = prompt.onCancel
                            )
                        }

                        // Risky Download File Dialog (YES / NO)
                        pendingRiskyFilePrompt?.let { prompt ->
                            RiskyFileDialog(
                                filename = prompt.filename,
                                reason = prompt.reason,
                                onConfirm = prompt.onConfirm,
                                onCancel = prompt.onCancel
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            android.webkit.CookieManager.getInstance().flush()
        } catch (e: Exception) {
            // Protect against webview uninitialized
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWebIntent(intent)
    }

    private fun handleWebIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.dataString
        if (Intent.ACTION_VIEW == action && !data.isNullOrBlank()) {
            viewModel.createNewTab(data, incognito = false)
        }
    }
}
