package com.jeremysu0818.igthreadsdownloader

import com.jeremysu0818.igthreadsdownloader.data.resolver.InstagramPublicResolver
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaItemType
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in integration check for Instagram's current logged-out post query.
 *
 * Run with:
 * RUN_LIVE_RESOLVER_TESTS=true ./gradlew testDebugUnitTest
 */
class InstagramLiveValidationTest {
    @Test
    fun publicReelReturnsDirectVideo() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_RESOLVER_TESTS") == "true")
        val sourceUrl = System.getenv("INSTAGRAM_LIVE_TEST_URL")
            ?: "https://www.instagram.com/reel/DSBv_erDe7H/"

        val result = InstagramPublicResolver().resolve(sourceUrl)

        assertTrue(result is ResolverResult.Success)
        val items = (result as ResolverResult.Success).manifest.items
        assertTrue(items.isNotEmpty())
        assertTrue(items.any { it.type == MediaItemType.VIDEO })
        assertTrue(items.all { it.downloadUrl.startsWith("https://") })
        assertTrue(items.none { "profile_pic" in it.downloadUrl || "reel-cover" in it.downloadUrl })
    }

    @Test
    fun publicPhotoReturnsDirectImage() = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE_RESOLVER_TESTS") == "true")
        val sourceUrl = System.getenv("INSTAGRAM_PHOTO_LIVE_TEST_URL")
            ?: "https://www.instagram.com/p/DT_GwSbkpO6/"

        val result = InstagramPublicResolver().resolve(sourceUrl)

        assertTrue(result is ResolverResult.Success)
        val items = (result as ResolverResult.Success).manifest.items
        assertTrue(items.isNotEmpty())
        assertTrue(items.all { it.type == MediaItemType.IMAGE })
        assertTrue(items.all { it.downloadUrl.startsWith("https://") })
        assertTrue(items.none { "profile_pic" in it.downloadUrl })
    }
}
