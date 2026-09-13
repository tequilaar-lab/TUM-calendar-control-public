/*
 * 职责：在用户明确触发后，以受限 HTTPS GET 读取 iCal 文本。
 * 输入／输出：输入私有 URL；输出文本或不含 URL/响应内容的失败代码。
 * 外部副作用：仅发生一次 HTTPS 只读请求；不保存响应、不写日历。
 * 不负责：解析、Fixplatz 决策、重试、账户授权或任何日历写入。
 */
package com.tequila.tumcalendarcontrol.sync

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

sealed interface IcalFetchResult {
    data class Success(val text: String) : IcalFetchResult
    data class Failure(val reason: String) : IcalFetchResult
}

/** 可替换的 HTTPS 传输边界，使网络错误可以在 JVM 合成测试中复现。 */
interface IcalHttpTransport {
    fun get(uri: URI, connectTimeoutMillis: Int, readTimeoutMillis: Int, maxBytes: Int): IcalHttpResponse
}

data class IcalHttpResponse(val statusCode: Int, val body: ByteArray)

class HttpsIcalSourceFetcher(
    private val connectTimeoutMillis: Int = 12_000,
    private val readTimeoutMillis: Int = 20_000,
    private val maxBytes: Int = 2_000_000,
    private val transport: IcalHttpTransport = UrlConnectionIcalHttpTransport(),
) {
    fun fetch(urlText: String): IcalFetchResult {
        val uri = runCatching { URI(urlText) }.getOrNull()
            ?: return IcalFetchResult.Failure("地址格式无效")
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return IcalFetchResult.Failure("只允许 HTTPS 私有订阅地址")
        val response = runCatching { transport.get(uri, connectTimeoutMillis, readTimeoutMillis, maxBytes) }
            .getOrElse { return IcalFetchResult.Failure("来源读取失败") }
        if (response.statusCode !in 200..299) return IcalFetchResult.Failure("来源服务器未成功响应")
        if (response.body.size > maxBytes) return IcalFetchResult.Failure("来源响应超过大小限制")
        return IcalFetchResult.Success(response.body.toString(Charsets.UTF_8))
    }
}

/** 生产传输仅执行单次、无重定向的 HTTPS GET；调用者绝不记录 URI 或响应。 */
private class UrlConnectionIcalHttpTransport : IcalHttpTransport {
    override fun get(uri: URI, connectTimeoutMillis: Int, readTimeoutMillis: Int, maxBytes: Int): IcalHttpResponse {
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connect()
            val statusCode = connection.responseCode
            val body = if (statusCode in 200..299) {
                connection.inputStream.use { input -> input.readBounded(maxBytes) }
            } else {
                ByteArray(0)
            }
            return IcalHttpResponse(statusCode, body)
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw IllegalArgumentException("response too large")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
