package com.eduflow.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object ScheduleCycle {
    fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun resolveTemplateId(
        weekMonday: LocalDate,
        anchorMonday: LocalDate,
        anchorTemplateId: Long,
        orderedTemplateIds: List<Long>
    ): Long? {
        if (orderedTemplateIds.isEmpty()) return null
        val anchorIndex = orderedTemplateIds.indexOf(anchorTemplateId)
        if (anchorIndex == -1) return null
        val offset = java.time.temporal.ChronoUnit.WEEKS.between(mondayOf(anchorMonday), mondayOf(weekMonday)).toInt()
        return orderedTemplateIds[Math.floorMod(anchorIndex + offset, orderedTemplateIds.size)]
    }
}
