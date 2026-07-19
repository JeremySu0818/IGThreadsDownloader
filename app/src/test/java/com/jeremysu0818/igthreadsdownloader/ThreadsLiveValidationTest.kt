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
            "https://www.threads.com/@jiangmingyuan9/post/Da9u2HzlAaT"

        val result = ThreadsHtmlResolver().resolve(sourceUrl)

        assertTrue("Expected success but got $result", result is ResolverResult.Success)
        val items = (result as ResolverResult.Success).manifest.items
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.downloadUrl.startsWith("https://") })
        assertTrue(items.none { "thumbnailsite" in it.downloadUrl || "favicon" in it.downloadUrl })
    }
}
