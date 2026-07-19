package com.jeremysu0818.igthreadsdownloader.overlay

import android.content.Context
import androidx.core.content.edit
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
    DETECTED,
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
    val paused: Boolean = false,
    val targetAppForeground: Boolean = false,
    val activeDownloadIds: Set<Long> = emptySet(),
)

class OverlayCoordinator(
    context: Context,
    private val resolverRepository: ResolverRepository,
    private val downloadRepository: AndroidDownloadRepository,
) {
    private val preferences =
        context.getSharedPreferences("overlay_preferences", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(
        OverlayState(paused = preferences.getBoolean(KEY_PAUSED, false)),
    )
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    fun onTargetAppForeground(isForeground: Boolean) {
        _state.update { current ->
            current.copy(
                targetAppForeground = isForeground,
                phase = if (
                    !isForeground &&
                    current.phase in setOf(OverlayPhase.IDLE, OverlayPhase.DETECTED)
                ) {
                    OverlayPhase.IDLE
                } else {
                    current.phase
                },
            )
        }
    }

    fun onDetectedUrl(url: String) {
        val current = _state.value
        if (current.paused || current.detectedUrl == url) return
        _state.value = current.copy(
            phase = OverlayPhase.DETECTED,
            detectedUrl = url,
            manifest = null,
            errorMessage = null,
        )
    }

    fun resolveCurrent() {
        _state.value.detectedUrl?.let(::resolve)
    }

    fun resolve(url: String) {
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

    fun togglePaused() {
        val paused = !_state.value.paused
        preferences.edit { putBoolean(KEY_PAUSED, paused) }
        _state.update {
            it.copy(
                paused = paused,
                phase = if (paused) OverlayPhase.IDLE else it.phase,
                errorMessage = null,
            )
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

    companion object {
        private const val KEY_PAUSED = "detection_paused"
    }
}
