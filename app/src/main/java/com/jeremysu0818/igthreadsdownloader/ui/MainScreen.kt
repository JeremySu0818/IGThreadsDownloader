package com.jeremysu0818.igthreadsdownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import android.os.Build
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteAmber
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteBg
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteCard
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteCardBorder
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteCardHover
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteEmerald
import com.jeremysu0818.igthreadsdownloader.ui.theme.MattePrimary
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteRose
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteTextMuted
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteTextPrimary
import com.jeremysu0818.igthreadsdownloader.ui.theme.MatteTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class NavItem(
    val tab: MainTab,
    val label: String,
    val icon: ImageVector,
    val count: Int,
)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    permissionStatus: AppPermissionStatus,
    onOverlaySettings: () -> Unit,
    onNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onPasteClipboard: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val hazeState = remember { HazeState() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MatteBg)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().hazeSource(state = hazeState),
        ) {
            MainHeader(permissionStatus = permissionStatus)

            when (state.tab) {
                MainTab.DOWNLOAD -> DownloadPage(
                    state = state,
                    permissionStatus = permissionStatus,
                    onInputChange = viewModel::updateInput,
                    onParse = viewModel::parse,
                    onPasteClipboard = onPasteClipboard,
                    onOverlaySettings = onOverlaySettings,
                    onNotificationPermission = onNotificationPermission,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                    onToggleItem = viewModel::toggleSelection,
                    onSelectAll = viewModel::selectAll,
                    onDownload = viewModel::downloadSelected,
                )
                MainTab.QUEUE -> RecordsPage(
                    records = state.records.filter { it.isActive },
                    emptyTitle = "目前沒有下載中的工作",
                    emptyBody = "在「解析」貼上 IG 或 Threads 連結後，下載進度會顯示在這裡。",
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry,
                    onOpen = viewModel::open,
                    onShare = viewModel::share,
                    onDelete = viewModel::delete,
                )
                MainTab.HISTORY -> RecordsPage(
                    records = state.records.filter { !it.isActive },
                    emptyTitle = "尚無歷史紀錄",
                    emptyBody = "已下載完成或取消的檔案紀錄會存放在這裡。",
                    onCancel = viewModel::cancel,
                    onRetry = viewModel::retry,
                    onOpen = viewModel::open,
                    onShare = viewModel::share,
                    onDelete = viewModel::delete,
                )
            }
        }

        BottomNavBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
            hazeState = hazeState,
            selected = state.tab,
            activeCount = state.records.count { it.isActive },
            historyCount = state.records.count { !it.isActive },
            onSelect = viewModel::selectTab,
        )
    }
}

@Composable
private fun MainHeader(permissionStatus: AppPermissionStatus) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MattePrimary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "IG & Threads 下載器",
                color = MatteTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "公開影音與圖片儲存",
                color = MatteTextMuted,
                fontSize = 11.sp,
            )
        }
        
        SimpleStatusPill(
            text = if (permissionStatus.overlayReady) "懸浮視窗已開啟" else "懸浮視窗未開啟",
            enabled = permissionStatus.overlayReady
        )
    }
}

