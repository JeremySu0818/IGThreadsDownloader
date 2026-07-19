package com.jeremysu0818.igthreadsdownloader.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.jeremysu0818.igthreadsdownloader.AppGraph
import com.jeremysu0818.igthreadsdownloader.data.resolver.UrlNormalizer
import com.jeremysu0818.igthreadsdownloader.overlay.OverlayService
import com.jeremysu0818.igthreadsdownloader.permissions.PermissionStatus

class MediaDetectionAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var lastScanAt = 0L
    private var lastDetectedUrl: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (Settings.canDrawOverlays(this) && PermissionStatus.shouldRunOverlay(this)) {
            ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString().orEmpty()
        if (packageName !in TARGET_PACKAGES) {
            AppGraph.overlayCoordinator.onTargetAppForeground(false)
            return
        }
        AppGraph.overlayCoordinator.onTargetAppForeground(true)

        val now = System.currentTimeMillis()
        if (now - lastScanAt >= SCAN_THROTTLE_MS) {
            lastScanAt = now
            findSupportedUrl(event.source ?: rootInActiveWindow)?.let(::publishUrl)
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            handler.removeCallbacks(readClipboardRunnable)
            handler.postDelayed(readClipboardRunnable, 350)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private val readClipboardRunnable = Runnable {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            .orEmpty()
        UrlNormalizer.extractSupportedUrl(text)?.let(::publishUrl)
    }

    private fun findSupportedUrl(root: AccessibilityNodeInfo?): String? {
        root ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1
            val values = sequenceOf(
                node.text?.toString(),
                node.contentDescription?.toString(),
                node.viewIdResourceName,
            ).filterNotNull()
            values.forEach { value ->
                UrlNormalizer.extractSupportedUrl(value)?.let { return it }
            }
            repeat(node.childCount) { index ->
                node.getChild(index)?.let(queue::add)
            }
        }
        return null
    }

    private fun publishUrl(url: String) {
        if (url == lastDetectedUrl) return
        lastDetectedUrl = url
        AppGraph.overlayCoordinator.onDetectedUrl(url)
    }

    companion object {
        private const val SCAN_THROTTLE_MS = 600L
        private const val MAX_NODES = 350
        private val TARGET_PACKAGES = setOf(
            "com.instagram.android",
            "com.instagram.barcelona",
        )
    }
}
