/*
 * Aufgabe: Nach ausdrücklicher Aktion iCal über begrenzten HTTPS-GET lesen.
 * Ein-/Ausgabe: Private HTTPS-URL -> begrenzter iCal-Text oder bereinigter Lesefehler.
 * Nebenwirkungen: Ein begrenzter HTTPS-GET nach ausdrücklichem Auftrag; keine URL-/Antwortprotokollierung.
 * Nicht zuständig: Parsing, Fixplatz-Entscheidungen, Wiederholungsversuche, Kontoautorisierung oder Kalenderausgabe.
 */
package com.tequila.tumcalendarcontrol.sync

import com.tequila.tumcalendarcontrol.i18n.message

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

sealed interface IcalFetchResult {
    data class Success(val text: String) : IcalFetchResult
    data class Failure(val reason: String) : IcalFetchResult
}

/** Austauschbare HTTPS-Grenze für reproduzierbare Netzwerkfehler in synthetischen JVM-Tests. */
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
            ?: return IcalFetchResult.Failure(message(79))
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return IcalFetchResult.Failure(message(80))
        val response = runCatching { transport.get(uri, connectTimeoutMillis, readTimeoutMillis, maxBytes) }
            .getOrElse { return IcalFetchResult.Failure(message(81)) }
        if (response.statusCode !in 200..299) return IcalFetchResult.Failure(message(82))
        if (response.body.size > maxBytes) return IcalFetchResult.Failure(message(83))
        return IcalFetchResult.Success(response.body.toString(Charsets.UTF_8))
    }
}

/** Produktiv nur einzelner HTTPS-GET ohne Umleitung; URI und Antwort niemals protokollieren. */
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
