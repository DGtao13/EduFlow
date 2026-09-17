package com.eduflow.app.ui

/** Editor-only weekday-set state. Persistence remains owned by PrivateLessonRepository. */
object RecurringWeekdaySelection {
    fun toggle(current: Set<Int>, weekday: Int): Set<Int> {
        require(weekday in 1..7)
        return if (weekday in current) current - weekday else current + weekday
    }

    fun ordered(days: Set<Int>): List<Int> = days.filter { it in 1..7 }.sorted()

    fun bulgarianSummary(days: Set<Int>): String = ordered(days).joinToString(", ") { shortLabel(it) }

    fun shortLabel(day: Int): String = when (day) {
        1 -> "Пон"; 2 -> "Вто"; 3 -> "Сря"; 4 -> "Чет"; 5 -> "Пет"; 6 -> "Съб"; else -> "Нед"
    }
}
