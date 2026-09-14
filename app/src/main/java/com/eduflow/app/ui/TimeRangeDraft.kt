package com.eduflow.app.ui

import java.time.LocalTime

data class TimeRange(val start: LocalTime, val end: LocalTime) {
    init { require(end.isAfter(start)) }
}

enum class TimeRangeDraftStage { START, END }

/** Start is a draft only. Cancelling either stage leaves the caller's range untouched. */
data class TimeRangeDraft(val start: LocalTime? = null) {
    val stage: TimeRangeDraftStage get() = if (start == null) TimeRangeDraftStage.START else TimeRangeDraftStage.END
    fun chooseStart(value: LocalTime) = copy(start = value)
    fun returnToStart() = TimeRangeDraft()
    fun finish(end: LocalTime): TimeRange? = start?.takeIf { end.isAfter(it) }?.let { TimeRange(it, end) }
}
