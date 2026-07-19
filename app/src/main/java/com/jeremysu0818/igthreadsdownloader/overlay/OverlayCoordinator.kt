package com.jeremysu0818.igthreadsdownloader.overlay

import com.jeremysu0818.igthreadsdownloader.data.download.AndroidDownloadRepository
import com.jeremysu0818.igthreadsdownloader.data.resolver.ResolverRepository
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadStatus
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaManifest
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OverlayPhase {
    IDLE,
    RESOLVING,
    READY,
    DOWNLOADING,
    ERROR,
}

data class OverlayState(
    val phase: OverlayPhase = OverlayPhase.IDLE,
    val detectedUrl: String? = null,
    val manifest: MediaManifest? = null,
    val errorMessage: String? = null,
    val activeDownloadIds: Set<Long> = emptySet(),
)

class OverlayCoordinator(
    private val resolverRepository: ResolverRepository,
    private val downloadRepository: AndroidDownloadRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    fun showError(message: String) {
        _state.update {
            it.copy(
                phase = OverlayPhase.ERROR,
                detectedUrl = null,
                manifest = null,
                errorMessage = message,
            )
        }
    }

    fun resolve(url: String) {
        if (
            _state.value.phase == OverlayPhase.RESOLVING &&
            _state.value.detectedUrl == url
        ) {
            return
        }
        scope.launch {
            _state.update {
                it.copy(
                    phase = OverlayPhase.RESOLVING,
                    detectedUrl = url,
                    manifest = null,
                    errorMessage = null,
                )
            }
            when (val result = resolverRepository.resolve(url)) {
                is ResolverResult.Success -> {
                    _state.update {
                        it.copy(
                            phase = OverlayPhase.READY,
                            detectedUrl = result.manifest.sourceUrl,
                            manifest = result.manifest,
                            errorMessage = null,
                        )
                    }
                }
                is ResolverResult.Failure -> {
                    _state.update {
                        it.copy(
                            phase = OverlayPhase.ERROR,
                            manifest = null,
                            errorMessage = result.error.userMessage(),
                        )
                    }
                }
            }
        }
    }

    fun download(selectedIds: Set<String>) {
        val manifest = _state.value.manifest ?: return
        val selected = manifest.items.filter { it.id in selectedIds }
        if (selected.isEmpty()) {
            _state.update {
                it.copy(phase = OverlayPhase.ERROR, errorMessage = "請至少選取一個媒體項目。")
            }
            return
        }
        scope.launch {
            val records = downloadRepository.enqueue(manifest, selected)
            val ids = records.map { it.managerId }.toSet()
            val allFailedImmediately = records.isNotEmpty() &&
                records.all { it.status == DownloadStatus.FAILED }
            if (allFailedImmediately) {
                _state.update {
                    it.copy(
                        phase = OverlayPhase.ERROR,
                        errorMessage = records.firstNotNullOfOrNull { record ->
                            record.statusMessage
                        } ?: "無法建立下載。",
                        activeDownloadIds = emptySet(),
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    phase = OverlayPhase.DOWNLOADING,
                    errorMessage = null,
                    activeDownloadIds = ids,
                )
            }
            val completed = downloadRepository.records.first { all ->
                val relevant = all.filter { it.managerId in ids }
                relevant.size == ids.size && relevant.none { it.isActive }
            }.filter { it.managerId in ids }
            val failure = completed.firstOrNull { it.status == DownloadStatus.FAILED }
            _state.update {
                it.copy(
                    phase = if (failure == null) OverlayPhase.READY else OverlayPhase.ERROR,
                    errorMessage = failure?.statusMessage,
                    activeDownloadIds = emptySet(),
                )
            }
        }
    }

    fun clearError() {
        _state.update {
            it.copy(
                phase = if (it.manifest != null) OverlayPhase.READY else OverlayPhase.IDLE,
                errorMessage = null,
            )
        }
    }
}
