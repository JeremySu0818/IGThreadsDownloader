package com.jeremysu0818.igthreadsdownloader.domain.resolver

import com.jeremysu0818.igthreadsdownloader.domain.model.MediaManifest

interface MediaResolver {
    fun supports(url: String): Boolean

    suspend fun resolve(url: String): ResolverResult
}

sealed interface ResolverResult {
    data class Success(val manifest: MediaManifest) : ResolverResult

    data class Failure(val error: ResolverError) : ResolverResult
}
