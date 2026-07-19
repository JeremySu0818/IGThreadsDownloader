package com.jeremysu0818.igthreadsdownloader.data.resolver

import com.jeremysu0818.igthreadsdownloader.domain.resolver.MediaResolver
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverError
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult

class ResolverRepository(
    private val resolvers: List<MediaResolver> = listOf(
        ThreadsHtmlResolver(),
        InstagramPublicResolver(),
    ),
) {
    suspend fun resolve(input: String): ResolverResult {
        val url = UrlNormalizer.extractSupportedUrl(input)
            ?: return if (UrlNormalizer.extractFirstUrl(input) == null) {
                ResolverResult.Failure(ResolverError.InvalidUrl())
            } else {
                ResolverResult.Failure(ResolverError.UnsupportedPlatform())
            }
        val resolver = resolvers.firstOrNull { it.supports(url) }
            ?: return ResolverResult.Failure(ResolverError.UnsupportedPlatform())
        return resolver.resolve(url)
    }
}
