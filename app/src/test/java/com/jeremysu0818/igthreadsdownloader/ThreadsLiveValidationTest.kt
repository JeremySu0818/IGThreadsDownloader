package com.jeremysu0818.igthreadsdownloader

import com.jeremysu0818.igthreadsdownloader.data.resolver.ThreadsHtmlResolver
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in integration check for the confirmed production endpoint.
 *
 * Run with:
 * RUN_LIVE_RESOLVER_TESTS=true ./gradlew testDebugUnitTest
 */
class ThreadsLiveValidationTest {
    @Test
    fun confirmedThreadsUrlReturnsRealMedia() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_RESOLVER_TESTS") == "true")
        val sourceUrl =
            "https://www.threads.com/@jere.my0818/post/Da-qUPhgayG" +
                "?xmt=AQG0z-e4gEeGf__WPzUsgnz4MxUrverhOKD5A5WpmlY4jQ"

        val result = ThreadsHtmlResolver().resolve(sourceUrl)

        assertTrue(result is ResolverResult.Success)
        val items = (result as ResolverResult.Success).manifest.items
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.downloadUrl.startsWith("https://") })
        assertTrue(items.none { "thumbnailsite" in it.downloadUrl || "favicon" in it.downloadUrl })
    }
}
