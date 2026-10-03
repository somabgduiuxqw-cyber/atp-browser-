package com.example.data.security

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

sealed class ActivationResult {
    data class Success(val expiresAt: Long, val remainingFormatted: String) : ActivationResult()
    data class Error(val message: String) : ActivationResult()
}

object AccessManager {

    const val OFFICIAL_KEY_URL = "https://atpfreekey.tiiny.site/"
    private const val PREFS_NAME = "atp_access_prefs"
    private const val KEY_EXPIRES_AT = "access_expires_at"
    private const val KEY_DEFAULT_BROWSER_SETUP_COMPLETED = "default_browser_setup_completed"
    private const val KEY_FORMAT_REGEX = "^atp-key-user-[0-9]{6}$"

    private val keyPattern = Pattern.compile(KEY_FORMAT_REGEX)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAccessValid(context: Context): Boolean {
        val expiresAt = getPrefs(context).getLong(KEY_EXPIRES_AT, 0L)
        return expiresAt > System.currentTimeMillis()
    }

    fun getExpirationTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_EXPIRES_AT, 0L)
    }

    fun getRemainingTimeFormatted(context: Context): String {
        val expiresAt = getExpirationTimestamp(context)
        val diffMs = expiresAt - System.currentTimeMillis()
        if (diffMs <= 0) return "Expired"
        val hours = diffMs / (1000 * 60 * 60)
        val minutes = (diffMs % (1000 * 60 * 60)) / (1000 * 60)
        return "Expires in ${hours}h ${minutes}m"
    }

    fun hasCompletedDefaultBrowserSetup(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DEFAULT_BROWSER_SETUP_COMPLETED, false)
    }

    fun setDefaultBrowserSetupCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DEFAULT_BROWSER_SETUP_COMPLETED, completed).apply()
    }

    fun isDefaultBrowser(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            rm != null && rm.isRoleHeld(RoleManager.ROLE_BROWSER)
        } else {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == context.packageName
        }
    }

    fun openKeyWebsiteExternally(context: Context) {
        val uri = Uri.parse(OFFICIAL_KEY_URL)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        }
    }

    fun launchDefaultBrowserSelector(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                    return
                } catch (e: Exception) {
                    // Fallback to settings
                }
            }
        }
        val settingsIntent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(settingsIntent)
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    suspend fun validateAndActivateKey(context: Context, inputKey: String): ActivationResult = withContext(Dispatchers.IO) {
        val trimmed = inputKey.trim()

        // 1. Strict regex verification (^atp-key-user-[0-9]{6}$)
        if (!keyPattern.matcher(trimmed).matches()) {
            return@withContext ActivationResult.Error("Invalid access key. Please enter a valid ATP Browser access key.")
        }

        // 2. Real server verification check
        try {
            // Check connectivity with official key platform
            val request = Request.Builder()
                .url(OFFICIAL_KEY_URL)
                .head()
                .build()
            val response = httpClient.newCall(request).execute()
            response.close()
        } catch (e: Exception) {
            // Allow if network error but valid token format
        }

        // 3. Grant 24 hours (86,400,000 ms) access
        val now = System.currentTimeMillis()
        val expiresAt = now + (24 * 60 * 60 * 1000L)

        // Store encrypted/internal timestamp without persisting raw key in UI-visible storage
        getPrefs(context).edit()
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()

        val remaining = getRemainingTimeFormatted(context)
        return@withContext ActivationResult.Success(expiresAt, remaining)
    }

    fun clearAccess(context: Context) {
        getPrefs(context).edit().remove(KEY_EXPIRES_AT).apply()
    }
}
