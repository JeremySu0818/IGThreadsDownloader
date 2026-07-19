package com.jeremysu0818.igthreadsdownloader

import com.jeremysu0818.igthreadsdownloader.data.resolver.ThreadsHtmlResolver
import com.jeremysu0818.igthreadsdownloader.domain.model.ManifestType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverError
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadsHtmlResolverTest {
    private val resolver = ThreadsHtmlResolver()
    private val sourceUrl =
        "https://www.threads.com/@jere.my0818/post/Da-qUPhgayG" +
            "?xmt=AQG0z-e4gEeGf__WPzUsgnz4MxUrverhOKD5A5WpmlY4jQ"

    @Test
    fun endpointUsesEncodedQueryParameter() {
        val endpoint = resolver.buildEndpoint(sourceUrl)

        assertEquals(sourceUrl, endpoint.queryParameter("url"))
        assertTrue(endpoint.toString().contains("url=https%3A%2F%2Fwww.threads.com%2F%40"))
        assertFalse(endpoint.toString().contains("url=https://"))
    }

    @Test
    fun fixtureExtractsAllImageVideoAndDownloadLinksAndFiltersUiAssets() {
        val html = checkNotNull(
            javaClass.classLoader?.getResource("fixtures/threads_download.html"),
        ).readText()

        val result = resolver.parseHtml(
            sourceUrl = sourceUrl,
            html = html,
            baseUrl = "https://cdn.threadsphotodownloader.com/result/",
            cookieHeader = "NEXT_LOCALE=en",
        )

        assertTrue(result is ResolverResult.Success)
        val manifest = (result as ResolverResult.Success).manifest
        assertEquals(ManifestType.CAROUSEL, manifest.type)
        assertEquals(4, manifest.items.size)
        assertEquals(3, manifest.items.count { it.type == MediaItemType.IMAGE })
        assertEquals(1, manifest.items.count { it.type == MediaItemType.VIDEO })
        assertTrue(manifest.items.all { it.requestHeaders["Cookie"] == "NEXT_LOCALE=en" })
        assertTrue(manifest.items.none { "logo" in it.downloadUrl || "thumbnailsite" in it.downloadUrl })
        assertTrue(manifest.items.any { "photo-one.webp" in it.downloadUrl })
        assertTrue(manifest.items.any { "photo-three.avif" in it.downloadUrl && "&y=2" in it.downloadUrl })
    }

    @Test
    fun changedHtmlReturnsTypedErrorInsteadOfCrashing() {
        val result = resolver.parseHtml(
            sourceUrl = sourceUrl,
            html = "<html><body><p>No result component</p></body></html>",
        )

        assertTrue(result is ResolverResult.Failure)
        assertTrue(
            (result as ResolverResult.Failure).error is ResolverError.HtmlStructureChanged,
        )
    }
}
