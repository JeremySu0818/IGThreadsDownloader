package com.jeremysu0818.igthreadsdownloader

import com.jeremysu0818.igthreadsdownloader.data.resolver.InstagramPublicResolver
import com.jeremysu0818.igthreadsdownloader.domain.model.ManifestType
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverError
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramPublicResolverTest {
    private val resolver = InstagramPublicResolver()
    private val sourceUrl = "https://www.instagram.com/p/Carousel123/"

    @Test
    fun verifiedSidecarCreatesOneItemPerNodeAndDoesNotDownloadVideoCover() {
        val html = checkNotNull(
            javaClass.classLoader?.getResource("fixtures/instagram_carousel.html"),
        ).readText()

        val result = resolver.parseHtml(sourceUrl, html)

        assertTrue(result is ResolverResult.Success)
        val manifest = (result as ResolverResult.Success).manifest
        assertEquals(ManifestType.CAROUSEL, manifest.type)
        assertEquals("jere.my0818", manifest.author)
        assertEquals(2, manifest.items.size)
        assertEquals(1, manifest.items.count { it.type == MediaItemType.IMAGE })
        assertEquals(1, manifest.items.count { it.type == MediaItemType.VIDEO })
        assertFalse(manifest.isPartial)
        assertTrue(manifest.items.none { "video-cover" in it.downloadUrl })
        assertTrue(manifest.items.none { "/preview/" in it.downloadUrl })
    }

    @Test
    fun visiblePrivatePageReturnsPrivateContent() {
        val result = resolver.parseHtml(
            sourceUrl,
            "<html><body><main>This account is private</main></body></html>",
        )

        assertTrue(result is ResolverResult.Failure)
        assertTrue((result as ResolverResult.Failure).error is ResolverError.PrivateContent)
    }

    @Test
    fun translationStringInsideScriptDoesNotMisclassifyPublicPostAsPrivate() {
        val result = resolver.parseHtml(
            sourceUrl,
            """
            <html><head>
              <meta property="og:image"
                    content="https://scontent.cdninstagram.com/v/media/public.jpg?x=1">
            </head><body><main>Public media</main>
              <script>{"translation":"This account is private"}</script>
            </body></html>
            """.trimIndent(),
        )

        assertTrue(result is ResolverResult.Success)
    }
}
