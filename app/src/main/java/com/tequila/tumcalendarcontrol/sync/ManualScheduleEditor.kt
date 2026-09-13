/*
 * 职责：创建、替换和移除应用拥有的手动校园事项，且保持稳定的手动身份。
 * 输入／输出：输入现有事项与明确编辑命令；输出新事项列表，不改变原输入。
 * 外部副作用：无。
 * 不负责：解析来源课程、Calendar Provider、Google 写入或按标题合并事项。
 */
package com.tequila.tumcalendarcontrol.sync

class ManualScheduleEditor {
    fun upsert(existing: List<ManualScheduleItem>, item: ManualScheduleItem): List<ManualScheduleItem> =
        (existing.filterNot { it.id == item.id } + item).sortedWith(compareBy(ManualScheduleItem::startsAt).thenBy(ManualScheduleItem::id))

    fun remove(existing: List<ManualScheduleItem>, itemId: String): List<ManualScheduleItem> = existing.filterNot { it.id == itemId }
}
