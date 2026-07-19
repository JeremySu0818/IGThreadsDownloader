package com.jeremysu0818.igthreadsdownloader.domain.model

enum class MediaPlatform(val value: String) {
    INSTAGRAM("instagram"),
    THREADS("threads"),
}

enum class ManifestType(val value: String) {
    REEL("reel"),
    POST("post"),
    PHOTO("photo"),
    VIDEO("video"),
    CAROUSEL("carousel"),
    UNKNOWN("unknown"),
}

enum class MediaItemType(val value: String) {
    IMAGE("image"),
    VIDEO("video"),
}

data class MediaManifest(
    val platform: MediaPlatform,
    val type: ManifestType,
    val author: String?,
    val sourceUrl: String,
    val title: String?,
    val caption: String?,
    val thumbnailUrl: String?,
    val items: List<MediaItem>,
    val isPartial: Boolean = false,
    val warnings: List<String> = emptyList(),
)

data class MediaItem(
    val id: String,
    val type: MediaItemType,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long?,
    val filename: String,
    val contentLength: Long?,
    val mimeType: String?,
    val requestHeaders: Map<String, String> = emptyMap(),
) {
    val isHls: Boolean
        get() = downloadUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true) ||
            mimeType.equals("application/vnd.apple.mpegurl", ignoreCase = true) ||
            mimeType.equals("application/x-mpegurl", ignoreCase = true)
}
