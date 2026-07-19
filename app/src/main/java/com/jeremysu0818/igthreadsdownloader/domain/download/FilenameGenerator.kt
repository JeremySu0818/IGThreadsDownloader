package com.jeremysu0818.igthreadsdownloader.domain.download

import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaPlatform
import java.net.URI
import java.util.Locale

object FilenameGenerator {
    private val knownExtensions = setOf("jpg", "jpeg", "png", "webp", "avif", "mp4", "mov", "m3u8")

    fun generate(
        platform: MediaPlatform,
        author: String?,
        shortcode: String,
        index: Int,
        type: MediaItemType,
        mediaUrl: String,
        mimeType: String? = null,
        occupiedNames: Set<String> = emptySet(),
    ): String {
        val safeAuthor = sanitize(author.orEmpty()).takeIf { it.isNotBlank() }
        val safeCode = sanitize(shortcode).ifBlank { "media" }
        val stem = buildList {
            add(platform.value)
            safeAuthor?.let(::add)
            add(safeCode)
            add((index + 1).toString().padStart(2, '0'))
        }.joinToString("_")
        val extension = extensionFor(mediaUrl, mimeType, type)
        val existing = occupiedNames.map { it.lowercase(Locale.US) }.toSet()

        var candidate = "$stem.$extension"
        var suffix = 2
        while (candidate.lowercase(Locale.US) in existing) {
            candidate = "${stem}_$suffix.$extension"
            suffix += 1
        }
        return candidate
    }

    fun extensionFor(
        mediaUrl: String,
        mimeType: String?,
        type: MediaItemType,
    ): String {
        val mimeExtension = when (mimeType?.substringBefore(';')?.lowercase(Locale.US)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/avif" -> "avif"
            "video/quicktime" -> "mov"
            "application/vnd.apple.mpegurl", "application/x-mpegurl" -> "m3u8"
            "video/mp4" -> "mp4"
            else -> null
        }
        if (mimeExtension != null) return mimeExtension

        val pathExtension = runCatching {
            URI(mediaUrl).path.substringAfterLast('.', "").lowercase(Locale.US)
        }.getOrDefault("")
        if (pathExtension in knownExtensions) return pathExtension

        return if (type == MediaItemType.VIDEO) "mp4" else "jpg"
    }

    private fun sanitize(value: String): String =
        value.trim()
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .take(48)
}
