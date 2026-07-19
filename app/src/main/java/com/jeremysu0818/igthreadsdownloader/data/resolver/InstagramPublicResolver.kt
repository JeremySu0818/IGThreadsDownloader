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
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.Locale

class InstagramPublicResolver(
    private val client: OkHttpClient = defaultResolverClient,
) : MediaResolver {
    override fun supports(url: String): Boolean = UrlNormalizer.normalizeInstagram(url) != null

    override suspend fun resolve(url: String): ResolverResult = withContext(Dispatchers.IO) {
        val normalized = UrlNormalizer.normalizeInstagram(url)
            ?: return@withContext ResolverResult.Failure(ResolverError.InvalidUrl())
        val request = Request.Builder()
            .url(normalized.normalizedUrl)
            .get()
            .header("User-Agent", ANDROID_CHROME_USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            )
            .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            .header("Referer", INSTAGRAM_BASE_URL)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext ResolverResult.Failure(
                        ResolverError.fromHttpStatus(
                            statusCode = response.code,
                            responseText = responseText,
                            platform = "instagram",
                        ),
                    )
                }
                if (response.request.url.encodedPath.startsWith("/accounts/login")) {
                    return@withContext ResolverResult.Failure(ResolverError.LoginRequired())
                }
                val cookies = responseCookieList(response.headers, response.request.url)
                val cookieHeader = cookies
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("; ") { "${it.name}=${it.value}" }
                when (
                    val parsed = parseHtml(
                        sourceUrl = normalized.normalizedUrl,
                        html = responseText,
                        cookieHeader = cookieHeader,
                    )
                ) {
                    is ResolverResult.Success -> {
                        val enriched = client.enrichMediaMetadata(parsed.manifest.items)
                        ResolverResult.Success(parsed.manifest.copy(items = enriched))
                    }
                    is ResolverResult.Failure -> {
                        if (parsed.error !is ResolverError.HtmlStructureChanged &&
                            parsed.error !is ResolverError.LoginRequired
                        ) {
                            parsed
                        } else {
                            resolveGraphQlFallback(
                                sourceUrl = normalized.normalizedUrl,
                                shortcode = normalized.shortcode,
                                html = responseText,
                                cookies = cookies,
                                cookieHeader = cookieHeader,
                                originalFailure = parsed,
                            )
                        }
                    }
                }
            }
        } catch (error: IOException) {
            ResolverResult.Failure(ResolverError.Network(error.message))
        } catch (error: RuntimeException) {
            ResolverResult.Failure(
                ResolverError.HtmlStructureChanged("instagram", error.message),
            )
        }
    }

    private suspend fun resolveGraphQlFallback(
        sourceUrl: String,
        shortcode: String,
        html: String,
        cookies: List<Cookie>,
        cookieHeader: String?,
        originalFailure: ResolverResult.Failure,
    ): ResolverResult {
        val lsd = extractLsdToken(html) ?: return originalFailure
        val csrfToken = cookies.firstOrNull { it.name == "csrftoken" }?.value
            ?: return originalFailure
        val variables = JSONObject()
            .put("shortcode", shortcode)
            .put(
                "__relay_internal__pv__PolarisMultiCaptionCarouselEnabledrelayprovider",
                false,
            )
            .toString()
        val body = FormBody.Builder()
            .add("doc_id", POST_ROOT_QUERY_DOCUMENT_ID)
            .add("variables", variables)
            .add("lsd", lsd)
            .build()
        val request = Request.Builder()
            .url(INSTAGRAM_GRAPHQL_URL)
            .post(body)
            .header("User-Agent", ANDROID_CHROME_USER_AGENT)
            .header("Accept", "application/json")
            .header("Accept-Language", "zh-TW,zh;q=0.9,en;q=0.8")
            .header("Referer", sourceUrl)
            .header("X-IG-App-ID", INSTAGRAM_WEB_APP_ID)
            .header("X-ASBD-ID", INSTAGRAM_ASBD_ID)
            .header("X-FB-LSD", lsd)
            .header("X-CSRFToken", csrfToken)
            .header("X-Requested-With", "XMLHttpRequest")
            .apply {
                cookieHeader?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }
            .build()

        return client.newCall(request).execute().use { response ->
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 429) {
                    ResolverResult.Failure(ResolverError.RateLimited())
                } else {
                    originalFailure
                }
            } else {
                when (
                    val parsed = parseGraphQlPayload(
                        sourceUrl = sourceUrl,
                        payload = payload,
                        cookieHeader = cookieHeader,
                    )
                ) {
                    is ResolverResult.Failure -> originalFailure
                    is ResolverResult.Success -> {
                        val enriched = client.enrichMediaMetadata(parsed.manifest.items)
                        ResolverResult.Success(parsed.manifest.copy(items = enriched))
                    }
                }
            }
        }
    }

    internal fun parseGraphQlPayload(
        sourceUrl: String,
        payload: String,
        cookieHeader: String? = null,
    ): ResolverResult {
        val normalized = UrlNormalizer.normalizeInstagram(sourceUrl)
            ?: return ResolverResult.Failure(ResolverError.InvalidUrl())
        val root = runCatching { JSONObject(payload) }.getOrNull()
            ?: return ResolverResult.Failure(
                ResolverError.HtmlStructureChanged("instagram", "invalid GraphQL response"),
            )
        val items = root.optJSONObject("data")
            ?.optJSONObject("xdt_api__v1__media__shortcode__web_info")
            ?.optJSONArray("items")
        val media = items?.optJSONObject(0)
            ?: return ResolverResult.Failure(
                ResolverError.HtmlStructureChanged("instagram", "GraphQL response has no media"),
            )

        val carousel = media.optJSONArray("carousel_media")
        val nodes = if (carousel != null && carousel.length() > 0) {
            (0 until carousel.length()).mapNotNull(carousel::optJSONObject)
        } else {
            listOf(media)
        }
        val resolved = nodes.mapNotNull(::resolveGraphQlMedia)
            .distinctBy { mediaIdentity(it.url) }
            .take(MAX_MEDIA_ITEMS)
        if (resolved.isEmpty()) {
            return ResolverResult.Failure(
                ResolverError.HtmlStructureChanged("instagram", "GraphQL media URLs are empty"),
            )
        }

        val author = media.optJSONObject("user")
            ?.optString("username")
            ?.takeIf { it.isNotBlank() }
        val caption = media.optJSONObject("caption")
            ?.optString("text")
            ?.takeIf { it.isNotBlank() }
        val requestHeaders = buildMap {
            put("User-Agent", ANDROID_CHROME_USER_AGENT)
            put("Referer", normalized.normalizedUrl)
            cookieHeader?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        }
        val filenames = mutableSetOf<String>()
        val manifestItems = resolved.mapIndexed { index, candidate ->
            val mimeType = inferMimeType(candidate.url, candidate.type)
            val filename = FilenameGenerator.generate(
                platform = MediaPlatform.INSTAGRAM,
                author = author,
                shortcode = normalized.shortcode,
                index = index,
                type = candidate.type,
                mediaUrl = candidate.url,
                mimeType = mimeType,
                occupiedNames = filenames,
            )
            filenames += filename
            MediaItem(
                id = "instagram_${normalized.shortcode}_$index",
                type = candidate.type,
                downloadUrl = candidate.url,
                thumbnailUrl = candidate.thumbnailUrl,
                width = candidate.width,
                height = candidate.height,
                durationMs = null,
                filename = filename,
                contentLength = null,
                mimeType = mimeType,
                requestHeaders = requestHeaders,
            )
        }
        val type = when {
            carousel != null && carousel.length() > 0 -> ManifestType.CAROUSEL
            normalized.type == ManifestType.REEL -> ManifestType.REEL
            manifestItems.first().type == MediaItemType.VIDEO -> ManifestType.VIDEO
            else -> ManifestType.PHOTO
        }
        return ResolverResult.Success(
            MediaManifest(
                platform = MediaPlatform.INSTAGRAM,
                type = type,
                author = author,
                sourceUrl = normalized.normalizedUrl,
                title = null,
                caption = caption,
                thumbnailUrl = resolved.firstOrNull()?.thumbnailUrl,
                items = manifestItems,
                isPartial = false,
                warnings = emptyList(),
            ),
        )
    }

    private fun resolveGraphQlMedia(media: JSONObject): GraphQlMedia? {
        val image = bestImageCandidate(media)
        val videos = media.optJSONArray("video_versions")
        val video = videos?.let { array ->
            (0 until array.length())
                .mapNotNull(array::optJSONObject)
                .filter { it.optString("url").startsWith("http") }
                .maxByOrNull { dimensionsArea(it) }
        }
        return if (video != null) {
            GraphQlMedia(
                url = video.optString("url"),
                type = MediaItemType.VIDEO,
                width = video.optInt("width").takeIf { it > 0 },
                height = video.optInt("height").takeIf { it > 0 },
                thumbnailUrl = image?.optString("url")?.takeIf { it.isNotBlank() },
            )
        } else if (image != null) {
            GraphQlMedia(
                url = image.optString("url"),
                type = MediaItemType.IMAGE,
                width = image.optInt("width").takeIf { it > 0 },
                height = image.optInt("height").takeIf { it > 0 },
                thumbnailUrl = image.optString("url"),
            )
        } else {
            null
        }
    }

    private fun bestImageCandidate(media: JSONObject): JSONObject? {
        val candidates = media.optJSONObject("image_versions2")
            ?.optJSONArray("candidates")
            ?: return null
        return (0 until candidates.length())
            .mapNotNull(candidates::optJSONObject)
            .filter { it.optString("url").startsWith("http") }
            .maxByOrNull { dimensionsArea(it) }
    }

    private fun dimensionsArea(value: JSONObject): Long =
        value.optLong("width").coerceAtLeast(0) *
            value.optLong("height").coerceAtLeast(0)

    private fun extractLsdToken(html: String): String? =
        LSD_TOKEN_PATTERN.find(html)?.groupValues?.getOrNull(1)

    internal fun parseHtml(
        sourceUrl: String,
        html: String,
        cookieHeader: String? = null,
    ): ResolverResult {
        val normalized = UrlNormalizer.normalizeInstagram(sourceUrl)
            ?: return ResolverResult.Failure(ResolverError.InvalidUrl())
        if (html.isBlank()) {
            return ResolverResult.Failure(
                ResolverError.HtmlStructureChanged("instagram", "empty response"),
            )
        }

        val document = Jsoup.parse(html, normalized.normalizedUrl)
        val pageText = document.text().lowercase(Locale.US)
        val rawLower = html.lowercase(Locale.US)
        detectPageError(pageText, rawLower)?.let {
            return ResolverResult.Failure(it)
        }

        val candidates = LinkedHashMap<String, Candidate>()
        val carouselCandidates = LinkedHashMap<String, Candidate>()
        addMetaCandidates(document, candidates)

        var carouselDisclosed = false
        document.select("script").forEach { element ->
            val decoded = decodeEscapedHtmlValue(element.data())
            if (
                "edge_sidecar_to_children" in decoded ||
                "\"carousel_media\"" in decoded ||
                "\"carouselMedia\"" in decoded
            ) {
                carouselDisclosed = true
            }
            extractObjectValues(decoded, "edge_sidecar_to_children").forEach { sidecar ->
                extractObjectValues(sidecar, "node").forEach { node ->
                    val videoUrls = listOf("video_url", "videoUrl")
                        .flatMap { extractKeyedUrls(node, it) }
                    val urls = videoUrls.ifEmpty {
                        listOf("display_url", "displayUrl")
                            .flatMap { extractKeyedUrls(node, it) }
                    }
                    val type = if (videoUrls.isNotEmpty()) {
                        MediaItemType.VIDEO
                    } else {
                        MediaItemType.IMAGE
                    }
                    urls.take(1).forEach { url ->
                        addCandidate(carouselCandidates, url, type, priority = 130)
                    }
                }
            }
            scriptKeys.forEach { (key, type) ->
                extractKeyedUrls(decoded, key).forEach { url ->
                    addCandidate(candidates, url, type, priority = 90)
                }
            }
        }

        document.select("video[src], video source[src], source[src]").forEach {
            addCandidate(candidates, it.mediaSource("src"), MediaItemType.VIDEO, 75)
        }
        document.select("video[poster]").forEach {
            addCandidate(candidates, it.mediaSource("poster"), MediaItemType.IMAGE, 25)
        }
        document.select("img[src], img[data-src]").forEach {
            val attr = if (it.hasAttr("src")) "src" else "data-src"
            val context = "${it.attr("alt")} ${it.className()} ${it.id()}".lowercase(Locale.US)
            if (blockedTokens.none(context::contains)) {
                addCandidate(candidates, it.mediaSource(attr), MediaItemType.IMAGE, 35)
            }
        }

        val allAccepted = candidates.values
            .filter { isAllowedMedia(it.url) }
            .sortedByDescending { it.priority }
            .distinctBy { mediaIdentity(it.url) }
        val videos = allAccepted.filter { it.type == MediaItemType.VIDEO }
        val images = allAccepted.filter { it.type == MediaItemType.IMAGE }
        val verifiedCarouselItems = carouselCandidates.values
            .filter { isAllowedMedia(it.url) }
            .distinctBy { mediaIdentity(it.url) }
        val accepted = when {
            carouselDisclosed && verifiedCarouselItems.isNotEmpty() -> verifiedCarouselItems
            carouselDisclosed -> allAccepted
            videos.isNotEmpty() -> videos
            else -> images
        }.take(MAX_MEDIA_ITEMS)
        if (accepted.isEmpty()) {
            val error = when {
                isLoginWall(pageText, rawLower) -> ResolverError.LoginRequired()
                else -> ResolverError.HtmlStructureChanged("instagram")
            }
            return ResolverResult.Failure(error)
        }

        val author = extractAuthor(document)
        val caption = extractCaption(document)
        val requestHeaders = buildMap {
            put("User-Agent", ANDROID_CHROME_USER_AGENT)
            put("Referer", normalized.normalizedUrl)
            cookieHeader?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        }
        val filenames = mutableSetOf<String>()
        val items = accepted.mapIndexed { index, candidate ->
            val mimeType = inferMimeType(candidate.url, candidate.type)
            val filename = FilenameGenerator.generate(
                platform = MediaPlatform.INSTAGRAM,
                author = author,
                shortcode = normalized.shortcode,
                index = index,
                type = candidate.type,
                mediaUrl = candidate.url,
                mimeType = mimeType,
                occupiedNames = filenames,
            )
            filenames += filename
            MediaItem(
                id = "instagram_${normalized.shortcode}_$index",
                type = candidate.type,
                downloadUrl = candidate.url,
                thumbnailUrl = if (candidate.type == MediaItemType.IMAGE) candidate.url else images.firstOrNull()?.url,
                width = candidate.width,
                height = candidate.height,
                durationMs = null,
                filename = filename,
                contentLength = null,
                mimeType = mimeType,
                requestHeaders = requestHeaders,
            )
        }
        val partial = carouselDisclosed &&
            (verifiedCarouselItems.isEmpty() || items.size <= 1)
        val type = when {
            carouselDisclosed || items.size > 1 -> ManifestType.CAROUSEL
            normalized.type == ManifestType.REEL -> ManifestType.REEL
            items.first().type == MediaItemType.VIDEO -> ManifestType.VIDEO
            else -> ManifestType.PHOTO
        }
        return ResolverResult.Success(
            MediaManifest(
                platform = MediaPlatform.INSTAGRAM,
                type = type,
                author = author,
                sourceUrl = normalized.normalizedUrl,
                title = document.selectFirst("meta[property=og:title]")?.attr("content")
                    ?.takeIf { it.isNotBlank() },
                caption = caption,
                thumbnailUrl = images.firstOrNull()?.url ?: items.firstOrNull()?.thumbnailUrl,
                items = items,
                isPartial = partial,
                warnings = if (partial) {
                    listOf("公開頁面未揭露可驗證的完整輪播結構；僅列出實際解析到的項目，未偽造其餘項目。")
                } else {
                    emptyList()
                },
            ),
        )
    }

    private fun addMetaCandidates(
        document: Document,
        destination: MutableMap<String, Candidate>,
    ) {
        document.select(
            "meta[property=og:video], meta[property=og:video:url], " +
                "meta[property=og:video:secure_url], meta[name=twitter:player:stream]",
        ).forEach {
            addCandidate(destination, it.attr("content"), MediaItemType.VIDEO, 110)
        }
        val width = document.selectFirst("meta[property=og:image:width]")
            ?.attr("content")
            ?.toIntOrNull()
        val height = document.selectFirst("meta[property=og:image:height]")
            ?.attr("content")
            ?.toIntOrNull()
        document.select(
            "meta[property=og:image], meta[property=og:image:secure_url], " +
                "meta[name=twitter:image], meta[name=twitter:image:src]",
        ).forEach {
            addCandidate(destination, it.attr("content"), MediaItemType.IMAGE, 100, width, height)
        }
        document.select("meta[name=twitter:player]").forEach {
            val value = it.attr("content")
            if (hasMediaExtension(value)) {
                addCandidate(destination, value, MediaItemType.VIDEO, 80)
            }
        }
    }

    private fun extractKeyedUrls(script: String, key: String): List<String> {
        val pattern = Regex(
            """"${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"])*)"""",
            RegexOption.IGNORE_CASE,
        )
        return pattern.findAll(script)
            .map { decodeEscapedHtmlValue(it.groupValues[1]) }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .toList()
    }

    private fun extractObjectValues(script: String, key: String): List<String> {
        val keyPattern = Regex(""""${Regex.escape(key)}"\s*:""", RegexOption.IGNORE_CASE)
        return keyPattern.findAll(script).mapNotNull { match ->
            val start = script.indexOf('{', match.range.last + 1)
            if (start < 0) return@mapNotNull null
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until script.length) {
                val char = script[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inString = false
                    }
                } else {
                    when (char) {
                        '"' -> inString = true
                        '{' -> depth += 1
                        '}' -> {
                            depth -= 1
                            if (depth == 0) return@mapNotNull script.substring(start, index + 1)
                        }
                    }
                }
            }
            null
        }.toList()
    }

    private fun extractAuthor(document: Document): String? {
        val title = document.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val fromTitle = Regex("""^@?([A-Za-z0-9._]+)\s+(?:on|•)""")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
        if (!fromTitle.isNullOrBlank()) return fromTitle
        Regex("""@([A-Za-z0-9._]+)""")
            .find(title)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val scripts = document.select("script").joinToString("\n") {
            decodeEscapedHtmlValue(it.data())
        }
        return Regex(
            """"owner"\s*:\s*\{.{0,1000}?"username"\s*:\s*"([A-Za-z0-9._]+)"""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(scripts)?.groupValues?.getOrNull(1)
    }

    private fun extractCaption(document: Document): String? {
        val description = document.selectFirst("meta[property=og:description]")
            ?.attr("content")
            ?.trim()
            .orEmpty()
        return description.takeIf {
            it.isNotBlank() &&
                !it.contains("log in", ignoreCase = true) &&
                !it.startsWith("See Instagram photos", ignoreCase = true)
        }
    }

    private fun detectPageError(pageText: String, rawLower: String): ResolverError? = when {
        privatePhrases.any { it in pageText } -> ResolverError.PrivateContent()
        notFoundPhrases.any { it in pageText } -> ResolverError.ContentNotFound()
        rateLimitPhrases.any { it in pageText } ||
            "\"error_type\":\"rate_limit" in rawLower -> ResolverError.RateLimited()
        else -> null
    }

    private fun isLoginWall(pageText: String, rawLower: String): Boolean =
        loginPhrases.any { it in pageText } ||
            "\"login_required\"" in rawLower ||
            "\"require_login\":true" in rawLower

    private fun Element.mediaSource(attribute: String): String {
        val absolute = absUrl(attribute)
        return decodeEscapedHtmlValue(absolute.ifBlank { attr(attribute) })
    }

    private fun addCandidate(
        destination: MutableMap<String, Candidate>,
        rawUrl: String,
        type: MediaItemType,
        priority: Int,
        width: Int? = null,
        height: Int? = null,
    ) {
        val url = decodeEscapedHtmlValue(rawUrl)
        if (url.toHttpUrlOrNull() == null) return
        val identity = mediaIdentity(url)
        val existing = destination[identity]
        if (existing == null || priority > existing.priority) {
            destination[identity] = Candidate(url, type, priority, width, height)
        }
    }

    private fun isAllowedMedia(url: String): Boolean {
        val parsed = url.toHttpUrlOrNull() ?: return false
        val host = parsed.host.lowercase(Locale.US)
        val path = parsed.encodedPath.lowercase(Locale.US)
        val combined = "$host$path"
        if (blockedTokens.any(combined::contains)) return false
        val instagramCdn = "cdninstagram.com" in host || "fbcdn.net" in host
        return instagramCdn && (hasMediaExtension(url) || path.contains("/v/"))
    }

    private fun hasMediaExtension(url: String): Boolean {
        val path = url.toHttpUrlOrNull()?.encodedPath?.lowercase(Locale.US)
            ?: url.substringBefore('?').lowercase(Locale.US)
        return mediaExtensions.any(path::endsWith)
    }

    private fun responseCookieList(headers: Headers, url: HttpUrl): List<Cookie> =
        Cookie.parseAll(url, headers)

    private data class Candidate(
        val url: String,
        val type: MediaItemType,
        val priority: Int,
        val width: Int? = null,
        val height: Int? = null,
    )

    private data class GraphQlMedia(
        val url: String,
        val type: MediaItemType,
        val width: Int?,
        val height: Int?,
        val thumbnailUrl: String?,
    )

    companion object {
        private const val INSTAGRAM_BASE_URL = "https://www.instagram.com/"
        private const val INSTAGRAM_GRAPHQL_URL = "https://www.instagram.com/graphql/query"
        private const val INSTAGRAM_WEB_APP_ID = "936619743392459"
        private const val INSTAGRAM_ASBD_ID = "129477"
        private const val POST_ROOT_QUERY_DOCUMENT_ID = "27402151082740637"
        private val LSD_TOKEN_PATTERN = Regex(
            """"LSD"\s*,\s*\[\s*]\s*,\s*\{\s*"token"\s*:\s*"([^"]+)"""",
        )
        private val scriptKeys = listOf(
            "video_url" to MediaItemType.VIDEO,
            "videoUrl" to MediaItemType.VIDEO,
            "contentUrl" to MediaItemType.VIDEO,
            "display_url" to MediaItemType.IMAGE,
            "displayUrl" to MediaItemType.IMAGE,
        )
        private val mediaExtensions = listOf(
            ".jpg",
            ".jpeg",
            ".png",
            ".webp",
            ".avif",
            ".mp4",
            ".mov",
            ".m3u8",
        )
        private val blockedTokens = listOf(
            "favicon",
            "logo",
            "sprite",
            "profile_pic",
            "avatar",
            "tracking",
            "pixel",
            "beacon",
            ".svg",
        )
        private val privatePhrases = listOf(
            "this account is private",
            "這是私人帳號",
            "此帳號為私人帳號",
        )
        private val notFoundPhrases = listOf(
            "sorry, this page isn't available",
            "page isn't available",
            "content isn't available",
            "內容無法顯示",
            "頁面無法使用",
        )
        private val rateLimitPhrases = listOf(
            "please wait a few minutes before you try again",
            "too many requests",
            "rate limit",
        )
        private val loginPhrases = listOf(
            "log in to see photos and videos",
            "login to see photos and videos",
            "登入即可查看相片和影片",
        )
        private const val MAX_MEDIA_ITEMS = 20
    }
}
