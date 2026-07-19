package com.jeremysu0818.igthreadsdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadRecord
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadStatus
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItem
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaManifest
import com.jeremysu0818.igthreadsdownloader.permissions.AppPermissionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink = Color(0xFF0D0F13)
private val Surface = Color(0xFF16191F)
private val SurfaceRaised = Color(0xFF20242C)
private val Line = Color(0xFF2B303A)
private val Primary = Color(0xFF6676FF)
private val PrimarySoft = Color(0xFF252B54)
private val Success = Color(0xFF32C784)
private val Warning = Color(0xFFFFC966)
private val Error = Color(0xFFFF7279)
private val TextPrimary = Color(0xFFF5F6F8)
private val TextSecondary = Color(0xFFA9B0BD)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    permissionStatus: AppPermissionStatus,
    onOverlaySettings: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onPasteClipboard: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        AppHeader()
        TabStrip(
            selected = state.tab,
            activeCount = state.records.count { it.isActive },
            historyCount = state.records.count { !it.isActive },
            onSelect = viewModel::selectTab,
        )
        when (state.tab) {
            MainTab.DOWNLOAD -> DownloadPage(
                state = state,
                permissionStatus = permissionStatus,
                onInputChange = viewModel::updateInput,
                onParse = viewModel::parse,
                onPasteClipboard = onPasteClipboard,
                onOverlaySettings = onOverlaySettings,
                onAccessibilitySettings = onAccessibilitySettings,
                onNotificationPermission = onNotificationPermission,
                onStartOverlay = onStartOverlay,
                onStopOverlay = onStopOverlay,
                onToggleItem = viewModel::toggleSelection,
                onSelectAll = viewModel::selectAll,
                onDownload = viewModel::downloadSelected,
            )
            MainTab.QUEUE -> RecordsPage(
                records = state.records.filter { it.isActive },
                emptyTitle = "目前沒有下載工作",
                emptyBody = "解析媒體並點擊下載後，系統真實進度會顯示在這裡。",
                onCancel = viewModel::cancel,
                onRetry = viewModel::retry,
                onOpen = viewModel::open,
                onShare = viewModel::share,
                onDelete = viewModel::delete,
            )
            MainTab.HISTORY -> RecordsPage(
                records = state.records.filter { !it.isActive },
                emptyTitle = "尚無歷史紀錄",
                emptyBody = "完成、失敗與取消的下載會保留在裝置上。",
                onCancel = viewModel::cancel,
                onRetry = viewModel::retry,
                onOpen = viewModel::open,
                onShare = viewModel::share,
                onDelete = viewModel::delete,
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Primary),
            contentAlignment = Alignment.Center,
        ) {
            Text("↓", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "IGTHREADSDOWNLOADER",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
            Text("IG + Threads 公開媒體", color = TextSecondary, fontSize = 11.sp)
        }
        StatusPill("LOCAL", Success)
    }
}

@Composable
private fun TabStrip(
    selected: MainTab,
    activeCount: Int,
    historyCount: Int,
    onSelect: (MainTab) -> Unit,
) {
    val tabs = listOf(
        MainTab.DOWNLOAD to "下載",
        MainTab.QUEUE to "佇列 $activeCount",
        MainTab.HISTORY to "歷史 $historyCount",
    )
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface),
    ) {
        tabs.forEach { (tab, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected == tab) SurfaceRaised else Color.Transparent)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected == tab) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = if (selected == tab) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun DownloadPage(
    state: MainUiState,
    permissionStatus: AppPermissionStatus,
    onInputChange: (String) -> Unit,
    onParse: () -> Unit,
    onPasteClipboard: () -> Unit,
    onOverlaySettings: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            IntroBlock()
        }
        item {
            PermissionPanel(
                status = permissionStatus,
                onOverlaySettings = onOverlaySettings,
                onAccessibilitySettings = onAccessibilitySettings,
                onNotificationPermission = onNotificationPermission,
                onStartOverlay = onStartOverlay,
                onStopOverlay = onStopOverlay,
            )
        }
        item {
            ResolverInput(
                value = state.input,
                isResolving = state.isResolving,
                onValueChange = onInputChange,
                onPaste = onPasteClipboard,
                onParse = onParse,
            )
        }
        state.errorMessage?.let { message ->
            item { MessageBanner(message, Error) }
        }
        state.noticeMessage?.let { message ->
            item { MessageBanner(message, Success) }
        }
        state.manifest?.let { manifest ->
            item {
                ManifestPreview(
                    manifest = manifest,
                    selectedIds = state.selectedIds,
                    onToggleItem = onToggleItem,
                    onSelectAll = onSelectAll,
                    onDownload = onDownload,
                )
            }
        }
    }
}

