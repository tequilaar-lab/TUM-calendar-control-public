/*
 * Aufgabe: synthetische Regressionstests für ReadOnlyIcalImportService.
 * Ein-/Ausgabe: künstliche Zustände und Fixtures -> JUnit-Prüfergebnisse.
 * Nebenwirkungen: nur lokaler Testprozess und gegebenenfalls Robolectric-Präferenzen.
 * Nicht zuständig: echte Konten, private Quellen, Geräte oder reale Kalenderausgaben.
 */
package com.tequila.tumcalendarcontrol.sync

import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadOnlyIcalImportServiceTest {
    @Test
    fun `获取器拒绝非HTTPS且不调用传输`() {
        var calls = 0
        val fetcher = HttpsIcalSourceFetcher(transport = FakeTransport { calls += 1; IcalHttpResponse(200, ByteArray(0)) })

        val result = fetcher.fetch("http://synthetic.example.test/calendar")

        assertEquals(IcalFetchResult.Failure("只允许 HTTPS 私有订阅地址"), result)
        assertEquals(0, calls)
    }

    @Test
    fun `获取器覆盖成功 状态码 超限与传输失败`() {
        val success = HttpsIcalSourceFetcher(transport = FakeTransport { IcalHttpResponse(200, "BEGIN:VCALENDAR".toByteArray()) })
        assertEquals(IcalFetchResult.Success("BEGIN:VCALENDAR"), success.fetch("https://synthetic.example.test/calendar"))

        val status = HttpsIcalSourceFetcher(transport = FakeTransport { IcalHttpResponse(503, ByteArray(0)) })
        assertEquals(IcalFetchResult.Failure("来源服务器未成功响应"), status.fetch("https://synthetic.example.test/calendar"))

        val oversized = HttpsIcalSourceFetcher(maxBytes = 3, transport = FakeTransport { IcalHttpResponse(200, ByteArray(4)) })
        assertEquals(IcalFetchResult.Failure("来源响应超过大小限制"), oversized.fetch("https://synthetic.example.test/calendar"))

        val failing = HttpsIcalSourceFetcher(transport = FakeTransport { error("synthetic timeout") })
        assertEquals(IcalFetchResult.Failure("来源读取失败"), failing.fetch("https://synthetic.example.test/calendar"))
    }

    @Test
    fun `服务汇总全部来源并按 UID 生成本机课程目录`() {
        val service = ReadOnlyIcalImportService(
            fetcher = HttpsIcalSourceFetcher(transport = FakeTransport { IcalHttpResponse(200, syntheticCalendar().toByteArray()) }),
            clock = Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneOffset.UTC),
        )

        val result = service.preview("https://synthetic.example.test/calendar")

        assertTrue(result is IcalReadOnlyImportResult.Success)
        val preview = (result as IcalReadOnlyImportResult.Success).preview
        assertEquals(2, preview.sourceEventCount)
        assertEquals(2, preview.manageableOccurrenceCount)
        assertTrue(preview.skippedByReason.isEmpty())
        assertEquals(Instant.parse("2026-09-14T08:00:00Z"), preview.firstManageableStart)
        assertEquals(2, preview.courses.size)
    }

    @Test
    fun `服务将无效内容归为脱敏解析失败`() {
        val service = ReadOnlyIcalImportService(
            fetcher = HttpsIcalSourceFetcher(transport = FakeTransport { IcalHttpResponse(200, "not an ical".toByteArray()) }),
        )

        assertEquals(
            IcalReadOnlyImportResult.Failure("来源内容无法作为 iCal 日历解析"),
            service.preview("https://synthetic.example.test/calendar"),
        )
    }

    private class FakeTransport(private val response: (URI) -> IcalHttpResponse) : IcalHttpTransport {
        override fun get(uri: URI, connectTimeoutMillis: Int, readTimeoutMillis: Int, maxBytes: Int): IcalHttpResponse = response(uri)
    }

    private fun syntheticCalendar(): String = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:confirmed
        DTSTART:20260914T080000Z
        DTEND:20260914T100000Z
        SUMMARY:绝不应出现在预览中的合成标题
        END:VEVENT
        BEGIN:VEVENT
        UID:unconfirmed
        DTSTART:20260915T080000Z
        DTEND:20260915T100000Z
        SUMMARY:另一条合成标题
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()
}
