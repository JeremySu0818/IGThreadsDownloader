package com.jeremysu0818.igthreadsdownloader.domain.resolver

sealed class ResolverError(open val detail: String? = null) {
    data class InvalidUrl(override val detail: String? = null) : ResolverError(detail)

    data class UnsupportedPlatform(override val detail: String? = null) : ResolverError(detail)

    data class PrivateContent(override val detail: String? = null) : ResolverError(detail)

    data class LoginRequired(override val detail: String? = null) : ResolverError(detail)

    data class ContentNotFound(override val detail: String? = null) : ResolverError(detail)

    data class RateLimited(override val detail: String? = null) : ResolverError(detail)

    data class HtmlStructureChanged(
        val platform: String,
        override val detail: String? = null,
    ) : ResolverError(detail)

    data class Network(override val detail: String? = null) : ResolverError(detail)

    data class Http(
        val statusCode: Int,
        override val detail: String? = null,
    ) : ResolverError(detail)

    data class EmptyMedia(override val detail: String? = null) : ResolverError(detail)

    fun userMessage(): String = when (this) {
        is InvalidUrl -> "連結格式無效，請貼上完整的 Instagram 或 Threads 貼文連結。"
        is UnsupportedPlatform -> "目前只支援 Instagram 與 Threads 的公開貼文連結。"
        is PrivateContent -> "這是私人內容，公開頁面無法取得媒體。"
        is LoginRequired -> "這則內容需要登入才能查看；本 App 不會要求或儲存 Instagram 帳密。"
        is ContentNotFound -> "內容不存在、已刪除，或連結已失效。"
        is RateLimited -> "服務目前限制請求頻率，請稍後再試。"
        is HtmlStructureChanged -> {
            if (platform == "threads") {
                "Threads 解析頁結構已變更，目前找不到可下載媒體。"
            } else {
                "Instagram 公開頁面結構已變更，目前找不到可下載媒體。"
            }
        }
        is Network -> "網路連線失敗，請確認連線後重試。"
        is Http -> "解析服務回傳錯誤（HTTP $statusCode），請稍後再試。"
        is EmptyMedia -> "解析完成但沒有取得有效媒體，未建立下載。"
    }

    companion object {
        fun fromHttpStatus(
            statusCode: Int,
            responseText: String = "",
            platform: String,
        ): ResolverError {
            val body = responseText.lowercase()
            return when {
                statusCode == 404 || statusCode == 410 -> ContentNotFound()
                statusCode == 429 -> RateLimited()
                statusCode == 401 -> LoginRequired()
                statusCode == 403 && ("private" in body || "not authorized" in body) -> PrivateContent()
                statusCode == 403 -> LoginRequired()
                statusCode in 500..599 -> Http(statusCode, "$platform resolver server error")
                else -> Http(statusCode)
            }
        }
    }
}
