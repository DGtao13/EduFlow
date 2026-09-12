package com.eduflow.app.ui

import java.time.LocalTime

data class TimeRange(val start: LocalTime, val end: LocalTime) {
    init { require(end.isAfter(start)) }
}

/** Start is a draft only. Cancelling either stage leaves the caller's range untouched. */
data class TimeRangeDraft(val start: LocalTime? = null) {
    fun chooseStart(value: LocalTime) = copy(start = value)
    fun finish(end: LocalTime): TimeRange? = start?.takeIf { end.isAfter(it) }?.let { TimeRange(it, end) }
}
