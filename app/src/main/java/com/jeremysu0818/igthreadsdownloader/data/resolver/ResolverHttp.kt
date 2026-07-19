package com.jeremysu0818.igthreadsdownloader.data.resolver

import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItem
import com.jeremysu0818.igthreadsdownloader.domain.download.FilenameGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal const val ANDROID_CHROME_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro Build/AP4A.250105.002) " +
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

internal val defaultResolverClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

internal data class MediaProbe(
    val contentLength: Long?,
    val mimeType: String?,
)

internal fun OkHttpClient.probe(
    url: String,
    headers: Map<String, String>,
): MediaProbe {
    if (url.toHttpUrlOrNull() == null) return MediaProbe(null, null)
    return runCatching {
        val requestBuilder = Request.Builder()
            .url(url)
            .head()
            .header("User-Agent", ANDROID_CHROME_USER_AGENT)
            .header("Accept", "*/*")
        headers.forEach { (name, value) ->
            if (name.equals("Cookie", true) || name.equals("Referer", true)) {
                requestBuilder.header(name, value)
            }
        }
        newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                MediaProbe(null, null)
            } else {
                val length = response.header("Content-Length")
                    ?.toLongOrNull()
                    ?.takeIf { it > 0 }
                val mime = response.header("Content-Type")
                    ?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                MediaProbe(length, mime)
            }
        }
    }.getOrDefault(MediaProbe(null, null))
}

internal suspend fun OkHttpClient.enrichMediaMetadata(items: List<MediaItem>): List<MediaItem> {
    val probeClient = newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .build()
    val concurrency = Semaphore(4)
    return coroutineScope {
        items.map { item ->
            async(Dispatchers.IO) {
                concurrency.withPermit {
                    val probe = probeClient.probe(item.downloadUrl, item.requestHeaders)
                    val resolvedMime = probe.mimeType ?: item.mimeType
                    val resolvedExtension = FilenameGenerator.extensionFor(
                        mediaUrl = item.downloadUrl,
                        mimeType = resolvedMime,
                        type = item.type,
                    )
                    val filenameStem = item.filename.substringBeforeLast('.', item.filename)
                    item.copy(
                        contentLength = probe.contentLength,
                        mimeType = resolvedMime,
                        filename = "$filenameStem.$resolvedExtension",
                    )
                }
            }
        }.awaitAll()
    }
}

internal fun inferMimeType(url: String, type: MediaItemType): String? {
    val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase().orEmpty()
    return when {
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".webp") -> "image/webp"
        path.endsWith(".avif") -> "image/avif"
        path.endsWith(".mp4") -> "video/mp4"
        path.endsWith(".mov") -> "video/quicktime"
        path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
        else -> if (type == MediaItemType.VIDEO) null else null
    }
}

internal fun decodeEscapedHtmlValue(raw: String): String =
    raw.replace("\\u0026", "&", ignoreCase = true)
        .replace("\\u003d", "=", ignoreCase = true)
        .replace("\\u002f", "/", ignoreCase = true)
        .replace("\\/", "/")
        .replace("\\\"", "\"")
        .replace("\\'", "'")
        .replace("&amp;", "&")
        .trim()

internal fun mediaIdentity(url: String): String {
    val parsed = url.toHttpUrlOrNull()
    return if (parsed == null) {
        url.substringBefore('?')
    } else {
        "${parsed.host.lowercase()}${parsed.encodedPath.lowercase()}"
    }
}
