/*
 * 职责：组合受限 HTTPS 获取与 RFC 5545 解析，产出仅供用户查看的脱敏只读预览摘要。
 * 输入／输出：输入私有来源地址；输出全部来源课程的本机目录与脱敏摘要。
 * 外部副作用：通过获取器产生一次由用户操作触发的 HTTPS 只读请求；不保存原文或日程。
 * 不负责：推断 Fixplatz、向日志显示来源标题/URL、请求日历权限、创建或修改本机/Google 日历。
 */
package com.tequila.tumcalendarcontrol.sync

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit

data class IcalReadOnlyPreview(
    val sourceEventCount: Int,
    val manageableOccurrenceCount: Int,
    val skippedByReason: Map<IcalSkipReason, Int>,
    val firstManageableStart: Instant?,
    val lastManageableEnd: Instant?,
    /** 仅交给内存 UI 显示；不得由偏好、日志或审计文档持久化。 */
    val courses: List<SourceCourse>,
)

sealed interface IcalReadOnlyImportResult {
    data class Success(val preview: IcalReadOnlyPreview) : IcalReadOnlyImportResult
    data class Failure(val message: String) : IcalReadOnlyImportResult
}

class ReadOnlyIcalImportService(
    private val fetcher: HttpsIcalSourceFetcher = HttpsIcalSourceFetcher(),
    private val parser: IcalSourceParser = IcalSourceParser(),
    private val clock: Clock = Clock.systemUTC(),
) {
    /** 只展开历史 120 天到未来 180 天，避免无限 RRULE 占用资源。 */
    fun preview(url: String): IcalReadOnlyImportResult {
        val text = when (val fetched = fetcher.fetch(url)) {
            is IcalFetchResult.Success -> fetched.text
            is IcalFetchResult.Failure -> return IcalReadOnlyImportResult.Failure(fetched.reason)
        }
        val now = clock.instant()
        val parsed = runCatching {
            parser.parse(
                icsText = text,
                windowStart = now.minus(120, ChronoUnit.DAYS),
                windowEnd = now.plus(180, ChronoUnit.DAYS),
            )
        }.getOrElse { return IcalReadOnlyImportResult.Failure("来源内容无法作为 iCal 日历解析") }
        val occurrences = parsed.occurrences
        val courses = SourceCourseCatalog.from(occurrences)
        return IcalReadOnlyImportResult.Success(
            IcalReadOnlyPreview(
                sourceEventCount = occurrences.size + parsed.skipped.size,
                manageableOccurrenceCount = occurrences.size,
                skippedByReason = parsed.skipped.groupingBy { it.reason }.eachCount(),
                firstManageableStart = occurrences.minOfOrNull(SourceCalendarOccurrence::startsAt),
                lastManageableEnd = occurrences.maxOfOrNull(SourceCalendarOccurrence::endsAt),
                courses = courses,
            ),
        )
    }
}
