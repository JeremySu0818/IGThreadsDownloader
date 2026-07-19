package com.jeremysu0818.igthreadsdownloader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jeremysu0818.igthreadsdownloader.AppGraph
import com.jeremysu0818.igthreadsdownloader.data.resolver.UrlNormalizer
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadRecord
import com.jeremysu0818.igthreadsdownloader.domain.download.DownloadStatus
import com.jeremysu0818.igthreadsdownloader.domain.model.MediaManifest
import com.jeremysu0818.igthreadsdownloader.domain.resolver.ResolverResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainTab {
    DOWNLOAD,
    QUEUE,
    HISTORY,
}

data class MainUiState(
    val tab: MainTab = MainTab.DOWNLOAD,
    val input: String = "",
    val isResolving: Boolean = false,
    val manifest: MediaManifest? = null,
    val selectedIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val records: List<DownloadRecord> = emptyList(),
)

class MainViewModel : ViewModel() {
    private val resolverRepository = AppGraph.resolverRepository
    private val downloadRepository = AppGraph.downloadRepository
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var lastAutomaticUrl: String? = null

    init {
        viewModelScope.launch {
            downloadRepository.records.collect { records ->
                _state.update { it.copy(records = records) }
            }
        }
    }

    fun selectTab(tab: MainTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun updateInput(value: String) {
        _state.update {
            it.copy(
                input = value,
                errorMessage = null,
                noticeMessage = null,
            )
        }
    }

    fun parse() {
        parseText(_state.value.input)
    }

    fun receiveExternalText(
        text: String,
        autoResolve: Boolean,
    ) {
        val url = UrlNormalizer.extractSupportedUrl(text)
        if (url == null) {
            if (text.isNotBlank() && autoResolve) {
                _state.update {
                    it.copy(errorMessage = "分享或剪貼簿內容沒有可支援的 Instagram / Threads 貼文連結。")
                }
            }
            return
        }
        if (autoResolve && url == lastAutomaticUrl) return
        lastAutomaticUrl = if (autoResolve) url else lastAutomaticUrl
        _state.update {
            it.copy(
                tab = MainTab.DOWNLOAD,
                input = url,
                noticeMessage = if (autoResolve) "已擷取連結，正在解析公開內容。" else null,
            )
        }
        if (autoResolve) parseText(url)
    }

    fun toggleSelection(id: String) {
        _state.update { current ->
            val selected = current.selectedIds.toMutableSet()
            if (!selected.add(id)) selected.remove(id)
            current.copy(selectedIds = selected, errorMessage = null)
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { current ->
            current.copy(
                selectedIds = if (selected) {
                    current.manifest?.items?.map { it.id }?.toSet().orEmpty()
                } else {
                    emptySet()
                },
            )
        }
    }

    fun downloadSelected() {
        val current = _state.value
        val manifest = current.manifest ?: return
        val items = manifest.items.filter { it.id in current.selectedIds }
        if (items.isEmpty()) {
            _state.update { it.copy(errorMessage = "請至少選取一個媒體項目。") }
            return
        }
        viewModelScope.launch {
            val records = downloadRepository.enqueue(manifest, items)
            val accepted = records.count { it.status != DownloadStatus.FAILED }
            val failed = records.size - accepted
            _state.update {
                it.copy(
                    tab = MainTab.QUEUE,
                    noticeMessage = buildString {
                        if (accepted > 0) append("已建立 $accepted 個真實下載工作。")
                        if (failed > 0) {
                            if (isNotEmpty()) append(' ')
                            append("$failed 個項目無法下載，請查看紀錄。")
                        }
                    },
                    errorMessage = null,
                )
            }
        }
    }

    fun cancel(managerId: Long) {
        viewModelScope.launch { downloadRepository.cancel(managerId) }
    }

    fun retry(managerId: Long) {
        viewModelScope.launch {
            val record = downloadRepository.retry(managerId)
            _state.update {
                it.copy(
                    tab = MainTab.QUEUE,
                    noticeMessage = if (record != null) "已重新建立下載工作。" else "找不到可重試的項目。",
                )
            }
        }
    }

    fun delete(managerId: Long) {
        viewModelScope.launch {
            val deleted = downloadRepository.delete(managerId)
            _state.update {
                it.copy(noticeMessage = if (deleted) "檔案與歷史紀錄已刪除。" else "歷史紀錄已移除。")
            }
        }
    }

    fun open(record: DownloadRecord) {
        if (!downloadRepository.open(record)) {
            _state.update { it.copy(errorMessage = "找不到可開啟此檔案的 App，或檔案已不存在。") }
        }
    }

    fun share(record: DownloadRecord) {
        if (!downloadRepository.share(record)) {
            _state.update { it.copy(errorMessage = "無法分享此檔案；檔案可能已被移除。") }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(errorMessage = null, noticeMessage = null) }
    }

    private fun parseText(text: String) {
        if (text.isBlank()) {
            _state.update { it.copy(errorMessage = "請先貼上連結。") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isResolving = true,
                    manifest = null,
                    selectedIds = emptySet(),
                    errorMessage = null,
                )
            }
            when (val result = resolverRepository.resolve(text)) {
                is ResolverResult.Success -> {
                    _state.update {
                        it.copy(
                            isResolving = false,
                            input = result.manifest.sourceUrl,
                            manifest = result.manifest,
                            selectedIds = result.manifest.items.map { item -> item.id }.toSet(),
                            errorMessage = null,
                            noticeMessage = if (result.manifest.isPartial) {
                                "公開頁面只提供部分項目，畫面已清楚標示。"
                            } else {
                                "已取得 ${result.manifest.items.size} 個真實媒體項目。"
                            },
                        )
                    }
                }
                is ResolverResult.Failure -> {
                    _state.update {
                        it.copy(
                            isResolving = false,
                            manifest = null,
                            selectedIds = emptySet(),
                            errorMessage = result.error.userMessage(),
                            noticeMessage = null,
                        )
                    }
                }
            }
        }
    }
}
