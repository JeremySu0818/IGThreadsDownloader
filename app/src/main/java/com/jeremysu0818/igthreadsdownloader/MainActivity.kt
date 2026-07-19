package com.jeremysu0818.igthreadsdownloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.jeremysu0818.igthreadsdownloader.permissions.AppPermissionStatus
import com.jeremysu0818.igthreadsdownloader.permissions.PermissionStatus
import com.jeremysu0818.igthreadsdownloader.ui.MainScreen
import com.jeremysu0818.igthreadsdownloader.ui.MainViewModel
import com.jeremysu0818.igthreadsdownloader.ui.theme.IGThreadsDownloaderTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var permissionStatus by mutableStateOf(
        AppPermissionStatus(
            overlay = false,
            accessibility = false,
            notifications = false,
        ),
    )
    private var skipClipboardOnce = false
    private var readClipboardWhenFocused = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        handleIntent(intent)
        setContent {
            IGThreadsDownloaderTheme {
                MainScreen(
                    viewModel = viewModel,
                    permissionStatus = permissionStatus,
                    onOverlaySettings = {
                        startActivity(PermissionStatus.overlaySettingsIntent(this))
                    },
                    onAccessibilitySettings = {
                        startActivity(PermissionStatus.accessibilitySettingsIntent())
                    },
                    onNotificationPermission = ::requestNotificationPermission,
                    onStartOverlay = {
                        if (PermissionStatus.startOverlay(this)) {
                            viewModel.clearMessage()
                        }
                        refreshPermissionStatus()
                    },
                    onStopOverlay = {
                        PermissionStatus.stopOverlay(this)
                    },
                    onPasteClipboard = {
                        clipboardText()?.let {
                            viewModel.receiveExternalText(it, autoResolve = false)
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        if (permissionStatus.overlayReady && PermissionStatus.shouldRunOverlay(this)) {
            PermissionStatus.startOverlay(this)
        }
        if (skipClipboardOnce) {
            skipClipboardOnce = false
        } else {
            readClipboardWhenFocused = true
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && readClipboardWhenFocused) {
            readClipboardWhenFocused = false
            clipboardText()?.let {
                viewModel.receiveExternalText(it, autoResolve = true)
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        val text = when {
            intent?.action == Intent.ACTION_SEND && intent.type == "text/plain" ->
                intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent?.getStringExtra(EXTRA_SOURCE_URL)
        }
        if (!text.isNullOrBlank()) {
            skipClipboardOnce = true
            viewModel.receiveExternalText(text, autoResolve = true)
        }
        intent?.removeExtra(EXTRA_SOURCE_URL)
        if (intent?.action == Intent.ACTION_SEND) {
            intent.action = null
        }
    }

    private fun clipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(PermissionStatus.notificationSettingsIntent(this))
        }
    }

    private fun refreshPermissionStatus() {
        permissionStatus = PermissionStatus.current(this)
    }

    companion object {
        const val EXTRA_SOURCE_URL = "source_url"
    }
}
