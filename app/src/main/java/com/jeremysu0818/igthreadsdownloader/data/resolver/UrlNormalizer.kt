package com.jeremysu0818.igthreadsdownloader.data.resolver

import com.jeremysu0818.igthreadsdownloader.domain.model.ManifestType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class InstagramUrl(
    val normalizedUrl: String,
    val shortcode: String,
    val type: ManifestType,
)

data class ThreadsUrl(
    val normalizedUrl: String,
    val shortcode: String,
    val author: String?,
)

object UrlNormalizer {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val instagramHosts = setOf("instagram.com", "www.instagram.com", "m.instagram.com")
    private val threadsHosts = setOf(
        "threads.com",
        "www.threads.com",
        "threads.net",
        "www.threads.net",
    )
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '，', '。')

    fun extractFirstUrl(text: String): String? =
        urlRegex.findAll(text)
            .map { it.value.trimEnd(*trailingPunctuation) }
            .firstOrNull()

    fun extractSupportedUrl(text: String): String? =
        urlRegex.findAll(text)
            .map { it.value.trimEnd(*trailingPunctuation) }
            .firstOrNull { normalizeInstagram(it) != null || normalizeThreads(it) != null }

    fun normalizeInstagram(input: String): InstagramUrl? {
        val raw = extractFirstUrl(input) ?: input.trim()
        val parsed = raw.toHttpUrlOrNull() ?: return null
        if (parsed.host.lowercase() !in instagramHosts) return null

        val segments = parsed.pathSegments.filter { it.isNotBlank() }
        if (segments.size < 2) return null
        val route = segments[0].lowercase()
        if (route !in setOf("p", "reel", "reels", "tv")) return null
        val shortcode = segments[1].takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) } ?: return null
        val normalizedRoute = if (route == "reels") "reel" else route
        val type = when (normalizedRoute) {
            "reel", "tv" -> ManifestType.REEL
            else -> ManifestType.POST
        }

        return InstagramUrl(
            normalizedUrl = "https://www.instagram.com/$normalizedRoute/$shortcode/",
            shortcode = shortcode,
            type = type,
        )
    }

    fun normalizeThreads(input: String): ThreadsUrl? {
        val raw = extractFirstUrl(input) ?: input.trim()
        val parsed = raw.toHttpUrlOrNull() ?: return null
        if (parsed.host.lowercase() !in threadsHosts) return null
        val segments = parsed.pathSegments.filter { it.isNotBlank() }
        val postIndex = segments.indexOfFirst { it.equals("post", ignoreCase = true) }
        if (postIndex < 1 || postIndex + 1 >= segments.size) return null
        val shortcode = segments[postIndex + 1]
            .takeIf { it.matches(Regex("[A-Za-z0-9_-]+")) }
            ?: return null
        val author = segments.getOrNull(postIndex - 1)?.removePrefix("@")?.takeIf { it.isNotBlank() }
        val normalized = parsed.newBuilder()
            .scheme("https")
            .host("www.threads.com")
            .fragment(null)
            .build()
            .toString()

        return ThreadsUrl(
            normalizedUrl = normalized,
            shortcode = shortcode,
            author = author,
        )
    }

    fun isSupported(input: String): Boolean =
        normalizeInstagram(input) != null || normalizeThreads(input) != null
}
