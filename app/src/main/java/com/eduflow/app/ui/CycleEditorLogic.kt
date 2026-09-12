package com.eduflow.app.ui

import com.eduflow.app.domain.ScheduleCycle
import java.time.LocalDate

data class CyclePreviewItem(
    val monday: LocalDate,
    val templateId: Long
)

fun moveCycleEntry(orderedIds: List<Long>, index: Int, direction: Int): List<Long> {
    val targetIndex = index + direction
    if (index !in orderedIds.indices || targetIndex !in orderedIds.indices) return orderedIds
    return orderedIds.toMutableList().also {
        val item = it.removeAt(index)
        it.add(targetIndex, item)
    }
}

fun cyclePreview(
    anchorDate: LocalDate,
    anchorTemplateId: Long?,
    orderedIds: List<Long>,
    weeks: Int = 3
): List<CyclePreviewItem> {
    if (anchorTemplateId == null || orderedIds.isEmpty() || weeks <= 0) return emptyList()
    val anchorMonday = ScheduleCycle.mondayOf(anchorDate)
    return (0 until weeks).mapNotNull { offset ->
        val monday = anchorMonday.plusWeeks(offset.toLong())
        ScheduleCycle.resolveTemplateId(monday, anchorMonday, anchorTemplateId, orderedIds)
            ?.let { CyclePreviewItem(monday, it) }
    }
}
