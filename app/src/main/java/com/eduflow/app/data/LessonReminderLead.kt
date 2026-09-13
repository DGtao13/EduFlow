package com.eduflow.app.data

object LessonReminderLead {
    val presets = listOf(5, 10, 15, 20, 30, 45, 60, 120)
    const val MAX_MINUTES = 1440
    fun valid(minutes: Int): Boolean = minutes in 1..MAX_MINUTES
    fun parse(value: String, hours: Boolean): Int? = value.trim().toLongOrNull()
        ?.takeIf { it in 1..MAX_MINUTES.toLong() }
        ?.let { it * if (hours) 60 else 1 }?.takeIf { it in 1..MAX_MINUTES.toLong() }?.toInt()
    fun label(minutes: Int): String = if (minutes % 60 == 0) {
        val hours = minutes / 60
        "$hours ${if (hours == 1) "час" else "часа"}"
    } else "$minutes ${if (minutes == 1) "минута" else "минути"}"
}
