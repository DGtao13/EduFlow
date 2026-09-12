package com.eduflow.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class AcademicYearSettings(
    val startDate: LocalDate = SchoolYear.defaultStart,
    val endDate: LocalDate = SchoolYear.defaultEnd
)

object SchoolYear {
    val defaultStart: LocalDate = LocalDate.of(2026, 9, 15)
    val defaultEnd: LocalDate = LocalDate.of(2027, 5, 13)

    fun containsSchoolDate(date: LocalDate, settings: AcademicYearSettings = AcademicYearSettings()): Boolean =
        !date.isBefore(settings.startDate) && !date.isAfter(settings.endDate)

    fun fromStrings(start: String?, end: String?): AcademicYearSettings = AcademicYearSettings(
        start?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: defaultStart,
        end?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: defaultEnd
    )
}

private val Context.academicYearStore by preferencesDataStore(name = "academic_year_settings")

class AcademicYearSettingsRepository(context: Context) {
    private val store = context.applicationContext.academicYearStore
    val settings: Flow<AcademicYearSettings> = store.data.map { values ->
        SchoolYear.fromStrings(values[START_DATE], values[END_DATE])
    }

    suspend fun snapshot(): AcademicYearSettings = settings.first()
    suspend fun clear() { store.edit { it.clear() } }

    suspend fun set(value: AcademicYearSettings) {
        require(!value.endDate.isBefore(value.startDate))
        store.edit { values ->
            values[START_DATE] = value.startDate.toString()
            values[END_DATE] = value.endDate.toString()
        }
    }

    companion object {
        private val START_DATE = stringPreferencesKey("school_year_start")
        private val END_DATE = stringPreferencesKey("school_year_end")
    }
}