@Composable
private fun BottomNavBar(
    modifier: Modifier = Modifier,
    hazeState: HazeState,
    selected: MainTab,
    activeCount: Int,
    historyCount: Int,
    onSelect: (MainTab) -> Unit,
) {
    val items = listOf(
        NavItem(MainTab.DOWNLOAD, "解析", Icons.Default.Download, 0),
        NavItem(MainTab.QUEUE, "佇列", Icons.Default.Bolt, activeCount),
        NavItem(MainTab.HISTORY, "紀錄", Icons.Default.History, historyCount),
    )

    Box(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(28.dp),
                spotColor = Color.Black.copy(alpha = 0.45f),
                ambientColor = Color.Black.copy(alpha = 0.25f),
            )
            .clip(RoundedCornerShape(28.dp))
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = MatteBg,
                    blurRadius = 15.dp,
                    tint = HazeTint(Color(0xC816181D))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(28.dp)),
    ) {

        // Layer 2: Ultra-Crisp Foreground Content Layer (Text and Icons are 100% sharp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { navItem ->
                val isSelected = selected == navItem.tab

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isSelected) MatteCardHover else Color.Transparent)
                        .clickable { onSelect(navItem.tab) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = navItem.icon,
                            contentDescription = navItem.label,
                            tint = if (isSelected) MattePrimary else MatteTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            navItem.label,
                            color = if (isSelected) MatteTextPrimary else MatteTextMuted,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                        if (navItem.count > 0) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) MattePrimary else MatteCardBorder)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${navItem.count}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
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
    onNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 8.dp,
            end = 20.dp,
            bottom = 100.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            QuickSettingsCard(
                status = permissionStatus,
                onOverlaySettings = onOverlaySettings,
                onNotificationPermission = onNotificationPermission,
                onStartOverlay = onStartOverlay,
                onStopOverlay = onStopOverlay,
            )
        }
        item {
            UrlInputCard(
                value = state.input,
                isResolving = state.isResolving,
                onValueChange = onInputChange,
                onPaste = onPasteClipboard,
                onParse = onParse,
            )
        }
        state.errorMessage?.let { message ->
            item { SimpleBanner(message, MatteRose) }
        }
        state.noticeMessage?.let { message ->
            item { SimpleBanner(message, MatteEmerald) }
        }
        state.manifest?.let { manifest ->
            item {
                ManifestCard(
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
private fun QuickSettingsCard(
    status: AppPermissionStatus,
    onOverlaySettings: () -> Unit,
    onNotificationPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
) {
    MatteCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = MatteTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "快速設定",
                color = MatteTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusChip(
                label = "懸浮視窗",
                enabled = status.overlay,
                modifier = Modifier.weight(1f),
                onClick = onOverlaySettings
            )
            StatusChip(
                label = "通知權限",
                enabled = status.notifications,
                modifier = Modifier.weight(1f),
                onClick = onNotificationPermission
            )
            StatusChip(
                label = "儲存目錄",
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = {}
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HighRadiusButton(
                text = "啟動懸浮工具",
                icon = Icons.Default.PlayArrow,
                primary = true,
                enabled = status.overlayReady,
                modifier = Modifier.weight(1f),
                onClick = onStartOverlay,
            )
            HighRadiusButton(
                text = "關閉懸浮窗",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onStopOverlay,
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MatteCardHover)
            .border(
                1.dp,
                if (enabled) MatteEmerald.copy(alpha = 0.4f) else MatteCardBorder,
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (enabled) MatteEmerald else MatteAmber)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                color = MatteTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (enabled) "已開啟" else "前往設定",
                color = if (enabled) MatteEmerald else MatteAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UrlInputCard(
    value: String,
    isResolving: Boolean,
    onValueChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: () -> Unit,
) {
    MatteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Link,
                contentDescription = null,
                tint = MatteTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "貼上貼文連結",
                color = MatteTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MatteBg)
                .border(1.dp, MatteCardBorder, RoundedCornerShape(18.dp))
                .padding(13.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = MatteTextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
                minLines = 2,
                maxLines = 4,
                cursorBrush = SolidColor(MattePrimary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onParse() }),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            "貼上 Instagram (/p, /reel) 或 Threads 連結...",
                            color = MatteTextMuted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                    innerTextField()
                },
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HighRadiusButton(
                text = "貼上剪貼簿",
                icon = Icons.Default.ContentPaste,
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = onPaste,
            )
            HighRadiusButton(
                text = if (isResolving) "解析中..." else "解析連結",
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                primary = true,
                enabled = !isResolving,
                modifier = Modifier.weight(1f),
                onClick = onParse,
            )
        }
    }
}

@Composable
private fun ManifestCard(
    manifest: MediaManifest,
    selectedIds: Set<String>,
    onToggleItem: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    MatteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${manifest.platform.value.uppercase()}  ·  ${manifest.author?.let { "@$it" } ?: "公開來源"}",
                    color = MatteTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                "${manifest.items.size} 個項目",
                color = MattePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        manifest.thumbnailUrl?.let {
            Spacer(Modifier.height(12.dp))
            AsyncImage(
                model = it,
                contentDescription = "媒體預覽",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.78f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MatteBg),
                contentScale = ContentScale.Crop,
            )
        }

        manifest.caption?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                it,
                color = MatteTextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        manifest.warnings.forEach {
            Spacer(Modifier.height(8.dp))
            SimpleBanner(it, MatteAmber)
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "檔案預估大小：${estimatedSize(manifest.items)}",
                color = MatteTextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (selectedIds.size == manifest.items.size) "取消全選" else "全選",
                color = MattePrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    onSelectAll(selectedIds.size != manifest.items.size)
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        manifest.items.forEachIndexed { index, item ->
            MediaItemRow(
                item = item,
                index = index,
                selected = item.id in selectedIds,
                onToggle = { onToggleItem(item.id) },
            )
        }

        Spacer(Modifier.height(14.dp))

        HighRadiusButton(
            text = "下載選取的 ${selectedIds.size} 個項目",
            icon = Icons.Default.Download,
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
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MatteCardHover else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SimpleCheckCircle(selected)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.type == MediaItemType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.Image,
                    contentDescription = null,
                    tint = MatteTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "#${index + 1} ${if (item.type == MediaItemType.VIDEO) "影片" else "圖片"}",
                    color = MatteTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                "${item.mimeType ?: "檔案"}  ·  ${formatBytes(item.contentLength)}",
                color = MatteTextMuted,
                fontSize = 11.sp,
            )
        }
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
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MatteCard)
                        .border(1.dp, MatteCardBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MatteTextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    emptyTitle,
                    color = MatteTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    emptyBody,
                    color = MatteTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.widthIn(max = 280.dp),
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(records, key = { it.managerId }) { record ->
            RecordCard(
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
private fun RecordCard(
    record: DownloadRecord,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    MatteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor(record.status).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (record.mediaType == MediaItemType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.Image,
                    contentDescription = null,
                    tint = statusColor(record.status),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.filename,
                    color = MatteTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${record.platform.value}  ·  ${formatDate(record.updatedAt)}",
                    color = MatteTextMuted,
                    fontSize = 11.sp,
                )
            }
            SimpleBadge(statusLabel(record.status), statusColor(record.status))
        }

        val progress = record.progress
        if (progress != null && record.isActive) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MatteBg),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MattePrimary),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            buildString {
                append(formatBytes(record.bytesDownloaded))
                record.totalBytes?.let { append(" / ${formatBytes(it)}") }
                record.statusMessage?.let { append("  ·  $it") }
            },
            color = if (record.status == DownloadStatus.FAILED) MatteRose else MatteTextMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                record.isActive -> HighRadiusButton("取消", icon = Icons.Default.Close, primary = false, onClick = onCancel)
                record.status == DownloadStatus.SUCCEEDED -> {
                    HighRadiusButton("開啟檔案", icon = Icons.Default.PlayArrow, primary = true, onClick = onOpen)
                    HighRadiusButton("分享", icon = Icons.Default.Share, primary = false, onClick = onShare)
                    HighRadiusButton("刪除檔案", icon = Icons.Default.Delete, primary = false, onClick = onDelete)
                }
                else -> {
                    HighRadiusButton("重試", icon = Icons.Default.Refresh, primary = true, onClick = onRetry)
                    HighRadiusButton("刪除紀錄", icon = Icons.Default.Delete, primary = false, onClick = onDelete)
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// REUSABLE MINIMALIST MATTE COMPONENTS
// -----------------------------------------------------------------------------

@Composable
private fun MatteCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MatteCard)
            .border(1.dp, MatteCardBorder, RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun HighRadiusButton(
    text: String,
    icon: ImageVector? = null,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                when {
                    !enabled -> MatteCardHover
                    primary -> MattePrimary
                    else -> MatteCardHover
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else MatteTextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text,
                color = if (enabled) Color.White else MatteTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SimpleStatusPill(text: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (enabled) MatteEmerald.copy(alpha = 0.12f) else MatteCardHover)
            .border(1.dp, if (enabled) MatteEmerald.copy(alpha = 0.3f) else MatteCardBorder, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (enabled) MatteEmerald else MatteAmber)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = if (enabled) MatteEmerald else MatteTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SimpleBadge(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun SimpleCheckCircle(selected: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) MattePrimary else Color.Transparent)
            .border(
                1.5.dp,
                if (selected) MattePrimary else MatteTextMuted,
                CircleShape
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun SimpleBanner(text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.QUEUED, DownloadStatus.RUNNING, DownloadStatus.PAUSED -> MattePrimary
    DownloadStatus.SUCCEEDED -> MatteEmerald
    DownloadStatus.FAILED -> MatteRose
    DownloadStatus.CANCELLED -> MatteTextMuted
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "等待中"
    DownloadStatus.RUNNING -> "下載中"
    DownloadStatus.PAUSED -> "已暫停"
    DownloadStatus.SUCCEEDED -> "已完成"
    DownloadStatus.FAILED -> "下載失敗"
    DownloadStatus.CANCELLED -> "已取消"
}

private fun estimatedSize(items: List<MediaItem>): String =
    if (items.all { it.contentLength != null }) {
        formatBytes(items.sumOf { it.contentLength ?: 0L })
    } else {
        "未知"
    }

private fun formatBytes(bytes: Long?): String {
    bytes ?: return "未知大小"
    return when {
        bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MM/dd HH:mm", Locale.TAIWAN).format(Date(timestamp))
