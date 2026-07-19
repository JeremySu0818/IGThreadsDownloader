package com.jeremysu0818.igthreadsdownloader

import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverError
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverErrorTest {
    @Test
    fun httpStatusMapsToSpecificErrors() {
        assertTrue(
            ResolverError.fromHttpStatus(404, "", "instagram") is ResolverError.ContentNotFound,
        )
        assertTrue(
            ResolverError.fromHttpStatus(429, "", "threads") is ResolverError.RateLimited,
        )
        assertTrue(
            ResolverError.fromHttpStatus(403, "private media", "instagram") is
                ResolverError.PrivateContent,
        )
        assertTrue(
            ResolverError.fromHttpStatus(401, "", "instagram") is ResolverError.LoginRequired,
        )
    }
}
