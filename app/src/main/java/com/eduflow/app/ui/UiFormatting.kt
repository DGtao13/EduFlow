package com.eduflow.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val bulgarianLocale = Locale.forLanguageTag("bg-BG")
private val shortDateFormatter = DateTimeFormatter.ofPattern("d MMMM", bulgarianLocale)
private val longDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", bulgarianLocale)
private val crossYearDateFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", bulgarianLocale)

fun formatBulgarianDate(date: LocalDate): String = date.format(shortDateFormatter)

fun formatBulgarianDateWithYear(date: LocalDate): String = date.format(longDateFormatter)

fun formatBulgarianWeekRangeWithYear(monday: LocalDate): String {
    val friday = monday.plusDays(4)
    return if (monday.year == friday.year && monday.month == friday.month) {
        "${monday.dayOfMonth}–${friday.dayOfMonth} ${friday.month.getDisplayName(java.time.format.TextStyle.FULL, bulgarianLocale)} ${friday.year}"
    } else if (monday.year == friday.year) {
        "${monday.dayOfMonth} ${monday.month.getDisplayName(java.time.format.TextStyle.FULL, bulgarianLocale)}–${friday.dayOfMonth} ${friday.month.getDisplayName(java.time.format.TextStyle.FULL, bulgarianLocale)} ${friday.year}"
    } else {
        "${monday.format(longDateFormatter)}–${friday.format(longDateFormatter)}"
    }
}

fun formatBulgarianWeekRange(monday: LocalDate): String {
    val friday = monday.plusDays(4)
    return if (monday.year == friday.year && monday.month == friday.month) {
        "${monday.dayOfMonth}–${friday.dayOfMonth} ${friday.month.getDisplayName(java.time.format.TextStyle.FULL, bulgarianLocale)}"
    } else if (monday.year == friday.year) {
        "${monday.dayOfMonth} ${monday.month.getDisplayName(java.time.format.TextStyle.SHORT, bulgarianLocale)}–${friday.dayOfMonth} ${friday.month.getDisplayName(java.time.format.TextStyle.FULL, bulgarianLocale)}"
    } else {
        "${monday.format(crossYearDateFormatter)}–${friday.format(crossYearDateFormatter)}"
    }
}

fun bulgarianWeekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, bulgarianLocale)
