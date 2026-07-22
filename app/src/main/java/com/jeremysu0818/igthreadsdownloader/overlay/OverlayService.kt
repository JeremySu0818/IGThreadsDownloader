package com.jeremysu0818.igthreadsdownloader.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import coil.load
import com.jeremysu0818.igthreadsdownloader.AppGraph
import com.jeremysu0818.igthreadsdownloader.MainActivity
import com.jeremysu0818.igthreadsdownloader.R
import com.jeremysu0818.igthreadsdownloader.data.resolver.UrlNormalizer
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItem
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.permissions.PermissionStatus
import com.jeremysu0818.igthreadsdownloader.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: AccessibleBubbleView
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val selectedIds = linkedSetOf<String>()
    private var lastManifestKey: String? = null
    private var currentState = OverlayState()
    private var panelControls = false
    private var panelHint: String? = null
    private var clipboardReadJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createBubble()
        serviceScope.launch {
            AppGraph.overlayCoordinator.state.collectLatest { state ->
                currentState = state
                val manifestKey = state.manifest?.sourceUrl
                if (manifestKey != null && manifestKey != lastManifestKey) {
                    lastManifestKey = manifestKey
                    selectedIds.clear()
                    selectedIds += state.manifest.items.map { it.id }
                }
                renderBubble(state)
                if (panelView != null) renderPanel()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            closeOverlay()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        removePanel()
        if (::windowManager.isInitialized && ::bubbleView.isInitialized) {
            runCatching { windowManager.removeView(bubbleView) }
        }
        super.onDestroy()
    }

    private fun createBubble() {
        val preferences = getSharedPreferences(
            PermissionStatus.OVERLAY_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        bubbleView = AccessibleBubbleView(this).apply {
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(MatteTextPrimaryInt)
            elevation = 12f
            contentDescription = "IGThreadsDownloader 懸浮下載工具"
            setPadding(0, 0, 0, dp(2))
        }
        bubbleParams = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt(KEY_X, resources.displayMetrics.widthPixels - dp(68))
            y = preferences.getInt(KEY_Y, resources.displayMetrics.heightPixels / 3)
        }
        attachBubbleGestures()
        runCatching { windowManager.addView(bubbleView, bubbleParams) }
            .onFailure { stopSelf() }
    }

    private fun attachBubbleGestures() {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        var longPressed = false
        var longPressJob: Job? = null

        bubbleView.setOnClickListener { beginBubbleAction() }
        bubbleView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    moved = false
                    longPressed = false
                    longPressJob?.cancel()
                    longPressJob = serviceScope.launch {
                        kotlinx.coroutines.delay(ViewConfiguration.getLongPressTimeout().toLong())
                        if (!moved) {
                            longPressed = true
                            panelControls = true
                            panelHint = null
                            showPanel()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        moved = true
                        longPressJob?.cancel()
                    }
                    if (moved) {
                        bubbleParams.x = (startX + dx).roundToInt().coerceIn(
                            0,
                            resources.displayMetrics.widthPixels - dp(56),
                        )
                        bubbleParams.y = (startY + dy).roundToInt().coerceIn(
                            dp(24),
                            resources.displayMetrics.heightPixels - dp(80),
                        )
                        runCatching {
                            windowManager.updateViewLayout(bubbleView, bubbleParams)
                            updatePanelPosition()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressJob?.cancel()
                    if (event.actionMasked == MotionEvent.ACTION_UP && !moved && !longPressed) {
                        view.performClick()
                    } else if (moved) {
                        snapBubbleToEdge()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun beginBubbleAction() {
        clipboardReadJob?.cancel()
        setBubbleFocusable(true)
        clipboardReadJob = serviceScope.launch {
            // Android 10+ only exposes clipboard data to the focused app. Focus is
            // requested only after this explicit tap and released immediately after.
            kotlinx.coroutines.delay(CLIPBOARD_FOCUS_DELAY_MS)
            try {
                handleBubbleClick()
            } finally {
                setBubbleFocusable(false)
            }
        }
    }

    private fun setBubbleFocusable(focusable: Boolean) {
        if (!::windowManager.isInitialized || !::bubbleParams.isInitialized) return
        bubbleParams.flags = if (focusable) {
            bubbleParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            bubbleParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
    }

    private fun handleBubbleClick() {
        panelControls = false
        panelHint = null
        when (currentState.phase) {
            OverlayPhase.RESOLVING, OverlayPhase.DOWNLOADING -> showPanel()
            else -> {
                val clipboardUrl = readClipboardUrl()
                when {
                    clipboardUrl == null -> {
                        panelHint =
                            "剪貼簿沒有可支援的 IG / Threads 公開連結。請先複製貼文 URL，再點一次懸浮球。"
                        showPanel()
                    }
                    clipboardUrl == currentState.detectedUrl &&
                        currentState.manifest != null -> showPanel()
                    else -> {
                        showPanel()
                        AppGraph.overlayCoordinator.resolve(clipboardUrl)
                    }
                }
            }
        }
    }

    private fun showPanel() {
        if (panelView == null) {
            panelParams = WindowManager.LayoutParams(
                dp(336),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            panelView = FrameLayout(this)
            updatePanelPosition()
            runCatching { windowManager.addView(panelView, panelParams) }
                .onFailure {
                    panelView = null
                    panelParams = null
                }
        }
        renderPanel()
    }

    private fun renderPanel() {
        val frame = panelView as? FrameLayout ?: return
        frame.removeAllViews()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedDrawable(COLOR_PANEL, dp(22).toFloat())
            elevation = 16f
        }
        frame.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )
        content.addView(buildHeader())

        if (panelControls) {
            renderControlMenu(content)
            return
        }
        panelHint?.let {
            content.addView(bodyText(it, COLOR_MUTED).withTopMargin(12))
            content.addView(primaryButton("在 App 貼上連結") { openInApp(null) }.withTopMargin(14))
            return
        }

        when (currentState.phase) {
            OverlayPhase.IDLE -> {
                content.addView(
                    bodyText(
                        "先複製 Instagram / Threads 公開連結，再點懸浮球開始解析。",
                        COLOR_MUTED,
                    ).withTopMargin(12),
                )
            }
            OverlayPhase.RESOLVING -> {
                content.addView(bodyText("正在讀取公開頁面與媒體資訊…", COLOR_ACCENT).withTopMargin(12))
            }
            OverlayPhase.READY, OverlayPhase.DOWNLOADING -> renderManifest(content)
            OverlayPhase.ERROR -> {
                content.addView(
                    bodyText(
                        currentState.errorMessage ?: "發生未知錯誤。",
                        COLOR_ERROR,
                    ).withTopMargin(12),
                )
                currentState.detectedUrl?.let {
                    content.addView(
                        primaryButton("重試解析") {
                            AppGraph.overlayCoordinator.resolve(it)
                        }.withTopMargin(14),
                    )
                }
                if (currentState.manifest != null) {
                    content.addView(
                        secondaryButton("回到預覽") {
                            AppGraph.overlayCoordinator.clearError()
                        }.withTopMargin(8),
                    )
                }
            }
        }
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            TextView(this).apply {
                text = getString(R.string.overlay_brand)
                textSize = 13f
                letterSpacing = 0.12f
                setTextColor(MatteTextPrimaryInt)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(iconButton("—", "縮小") { removePanel() })
        row.addView(iconButton("×", "關閉懸浮工具") { closeOverlay() })
        return row
    }

    private fun renderControlMenu(content: LinearLayout) {
        content.addView(
            bodyText(
                "懸浮工具控制",
                COLOR_MUTED,
            ).withTopMargin(12),
        )
        content.addView(
            secondaryButton("移回右側預設位置") {
                moveToDefaultPosition()
                removePanel()
            }.withTopMargin(14),
        )
        content.addView(
            dangerButton("關閉懸浮工具") { closeOverlay() }.withTopMargin(8),
        )
    }

    private fun renderManifest(content: LinearLayout) {
        val manifest = currentState.manifest ?: return
        val source = manifest.author?.let { "@$it" } ?: manifest.platform.value
        content.addView(
            bodyText(
                "${manifest.platform.value.uppercase()}  ·  $source  ·  ${manifest.type.value}",
                COLOR_MUTED,
            ).withTopMargin(10),
        )
        manifest.thumbnailUrl?.let { thumbnailUrl ->
            content.addView(
                ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = roundedDrawable(COLOR_PANEL_ALT, dp(12).toFloat())
                    clipToOutline = true
                    load(thumbnailUrl) {
                        crossfade(true)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(118),
                ).apply { topMargin = dp(10) },
            )
        }
        content.addView(
            bodyText(
                "${manifest.items.size} 個項目  ·  原始畫質  ·  ${estimatedSize(manifest.items)}",
                MatteTextPrimaryInt,
            ).withTopMargin(10),
        )
        if (manifest.warnings.isNotEmpty()) {
            content.addView(
                bodyText(manifest.warnings.joinToString("\n"), COLOR_WARNING).withTopMargin(8),
            )
        }

        val itemList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        manifest.items.forEachIndexed { index, item ->
            itemList.addView(mediaCheckbox(item, index))
        }
        content.addView(
            ScrollView(this).apply {
                isVerticalScrollBarEnabled = false
                addView(itemList)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(154).coerceAtMost(dp(48) * manifest.items.size + dp(8)),
            ).apply { topMargin = dp(8) },
        )
        val downloadLabel = if (currentState.phase == OverlayPhase.DOWNLOADING) {
            "下載中…"
        } else {
            "下載已選項目"
        }
        content.addView(
            primaryButton(
                text = downloadLabel,
                enabled = currentState.phase != OverlayPhase.DOWNLOADING,
            ) {
                AppGraph.overlayCoordinator.download(selectedIds)
            }.withTopMargin(12),
        )
        content.addView(
            secondaryButton("在 App 開啟") {
                openInApp(manifest.sourceUrl)
            }.withTopMargin(8),
        )
    }

    private fun mediaCheckbox(item: MediaItem, index: Int): View =
        CheckBox(this).apply {
            val kind = if (item.type == MediaItemType.VIDEO) {
                getString(R.string.media_kind_video)
            } else {
                getString(R.string.media_kind_image)
            }
            text = getString(
                R.string.overlay_media_item,
                index + 1,
                kind,
                formatBytes(item.contentLength),
            )
            textSize = 13f
            setTextColor(MatteTextPrimaryInt)
            buttonTintList = ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(),
                ),
                intArrayOf(COLOR_ACCENT, COLOR_MUTED),
            )
            isChecked = item.id in selectedIds
            setOnCheckedChangeListener { _, checked ->
                if (checked) selectedIds += item.id else selectedIds -= item.id
            }
        }

    private fun renderBubble(state: OverlayState) {
        if (!::bubbleView.isInitialized) return
        val (label, color) = when {
            state.phase == OverlayPhase.IDLE -> "↓" to COLOR_IDLE
            state.phase == OverlayPhase.RESOLVING -> "…" to COLOR_ACCENT
            state.phase == OverlayPhase.READY -> "✓" to COLOR_READY
            state.phase == OverlayPhase.DOWNLOADING -> "⇩" to COLOR_ACCENT
            else -> "!" to COLOR_ERROR
        }
        bubbleView.text = label
        bubbleView.background = roundedDrawable(color, dp(28).toFloat())
        bubbleView.alpha = if (state.phase == OverlayPhase.IDLE) 0.9f else 1f
    }

    private fun snapBubbleToEdge() {
        val width = resources.displayMetrics.widthPixels
        bubbleParams.x = if (bubbleParams.x + dp(28) < width / 2) {
            dp(8)
        } else {
            width - dp(64)
        }
        runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
        saveBubblePosition()
        updatePanelPosition()
    }

    private fun moveToDefaultPosition() {
        bubbleParams.x = resources.displayMetrics.widthPixels - dp(64)
        bubbleParams.y = resources.displayMetrics.heightPixels / 3
        runCatching { windowManager.updateViewLayout(bubbleView, bubbleParams) }
        saveBubblePosition()
        updatePanelPosition()
    }

    private fun saveBubblePosition() {
        getSharedPreferences(PermissionStatus.OVERLAY_PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putInt(KEY_X, bubbleParams.x)
                putInt(KEY_Y, bubbleParams.y)
            }
    }

    private fun updatePanelPosition() {
        val params = panelParams ?: return
        val panelWidth = dp(336)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        params.x = if (bubbleParams.x > screenWidth / 2) {
            (bubbleParams.x - panelWidth - dp(8)).coerceAtLeast(dp(8))
        } else {
            (bubbleParams.x + dp(64)).coerceAtMost(screenWidth - panelWidth - dp(8))
        }
        val maxY = (screenHeight - dp(240)).coerceAtLeast(dp(24))
        params.y = bubbleParams.y.coerceIn(dp(24), maxY)
        panelView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun removePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelParams = null
        panelControls = false
        panelHint = null
    }

    private fun closeOverlay() {
        getSharedPreferences(PermissionStatus.OVERLAY_PREFERENCES, Context.MODE_PRIVATE)
            .edit {
                putBoolean(PermissionStatus.KEY_OVERLAY_ENABLED, false)
            }
        stopSelf()
    }

    private fun readClipboardUrl(): String? {
        return runCatching {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = clipboard.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(this)
                ?.toString()
                .orEmpty()
            UrlNormalizer.extractSupportedUrl(text)
        }.getOrNull()
    }

    private fun openInApp(sourceUrl: String?) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_SOURCE_URL, sourceUrl),
        )
        removePanel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "IGThreadsDownloader 懸浮工具",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "顯示可拖曳的媒體解析工具"
                setShowBadge(false)
            },
        )
    }

    private fun startAsForeground() {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_download)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text))
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "關閉", stopIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun bodyText(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(color)
        setLineSpacing(0f, 1.15f)
    }

    private fun primaryButton(
        text: String,
        enabled: Boolean = true,
        action: () -> Unit,
    ): TextView = actionButton(text, if (enabled) COLOR_ACCENT else COLOR_IDLE, MatteTextPrimaryInt) {
        if (enabled) action()
    }.apply { isEnabled = enabled }

    private fun secondaryButton(text: String, action: () -> Unit): TextView =
        actionButton(text, COLOR_PANEL_ALT, MatteTextPrimaryInt, action)

    private fun dangerButton(text: String, action: () -> Unit): TextView =
        actionButton(text, COLOR_ERROR_DARK, COLOR_ERROR_LIGHT, action)

    private fun actionButton(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        action: () -> Unit,
    ): TextView = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = 14f
        setTextColor(textColor)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = roundedDrawable(backgroundColor, dp(12).toFloat())
        setOnClickListener { action() }
    }

    private fun iconButton(text: String, description: String, action: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            contentDescription = description
            gravity = Gravity.CENTER
            textSize = 22f
            setTextColor(COLOR_MUTED)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }

    private fun View.withTopMargin(margin: Int): View = apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(margin) }
    }

    private fun roundedDrawable(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }

    private fun estimatedSize(items: List<MediaItem>): String {
        val sizes = items.map { it.contentLength }
        return if (sizes.all { it != null }) {
            formatBytes(sizes.filterNotNull().sum())
        } else {
            "大小未知"
        }
    }

    private fun formatBytes(bytes: Long?): String {
        bytes ?: return "大小未知"
        return when {
            bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1_024 -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
            else -> "$bytes B"
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "overlay_service"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP =
            "com.jeremysu0818.igthreadsdownloader.action.STOP_OVERLAY"
        private const val KEY_X = "bubble_x"
        private const val KEY_Y = "bubble_y"
        private const val CLIPBOARD_FOCUS_DELAY_MS = 80L

        private val COLOR_PANEL = MatteCardInt
        private val COLOR_PANEL_ALT = MatteCardHoverInt
        private val COLOR_IDLE = MatteCardBorderInt
        private val COLOR_ACCENT = MattePrimaryInt
        private val COLOR_READY = MatteEmeraldInt
        private val COLOR_ERROR = MatteRoseInt
        private val COLOR_ERROR_DARK = MatteRoseDarkInt
        private val COLOR_ERROR_LIGHT = MatteRoseLightInt
        private val COLOR_WARNING = MatteAmberInt
        private val COLOR_MUTED = MatteTextSecondaryInt
    }
}

private class AccessibleBubbleView(context: Context) : TextView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
