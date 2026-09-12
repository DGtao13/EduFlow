package com.eduflow.app.data

import com.eduflow.app.data.local.DayException
import com.eduflow.app.data.local.DayExceptionType
import com.eduflow.app.data.local.EduFlowDatabase
import java.time.LocalDate

/** Adds only absent official dates, leaving all user-created exceptions intact. */
class SchoolCalendarSeeder(private val database: EduFlowDatabase) {
    suspend fun seed2026_2027() {
        officialDates().forEach { (date, title) ->
            if (database.dayExceptionDao().getByDate(date) == null) {
                database.dayExceptionDao().upsert(DayException(date, DayExceptionType.NO_SCHOOL, title = title))
            }
        }
    }

    private fun officialDates(): List<Pair<LocalDate, String>> = buildList {
        addRange(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 2), "Есенна ваканция")
        addRange(LocalDate.of(2026, 12, 24), LocalDate.of(2027, 1, 3), "Коледна ваканция")
        addRange(LocalDate.of(2027, 1, 30), LocalDate.of(2027, 2, 2), "Междусрочна ваканция")
        addRange(LocalDate.of(2027, 4, 8), LocalDate.of(2027, 4, 11), "Пролетна ваканция за XII клас")
    }

    private fun MutableList<Pair<LocalDate, String>>.addRange(start: LocalDate, end: LocalDate, title: String) {
        var date = start
        while (!date.isAfter(end)) { add(date to title); date = date.plusDays(1) }
    }
}
