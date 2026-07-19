package com.jeremysu0818.igthreadsdownloader.permissions

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.jeremysu0818.igthreadsdownloader.accessibility.MediaDetectionAccessibilityService
import com.jeremysu0818.igthreadsdownloader.overlay.OverlayService

data class AppPermissionStatus(
    val overlay: Boolean,
    val accessibility: Boolean,
    val notifications: Boolean,
) {
    val overlayReady: Boolean
        get() = overlay && accessibility
}

object PermissionStatus {
    fun current(context: Context): AppPermissionStatus = AppPermissionStatus(
        overlay = Settings.canDrawOverlays(context),
        accessibility = isAccessibilityServiceEnabled(context),
        notifications = notificationsEnabled(context),
    )

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun startOverlay(context: Context): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        context.getSharedPreferences(OVERLAY_PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_OVERLAY_ENABLED, true)
            }
        ContextCompat.startForegroundService(
            context,
            Intent(context, OverlayService::class.java),
        )
        return true
    }

    fun stopOverlay(context: Context) {
        context.getSharedPreferences(OVERLAY_PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_OVERLAY_ENABLED, false)
            }
        context.stopService(Intent(context, OverlayService::class.java))
    }

    fun shouldRunOverlay(context: Context): Boolean =
        context.getSharedPreferences(OVERLAY_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_OVERLAY_ENABLED, true)

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, MediaDetectionAccessibilityService::class.java)
            .flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val globallyEnabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0,
        ) == 1
        return globallyEnabled && enabled.split(':').any {
            it.equals(expected, ignoreCase = true)
        }
    }

    private fun notificationsEnabled(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
    }

    const val OVERLAY_PREFERENCES = "overlay_preferences"
    const val KEY_OVERLAY_ENABLED = "overlay_enabled"
}
