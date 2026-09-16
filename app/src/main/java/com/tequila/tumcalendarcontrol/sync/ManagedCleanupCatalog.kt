/*
 * 职责：只读补齐应用已记录日程的标题与时间，供旧版记录按课程／日期清理。
 * 输入／输出：明确目标绑定与本机映射；输出带实际元数据的映射及失败数量。
 * 外部副作用：仅调用应用专用目标的只读接口；不创建、更新或删除事件。
 * 不负责：读取其他日历、按标题接管日程、执行清理或自动启动。
 */
package com.tequila.tumcalendarcontrol.sync

data class CleanupCatalogReadResult(val mappings: List<StoredEventMapping>, val checkedCount: Int, val failedCount: Int)

object ManagedCleanupCatalog {
    fun read(mappings: List<StoredEventMapping>, bindings: List<TargetBinding>): CleanupCatalogReadResult {
        var checked = 0
        var failed = 0
        val next = mappings.map { mapping ->
            val binding = bindings.firstOrNull { it.calendar.target == mapping.target } ?: return@map mapping
            runCatching {
                check(binding.gateway.verifyOwnership(binding.calendar))
                val ids = binding.gateway.findEventIdsByIdentity(binding.calendar, mapping.identity)
                if (ids.isEmpty()) { checked += 1; return@runCatching mapping }
                check(ids.size == 1)
                val actual = binding.gateway.readEvent(binding.calendar, ids.single(), mapping.identity)
                    ?: error("暂时无法识别日程详情")
                checked += 1
                mapping.copy(calendarId = binding.calendar.calendarId, targetEventId = ids.single(), writtenEvent = actual)
            }.getOrElse { failed += 1; mapping }
        }
        return CleanupCatalogReadResult(next, checked, failed)
    }
}
