package com.jeremysu0818.igthreadsdownloader.data.resolver

import com.jeremysu0818.igthreadsdownloader.domain.download.FilenameGenerator
import com.jeremysu0818.igthreadsdownloader.domain.model.ManifestType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItem
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaManifest
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaPlatform
import com.jeremysu0818.igthreadsdownloader.domain.resolver.MediaResolver
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverError
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Locale

class ThreadsHtmlResolver(
    private val client: OkHttpClient = defaultResolverClient,
) : MediaResolver {
    override fun supports(url: String): Boolean = UrlNormalizer.normalizeThreads(url) != null

    override suspend fun resolve(url: String): ResolverResult = withContext(Dispatchers.IO) {
        val normalized = UrlNormalizer.normalizeThreads(url)
            ?: return@withContext ResolverResult.Failure(ResolverError.InvalidUrl())
        val endpoint = buildEndpoint(normalized.normalizedUrl)
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("User-Agent", ANDROID_CHROME_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", BASE_URL)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ResolverResult.Failure(
                        ResolverError.fromHttpStatus(
                            statusCode = response.code,
                            responseText = responseText,
                            platform = "threads",
                        ),
                    )
                }
                val cookieHeader = responseCookies(response.headers, response.request.url)
                when (
                    val parsed = parseHtml(
                        sourceUrl = normalized.normalizedUrl,
                        html = responseText,
                        baseUrl = response.request.url.toString(),
                        cookieHeader = cookieHeader,
                    )
                ) {
                    is ResolverResult.Failure -> parsed
                    is ResolverResult.Success -> {
                        val enriched = client.enrichMediaMetadata(parsed.manifest.items)
                        ResolverResult.Success(parsed.manifest.copy(items = enriched))
                    }
                }
            }
        } catch (error: IOException) {
            ResolverResult.Failure(ResolverError.Network(error.message))
        } catch (error: RuntimeException) {
            ResolverResult.Failure(
                ResolverError.HtmlStructureChanged("threads", error.message),
            )
        }
    }

    fun buildEndpoint(sourceUrl: String): HttpUrl =
        "$BASE_URL/download".toHttpUrl()
            .newBuilder()
            .addQueryParameter("url", sourceUrl)
            .build()

    internal fun parseHtml(
        sourceUrl: String,
        html: String,
        baseUrl: String = "$BASE_URL/download",
        cookieHeader: String? = null,
    ): ResolverResult {
        val normalized = UrlNormalizer.normalizeThreads(sourceUrl)
            ?: return ResolverResult.Failure(ResolverError.InvalidUrl())
        if (html.isBlank()) {
            return ResolverResult.Failure(
                ResolverError.HtmlStructureChanged("threads", "empty response"),
            )
        }

        val document = Jsoup.parse(html, baseUrl)
        val candidates = LinkedHashMap<String, Candidate>()

        document.select("script").forEach { script ->
            val decoded = decodeEscapedHtmlValue(script.data())
            extractNamedArrays(decoded, "videoUrl").forEach {
                addCandidate(candidates, it, MediaItemType.VIDEO, 100)
            }
            extractNamedArrays(decoded, "imageUrl").forEach {
                addCandidate(candidates, it, MediaItemType.IMAGE, 100)
            }
        }

        document.select("video[src], video source[src], source[src]").forEach { element ->
            val source = element.mediaSource("src")
            addCandidate(candidates, source, MediaItemType.VIDEO, 80)
        }
        document.select("video[poster]").forEach { element ->
            addCandidate(candidates, element.mediaSource("poster"), MediaItemType.IMAGE, 30)
        }
        document.select("img[src], img[data-src]").forEach { element ->
            val attribute = if (element.hasAttr("src")) "src" else "data-src"
            addCandidate(candidates, element.mediaSource(attribute), MediaItemType.IMAGE, 60)
        }
        document.select("a[href]").forEach { element ->
            val href = element.mediaSource("href")
            if (looksLikeDownloadLink(element, href)) {
                addCandidate(candidates, href, typeForUrl(href), 70)
            }
        }

        val accepted = candidates.values
            .filter { isAllowedMedia(it.url, it.type) }
            .sortedByDescending { it.priority }
            .distinctBy { mediaIdentity(it.url) }
            .take(MAX_MEDIA_ITEMS)
        if (accepted.isEmpty()) {
            val lower = document.text().lowercase(Locale.US)
            val error = when {
                "rate limit" in lower || "too many requests" in lower -> ResolverError.RateLimited()
                "not found" in lower || "deleted" in lower -> ResolverError.ContentNotFound()
                else -> ResolverError.HtmlStructureChanged("threads")
            }
            return ResolverResult.Failure(error)
        }

        val requestHeaders = buildMap {
            put("User-Agent", ANDROID_CHROME_USER_AGENT)
            put("Referer", BASE_URL)
            cookieHeader?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        }
        val filenames = mutableSetOf<String>()
        val items = accepted.mapIndexed { index, candidate ->
            val mimeType = inferMimeType(candidate.url, candidate.type)
            val filename = FilenameGenerator.generate(
                platform = MediaPlatform.THREADS,
                author = normalized.author,
                shortcode = normalized.shortcode,
                index = index,
                type = candidate.type,
                mediaUrl = candidate.url,
                mimeType = mimeType,
                occupiedNames = filenames,
            )
            filenames += filename
            MediaItem(
                id = "threads_${normalized.shortcode}_$index",
                type = candidate.type,
                downloadUrl = candidate.url,
                thumbnailUrl = candidate.url.takeIf { candidate.type == MediaItemType.IMAGE },
                width = null,
                height = null,
                durationMs = null,
                filename = filename,
                contentLength = null,
                mimeType = mimeType,
                requestHeaders = requestHeaders,
            )
        }
        val type = when {
            items.size > 1 -> ManifestType.CAROUSEL
            items.first().type == MediaItemType.VIDEO -> ManifestType.VIDEO
            else -> ManifestType.PHOTO
        }
        return ResolverResult.Success(
            MediaManifest(
                platform = MediaPlatform.THREADS,
                type = type,
                author = normalized.author,
                sourceUrl = normalized.normalizedUrl,
                title = null,
                caption = null,
                thumbnailUrl = items.firstOrNull { it.type == MediaItemType.IMAGE }?.downloadUrl,
                items = items,
            ),
        )
    }

    private fun responseCookies(headers: Headers, url: HttpUrl): String? =
        Cookie.parseAll(url, headers)
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { "${it.name}=${it.value}" }

    private fun extractNamedArrays(script: String, key: String): List<String> {
        val keyPattern = Regex(
            """"${Regex.escape(key)}"\s*:\s*\[(.*?)]""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val stringPattern = Regex("""["'](https?://.*?)(?<!\\)["']""", RegexOption.DOT_MATCHES_ALL)
        return keyPattern.findAll(script).flatMap { match ->
            stringPattern.findAll(match.groupValues[1]).map { value ->
                decodeEscapedHtmlValue(value.groupValues[1])
            }
        }.toList()
    }

    private fun Element.mediaSource(attribute: String): String {
        val absolute = absUrl(attribute)
        return decodeEscapedHtmlValue(absolute.ifBlank { attr(attribute) })
    }

    private fun looksLikeDownloadLink(element: Element, href: String): Boolean {
        val context = buildString {
            append(href)
            append(' ')
            append(element.text())
            append(' ')
            append(element.className())
            append(' ')
            append(element.parent()?.text().orEmpty())
        }.lowercase(Locale.US)
        return downloadKeywords.any(context::contains) ||
            mediaExtensions.any { extension -> href.substringBefore('?').endsWith(extension, true) }
    }

    private fun addCandidate(
        destination: MutableMap<String, Candidate>,
        rawUrl: String,
        type: MediaItemType,
        priority: Int,
    ) {
        val url = decodeEscapedHtmlValue(rawUrl)
        if (url.toHttpUrlOrNull() == null) return
        val key = mediaIdentity(url)
        val existing = destination[key]
        if (existing == null || priority > existing.priority) {
            destination[key] = Candidate(url, type, priority)
        }
    }

    private fun typeForUrl(url: String): MediaItemType {
        val path = url.substringBefore('?').lowercase(Locale.US)
        return if (videoExtensions.any(path::endsWith) || "video" in path) {
            MediaItemType.VIDEO
        } else {
            MediaItemType.IMAGE
        }
    }

    private fun isAllowedMedia(url: String, type: MediaItemType): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val host = parsed.host.lowercase(Locale.US)
        val path = parsed.encodedPath.lowercase(Locale.US)
        val combined = "$host$path"
        if (blockedTokens.any(combined::contains)) return false
        val hasMediaExtension = mediaExtensions.any(path::endsWith)
        if (host == "threadsphotodownloader.com" || host == "www.threadsphotodownloader.com") {
            return hasMediaExtension &&
                ("/media/" in path || "/download/" in path || "/files/" in path)
        }
        val knownCdn = "cdn" in host || "fbcdn" in host || "cdninstagram" in host
        return hasMediaExtension || knownCdn ||
            (type == MediaItemType.VIDEO && ("video" in path || "media" in path)) ||
            (type == MediaItemType.IMAGE && ("image" in path || "media" in path))
    }

    private data class Candidate(
        val url: String,
        val type: MediaItemType,
        val priority: Int,
    )

    companion object {
        const val BASE_URL = "https://threadsphotodownloader.com"

        private val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".avif")
        private val videoExtensions = listOf(".mp4", ".mov", ".m3u8")
        private val mediaExtensions = imageExtensions + videoExtensions
        private val downloadKeywords = listOf(
            "download",
            "media",
            "cdn",
            "image",
            "photo",
            "video",
        )
        private val blockedTokens = listOf(
            "favicon",
            "apple-touch-icon",
            "logo",
            "sprite",
            "tracking",
            "analytics",
            "pixel",
            "beacon",
            "thumbnailsite",
            "google",
            "doubleclick",
            ".svg",
        )
        private const val MAX_MEDIA_ITEMS = 20
    }
}
