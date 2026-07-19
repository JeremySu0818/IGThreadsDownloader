package com.jeremysu0818.igthreadsdownloader.data.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadRecord
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadRepository
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadStatus
import com.jeremysu0818.igthreadsdownloader.domain.model.ManifestType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItem
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaManifest
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class AndroidDownloadRepository(
    context: Context,
) : DownloadRepository {
    private val appContext = context.applicationContext
    private val downloadManager =
        appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val preferences =
        appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val localId = AtomicLong(-1L)
    private val _records = MutableStateFlow(loadRecords())
    override val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

    private val completionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                scope.launch { refresh() }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            completionReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            while (isActive) {
                refresh()
                delay(if (_records.value.any { it.isActive }) 1_000 else 4_000)
            }
        }
    }

    override suspend fun enqueue(
        manifest: MediaManifest,
        items: List<MediaItem>,
    ): List<DownloadRecord> = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext emptyList()
        val occupied = currentOccupiedFilenames().toMutableSet()
        val created = items.map { item ->
            val filename = uniqueFilename(item.filename, occupied)
            occupied += filename.lowercase(Locale.US)
            if (item.isHls) {
                failedRecord(
                    manifest = manifest,
                    item = item,
                    filename = filename,
                    message = "此媒體是 HLS 串流（m3u8），目前未支援串流合併，未建立假下載。",
                )
            } else {
                enqueueOne(manifest, item, filename)
            }
        }
        updateRecords(_records.value + created)
        created
    }

    override suspend fun cancel(managerId: Long) = withContext(Dispatchers.IO) {
        if (managerId > 0) downloadManager.remove(managerId)
        replaceRecord(managerId) {
            it.copy(
                status = DownloadStatus.CANCELLED,
                statusMessage = "已取消",
                updatedAt = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun retry(managerId: Long): DownloadRecord? = withContext(Dispatchers.IO) {
        val previous = _records.value.firstOrNull { it.managerId == managerId }
            ?: return@withContext null
        val item = MediaItem(
            id = previous.mediaId,
            type = previous.mediaType,
            downloadUrl = previous.downloadUrl,
            thumbnailUrl = previous.thumbnailUrl,
            width = null,
            height = null,
            durationMs = null,
            filename = previous.filename,
            contentLength = previous.totalBytes,
            mimeType = previous.mimeType,
            requestHeaders = previous.requestHeaders,
        )
        val manifest = MediaManifest(
            platform = previous.platform,
            type = if (previous.mediaType == MediaItemType.VIDEO) {
                ManifestType.VIDEO
            } else {
                ManifestType.PHOTO
            },
            author = previous.author,
            sourceUrl = previous.sourceUrl,
            title = null,
            caption = null,
            thumbnailUrl = previous.thumbnailUrl,
            items = listOf(item),
        )
        enqueue(manifest, listOf(item)).firstOrNull()
    }

    override suspend fun delete(managerId: Long): Boolean = withContext(Dispatchers.IO) {
        val record = _records.value.firstOrNull { it.managerId == managerId }
            ?: return@withContext false
        val removed = if (record.managerId > 0) {
            runCatching { downloadManager.remove(record.managerId) >= 0 }.getOrDefault(false)
        } else {
            true
        }
        updateRecords(_records.value.filterNot { it.managerId == managerId })
        removed
    }

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        val managerIds = _records.value.map { it.managerId }.filter { it > 0 }.distinct()
        if (managerIds.isEmpty()) return@withContext
        val updates = mutableMapOf<Long, DownloadSnapshot>()
        val query = DownloadManager.Query().setFilterById(*managerIds.toLongArray())
        runCatching {
            downloadManager.query(query)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val snapshot = cursor.toSnapshot()
                    updates[snapshot.managerId] = snapshot
                }
            }
        }
        if (updates.isEmpty()) return@withContext

        val now = System.currentTimeMillis()
        val changed = _records.value.map { record ->
            val snapshot = updates[record.managerId] ?: return@map record
            val uri = if (snapshot.status == DownloadStatus.SUCCEEDED) {
                runCatching {
                    downloadManager.getUriForDownloadedFile(record.managerId)?.toString()
                }.getOrNull() ?: snapshot.localUri
            } else {
                snapshot.localUri
            }
            val emptyFile = snapshot.status == DownloadStatus.SUCCEEDED &&
                snapshot.bytesDownloaded <= 0
            record.copy(
                status = if (emptyFile) DownloadStatus.FAILED else snapshot.status,
                statusMessage = if (emptyFile) "下載檔案為空，已標記失敗。" else snapshot.message,
                bytesDownloaded = snapshot.bytesDownloaded.coerceAtLeast(0),
                totalBytes = snapshot.totalBytes?.takeIf { it > 0 },
                localUri = uri,
                updatedAt = if (
                    record.status != snapshot.status ||
                    record.bytesDownloaded != snapshot.bytesDownloaded
                ) {
                    now
                } else {
                    record.updatedAt
                },
            )
        }
        if (changed != _records.value) updateRecords(changed)
    }

    override fun open(record: DownloadRecord): Boolean {
        val uri = contentUri(record) ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, record.mimeType ?: "*/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return startSafely(intent)
    }

    override fun share(record: DownloadRecord): Boolean {
        val uri = contentUri(record) ?: return false
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(record.filename, uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return startSafely(Intent.createChooser(intent, "分享 ${record.filename}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun enqueueOne(
        manifest: MediaManifest,
        item: MediaItem,
        filename: String,
    ): DownloadRecord {
        val now = System.currentTimeMillis()
        return try {
            val request = DownloadManager.Request(item.downloadUrl.toUri())
                .setTitle(filename)
                .setDescription("下載 ${manifest.platform.value} 媒體")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "$DOWNLOAD_FOLDER/$filename",
                )
            item.mimeType?.let(request::setMimeType)
            item.requestHeaders.forEach { (name, value) ->
                if (name.isNotBlank() && value.isNotBlank()) {
                    request.addRequestHeader(name, value)
                }
            }
            val id = downloadManager.enqueue(request)
            DownloadRecord(
                managerId = id,
                platform = manifest.platform,
                author = manifest.author,
                sourceUrl = manifest.sourceUrl,
                mediaId = item.id,
                mediaType = item.type,
                downloadUrl = item.downloadUrl,
                thumbnailUrl = item.thumbnailUrl,
                filename = filename,
                mimeType = item.mimeType,
                requestHeaders = item.requestHeaders,
                status = DownloadStatus.QUEUED,
                statusMessage = "等待下載",
                bytesDownloaded = 0,
                totalBytes = item.contentLength,
                localUri = null,
                createdAt = now,
                updatedAt = now,
            )
        } catch (error: SecurityException) {
            failedRecord(manifest, item, filename, "下載權限未開：${error.message.orEmpty()}")
        } catch (error: IllegalStateException) {
            failedRecord(manifest, item, filename, "儲存空間不可用：${error.message.orEmpty()}")
        } catch (error: RuntimeException) {
            failedRecord(manifest, item, filename, "無法建立下載：${error.message.orEmpty()}")
        }
    }

    private fun failedRecord(
        manifest: MediaManifest,
        item: MediaItem,
        filename: String,
        message: String,
    ): DownloadRecord {
        val now = System.currentTimeMillis()
        return DownloadRecord(
            managerId = localId.getAndDecrement(),
            platform = manifest.platform,
            author = manifest.author,
            sourceUrl = manifest.sourceUrl,
            mediaId = item.id,
            mediaType = item.type,
            downloadUrl = item.downloadUrl,
            thumbnailUrl = item.thumbnailUrl,
            filename = filename,
            mimeType = item.mimeType,
            requestHeaders = item.requestHeaders,
            status = DownloadStatus.FAILED,
            statusMessage = message,
            bytesDownloaded = 0,
            totalBytes = item.contentLength,
            localUri = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    private fun Cursor.toSnapshot(): DownloadSnapshot {
        val id = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
        val rawStatus = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val reason = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        val bytes = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val localUri = getStringOrNull(DownloadManager.COLUMN_LOCAL_URI)
        val status = when (rawStatus) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.QUEUED
            DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCEEDED
            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
            else -> DownloadStatus.FAILED
        }
        return DownloadSnapshot(
            managerId = id,
            status = status,
            message = downloadStatusMessage(status, reason),
            bytesDownloaded = bytes,
            totalBytes = total.takeIf { it > 0 },
            localUri = localUri,
        )
    }

    private fun Cursor.getStringOrNull(columnName: String): String? {
        val index = getColumnIndex(columnName)
        return if (index >= 0 && !isNull(index)) getString(index) else null
    }

    private fun downloadStatusMessage(status: DownloadStatus, reason: Int): String = when (status) {
        DownloadStatus.QUEUED -> "等待下載"
        DownloadStatus.RUNNING -> "下載中"
        DownloadStatus.PAUSED -> when (reason) {
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "等待網路連線"
            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "等待 Wi-Fi"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "系統稍後重試"
            else -> "下載已暫停"
        }
        DownloadStatus.SUCCEEDED -> "下載完成"
        DownloadStatus.CANCELLED -> "已取消"
        DownloadStatus.FAILED -> when (reason) {
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "儲存空間不足"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "重複檔案"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "找不到儲存裝置"
            DownloadManager.ERROR_FILE_ERROR -> "儲存檔案失敗"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "CDN 傳輸失敗"
            DownloadManager.ERROR_CANNOT_RESUME -> "CDN 不支援續傳"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "CDN 重新導向次數過多"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "CDN 回傳不支援的 HTTP 狀態"
            else -> "下載失敗（錯誤碼 $reason）"
        }
    }

    private fun replaceRecord(
        managerId: Long,
        transform: (DownloadRecord) -> DownloadRecord,
    ) {
        updateRecords(_records.value.map { if (it.managerId == managerId) transform(it) else it })
    }

    private fun updateRecords(records: List<DownloadRecord>) {
        val sorted = records.sortedByDescending { it.createdAt }
        _records.value = sorted
        preferences.edit {
            putString(KEY_RECORDS, encodeRecords(sorted).toString())
        }
    }

    private fun currentOccupiedFilenames(): Set<String> {
        val recorded = _records.value.map { it.filename }
        @Suppress("DEPRECATION")
        val files = runCatching {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DOWNLOAD_FOLDER,
            ).list()?.toList().orEmpty()
        }.getOrDefault(emptyList())
        return (recorded + files).map { it.lowercase(Locale.US) }.toSet()
    }

    private fun uniqueFilename(desired: String, occupied: Set<String>): String {
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.substring(0, dot) else desired
        val extension = if (dot > 0) desired.substring(dot) else ""
        var candidate = desired
        var suffix = 2
        while (candidate.lowercase(Locale.US) in occupied) {
            candidate = "${stem}_$suffix$extension"
            suffix += 1
        }
        return candidate
    }

    private fun contentUri(record: DownloadRecord): Uri? {
        if (record.status != DownloadStatus.SUCCEEDED) return null
        return runCatching {
            if (record.managerId > 0) {
                downloadManager.getUriForDownloadedFile(record.managerId)
            } else {
                record.localUri?.let(Uri::parse)
            }
        }.getOrNull()
    }

    private fun startSafely(intent: Intent): Boolean = runCatching {
        if (intent.resolveActivity(appContext.packageManager) == null) return false
        appContext.startActivity(intent)
        true
    }.getOrDefault(false)

    private fun loadRecords(): List<DownloadRecord> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                repeat(array.length()) { index ->
                    add(array.getJSONObject(index).toRecord())
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeRecords(records: List<DownloadRecord>): JSONArray = JSONArray().apply {
        records.forEach { put(it.toJson()) }
    }

    private fun DownloadRecord.toJson(): JSONObject = JSONObject().apply {
        put("managerId", managerId)
        put("platform", platform.name)
        put("author", author)
        put("sourceUrl", sourceUrl)
        put("mediaId", mediaId)
        put("mediaType", mediaType.name)
        put("downloadUrl", downloadUrl)
        put("thumbnailUrl", thumbnailUrl)
        put("filename", filename)
        put("mimeType", mimeType)
        put("headers", JSONObject(requestHeaders))
        put("status", status.name)
        put("statusMessage", statusMessage)
        put("bytesDownloaded", bytesDownloaded)
        put("totalBytes", totalBytes)
        put("localUri", localUri)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toRecord(): DownloadRecord {
        val headersObject = optJSONObject("headers") ?: JSONObject()
        val headers = buildMap {
            headersObject.keys().forEach { key -> put(key, headersObject.optString(key)) }
        }
        return DownloadRecord(
            managerId = getLong("managerId"),
            platform = enumValueOf(optString("platform", MediaPlatform.INSTAGRAM.name)),
            author = optNullableString("author"),
            sourceUrl = getString("sourceUrl"),
            mediaId = getString("mediaId"),
            mediaType = enumValueOf(optString("mediaType", MediaItemType.IMAGE.name)),
            downloadUrl = getString("downloadUrl"),
            thumbnailUrl = optNullableString("thumbnailUrl"),
            filename = getString("filename"),
            mimeType = optNullableString("mimeType"),
            requestHeaders = headers,
            status = enumValueOf(optString("status", DownloadStatus.FAILED.name)),
            statusMessage = optNullableString("statusMessage"),
            bytesDownloaded = optLong("bytesDownloaded", 0L),
            totalBytes = if (has("totalBytes") && !isNull("totalBytes")) getLong("totalBytes") else null,
            localUri = optNullableString("localUri"),
            createdAt = optLong("createdAt", System.currentTimeMillis()),
            updatedAt = optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private data class DownloadSnapshot(
        val managerId: Long,
        val status: DownloadStatus,
        val message: String?,
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val localUri: String?,
    )

    companion object {
        private const val PREFERENCES_NAME = "download_history"
        private const val KEY_RECORDS = "records"
        private const val DOWNLOAD_FOLDER = "IGThreads"
    }
}
