package com.jeremysu0818.igthreadsdownloader.domain.download

import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaPlatform

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class DownloadRecord(
    val managerId: Long,
    val platform: MediaPlatform,
    val author: String?,
    val sourceUrl: String,
    val mediaId: String,
    val mediaType: MediaItemType,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val filename: String,
    val mimeType: String?,
    val requestHeaders: Map<String, String>,
    val status: DownloadStatus,
    val statusMessage: String?,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val localUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val progress: Float?
        get() = totalBytes?.takeIf { it > 0 }
            ?.let { (bytesDownloaded.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }

    val isActive: Boolean
        get() = status in setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
            DownloadStatus.PAUSED,
        )
}