@Composable
private fun IntroBlock() {
    Column {
        Text(
            "不離開正在看的內容。",
            color = TextPrimary,
            fontSize = 27.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "從分享、剪貼簿或懸浮工具取得公開 URL；只有你按下下載時才會建立工作。",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun PermissionPanel(
    status: AppPermissionStatus,
    onOverlaySettings: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    SectionCard {
        SectionLabel("快速工具設定")
        Spacer(Modifier.height(10.dp))
        PermissionRow("懸浮視窗", "顯示右側可拖曳按鈕", status.overlay, onOverlaySettings)
        PermissionRow("無障礙偵測", "僅限 Instagram / Threads 前景", status.accessibility, onAccessibilitySettings)
        PermissionRow("通知", "顯示前景服務與下載結果", status.notifications, onNotificationPermission)
        PermissionRow("儲存", "Downloads/IGThreads（Android 10+）", true, {})
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton(
                text = "啟動懸浮工具",
                primary = true,
                enabled = status.overlayReady,
                modifier = Modifier.weight(1f),
                onClick = onStartOverlay,
            )
            CompactButton(
                text = "關閉",
                primary = false,
                modifier = Modifier.width(72.dp),
                onClick = onStopOverlay,
            )
        }
        if (!status.overlayReady) {
            Spacer(Modifier.height(9.dp))
            Text(
                "開啟懸浮視窗與無障礙服務後，即可在 IG / Threads 上顯示工具。",
                color = Warning,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !enabled) { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (enabled) Success else Warning),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = TextSecondary, fontSize = 11.sp)
        }
        Text(
            if (enabled) "已開啟" else "前往設定",
            color = if (enabled) Success else Warning,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ResolverInput(
    value: String,
    isResolving: Boolean,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: () -> Unit,
) {
    SectionCard {
        SectionLabel("貼上公開連結")
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(Ink)
                .border(1.dp, Line, RoundedCornerShape(13.dp))
                .padding(13.dp),
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            ),
            minLines = 3,
            maxLines = 5,
            cursorBrush = SolidColor(Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onParse() }),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        "Instagram /p/、/reel/、/reels/、/tv/\n或 Threads /post/ 連結",
                        color = TextSecondary.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
                innerTextField()
            },
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton(
                text = "貼上剪貼簿",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onPaste,
            )
            CompactButton(
                text = if (isResolving) "解析中…" else "解析連結",
                primary = true,
                enabled = !isResolving,
                modifier = Modifier.weight(1f),
                onClick = onParse,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "回到 App 時會自動擷取剪貼簿內支援的連結。",
            color = TextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ManifestPreview(
    manifest: MediaManifest,
    selectedIds: Set<String>,
    onToggleItem: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("${manifest.platform.value.uppercase()} / ${manifest.type.value}")
                Text(
                    manifest.author?.let { "@$it" } ?: "公開來源",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
            StatusPill("${manifest.items.size} ITEMS", Primary)
        }
        manifest.thumbnailUrl?.let {
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = it,
                contentDescription = "媒體預覽",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.72f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        manifest.caption?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        manifest.warnings.forEach {
            Spacer(Modifier.height(8.dp))
            MessageBanner(it, Warning)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "原始畫質  ·  ${estimatedSize(manifest.items)}",
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (selectedIds.size == manifest.items.size) "取消全選" else "全選",
                color = Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    onSelectAll(selectedIds.size != manifest.items.size)
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        manifest.items.forEachIndexed { index, item ->
            MediaItemRow(
                item = item,
                index = index,
                selected = item.id in selectedIds,
                onToggle = { onToggleItem(item.id) },
            )
        }
        Spacer(Modifier.height(12.dp))
        CompactButton(
            text = "下載 ${selectedIds.size} 個已選項目",
            primary = true,
            enabled = selectedIds.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = onDownload,
        )
    }
}

@Composable
private fun MediaItemRow(
    item: MediaItem,
    index: Int,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 9.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionMark(selected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${index + 1}. ${if (item.type == MediaItemType.VIDEO) "影片" else "圖片"}",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "${item.mimeType ?: "格式待 CDN 回應"}  ·  ${formatBytes(item.contentLength)}",
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
        if (item.isHls) StatusPill("HLS 不支援", Error)
    }
}

@Composable
private fun RecordsPage(
    records: List<DownloadRecord>,
    emptyTitle: String,
    emptyBody: String,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onOpen: (DownloadRecord) -> Unit,
    onShare: (DownloadRecord) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (records.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("○", color = Line, fontSize = 44.sp)
                Spacer(Modifier.height(12.dp))
                Text(emptyTitle, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    emptyBody,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(records, key = { it.managerId }) { record ->
            DownloadRecordCard(
                record = record,
                onCancel = { onCancel(record.managerId) },
                onRetry = { onRetry(record.managerId) },
                onOpen = { onOpen(record) },
                onShare = { onShare(record) },
                onDelete = { onDelete(record.managerId) },
            )
        }
    }
}

@Composable
private fun DownloadRecordCard(
    record: DownloadRecord,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor(record.status).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (record.mediaType == MediaItemType.VIDEO) "▶" else "▧",
                    color = statusColor(record.status),
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.filename,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${record.platform.value}  ·  ${formatDate(record.updatedAt)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
            StatusPill(statusLabel(record.status), statusColor(record.status))
        }
        Spacer(Modifier.height(10.dp))
        val progress = record.progress
        if (progress != null && record.isActive) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Line),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(Primary),
                )
            }
            Spacer(Modifier.height(7.dp))
        }
        Text(
            buildString {
                append(formatBytes(record.bytesDownloaded))
                record.totalBytes?.let { append(" / ${formatBytes(it)}") }
                record.statusMessage?.let { append("  ·  $it") }
            },
            color = if (record.status == DownloadStatus.FAILED) Error else TextSecondary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                record.isActive -> CompactButton("取消", false, onClick = onCancel)
                record.status == DownloadStatus.SUCCEEDED -> {
                    CompactButton("開啟", true, onClick = onOpen)
                    CompactButton("分享", false, onClick = onShare)
                    CompactButton("刪除檔案", false, onClick = onDelete)
                }
                else -> {
                    CompactButton("重試", true, onClick = onRetry)
                    CompactButton("刪除紀錄", false, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface)
            .border(1.dp, Line.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
    )
}

@Composable
private fun MessageBanner(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(11.dp),
    )
}

@Composable
private fun SelectionMark(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) Primary else Color.Transparent)
            .border(1.dp, if (selected) Primary else TextSecondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}

@Composable
private fun CompactButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> Line
                    primary -> Primary
                    else -> SurfaceRaised
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) TextPrimary else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED -> Primary
    DownloadStatus.SUCCEEDED -> Success
    DownloadStatus.FAILED -> Error
    DownloadStatus.CANCELLED -> TextSecondary
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "等待"
    DownloadStatus.RUNNING -> "下載中"
    DownloadStatus.PAUSED -> "暫停"
    DownloadStatus.SUCCEEDED -> "完成"
    DownloadStatus.FAILED -> "失敗"
    DownloadStatus.CANCELLED -> "已取消"
}

private fun estimatedSize(items: List<MediaItem>): String =
    if (items.all { it.contentLength != null }) {
        formatBytes(items.sumOf { it.contentLength ?: 0L })
    } else {
        "大小未知"
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

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(Date(timestamp))
