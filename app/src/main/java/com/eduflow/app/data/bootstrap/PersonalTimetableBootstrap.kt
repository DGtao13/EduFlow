package com.eduflow.app.data.bootstrap

import android.util.Log
import androidx.room.withTransaction
import com.eduflow.app.data.local.CycleConfiguration
import com.eduflow.app.data.local.CycleEntry
import com.eduflow.app.data.local.DayException
import com.eduflow.app.data.local.DayExceptionType
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.data.local.ScheduleTemplate
import com.eduflow.app.data.local.Subject
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

data class BootstrapSubjectDefinition(
    val name: String,
    val shortName: String?,
    val teacher: String,
    val room: String
)

data class BootstrapSlotDefinition(
    val weekday: Int,
    val period: Int,
    val subjectName: String,
    val group: String? = null
)

/** The user's personal seed data, kept separate from the generic timetable domain. */
object PersonalTimetableData {
    const val WEEK_A = "Седмица А"
    const val WEEK_B = "Седмица Б"
    const val GROUP_ONE = "Група 1"

    val periodTimes: Map<Int, Pair<LocalTime, LocalTime>> = mapOf(
        1 to (LocalTime.of(8, 0) to LocalTime.of(8, 45)),
        2 to (LocalTime.of(8, 45) to LocalTime.of(9, 30)),
        3 to (LocalTime.of(9, 50) to LocalTime.of(10, 35)),
        4 to (LocalTime.of(10, 35) to LocalTime.of(11, 20)),
        5 to (LocalTime.of(11, 40) to LocalTime.of(12, 25)),
        6 to (LocalTime.of(12, 25) to LocalTime.of(13, 10)),
        7 to (LocalTime.of(13, 30) to LocalTime.of(14, 15)),
        8 to (LocalTime.of(14, 15) to LocalTime.of(15, 0))
    )

    val subjects = listOf(
        BootstrapSubjectDefinition("ГО", "ГО", "Юлия Р. Симеонова", "34 (Етаж 3)"),
        BootstrapSubjectDefinition("Мрежова и информационна сигурност (ИУЧ - СПП)", "МиИС", "Румяна Т. Николова-Трендафилова", "56 (Блок 5)"),
        BootstrapSubjectDefinition("Комуникационна техника и технологии (ИУЧ - СПП)", "КТТ", "Димитър Н. Стоянов", "57 (Блок 5)"),
        BootstrapSubjectDefinition("Програмиране за вградени микрокомпютри (ИУЧ - СПП)", "ПВМ", "Кирил А. Конов", "14 (Етаж 1)"),
        BootstrapSubjectDefinition("Увод в сензорите и сензорните системи (ИУЧ - СПП)", "Сензори", "Нели Н. Велинова", "21 (Етаж 2)"),
        BootstrapSubjectDefinition("ФВС", "ФВС", "Деница В. Радева", "80 (Спортни 8)"),
        BootstrapSubjectDefinition("ЧЕП (ОбПП)", "ЧЕП", "Таня И. Станева", "26 (Етаж 2)"),
        BootstrapSubjectDefinition("Управление на киберзаплахите (ФУЧ - ДП/ДруП)", "Киберзаплахи", "Румяна Т. Николова-Трендафилова", "56 (Блок 5)"),
        BootstrapSubjectDefinition("УП - Мрежова и информационна сигурност (ИУЧ - СПП)", "УП МиИС", "Румяна Т. Николова-Трендафилова", "58 (Блок 5)"),
        BootstrapSubjectDefinition("УП - Програмиране за вградени микрокомпютри (ИУЧ - СПП)", "УП ПВМ", "Кирил А. Конов", "60 (Блок 6)"),
        BootstrapSubjectDefinition("УП - Изграждане и диагностика на компютърни мрежи (ИУЧ - СПП)", "УП ИДКМ", "Александър М. Михайлов", "56 (Блок 5)"),
        BootstrapSubjectDefinition("БЕЛ", "БЕЛ", "Боряна А. Хубенова", "44 (Етаж 4)"),
        BootstrapSubjectDefinition("Мат", "Мат", "Милена И. Гошева", "31 (Етаж 3)"),
        BootstrapSubjectDefinition("Немски", "Немски", "Славка И. Славчева", "26 (Етаж 2)"),
        BootstrapSubjectDefinition("ЧК", "ЧК", "Милена И. Гошева", "31 (Етаж 3)"),
        BootstrapSubjectDefinition("УП - Системна администрация (ИУЧ - СПП)", "УП Сист. адм.", "Милена Й. Добрикова", "65 (Блок 6)"),
        BootstrapSubjectDefinition("БЕЛ (ИУЧ - РП/УП-А)", "БЕЛ ИУЧ", "Боряна А. Хубенова", "44 (Етаж 4)"),
        BootstrapSubjectDefinition("УП - Комуникационна техника и технологии (ИУЧ - СПП)", "УП КТТ", "Димитър Н. Стоянов", "55 (Блок 5)"),
        BootstrapSubjectDefinition("Спортни дейности", "Спорт", "Живко Г. Иванов", "80 (Спортни 8)"),
        BootstrapSubjectDefinition("Отраслови стандарти (ИУЧ - РПП)", "Отр. стандарти", "Димитър Н. Стоянов", "57 (Блок 5)"),
        BootstrapSubjectDefinition("Изграждане и диагностика на компютърни мрежи (ИУЧ - СПП)", "ИДКМ", "Александър М. Михайлов", "56 (Блок 5)"),
        BootstrapSubjectDefinition("Глобални мрежи (ИУЧ - СПП)", "Глобални мрежи", "Димитър Н. Стоянов", "57 (Блок 5)"),
        BootstrapSubjectDefinition("Системна администрация (ИУЧ - СПП)", "Сист. адм.", "Милена Й. Добрикова", "15 (Етаж 1)"),
        BootstrapSubjectDefinition("УП - Увод в сензорите и сензорните системи (ИУЧ - СПП)", "УП Сензори", "Нели Н. Велинова", "13 (Етаж 1)"),
        BootstrapSubjectDefinition("УП - Глобални мрежи (ИУЧ - СПП)", "УП Глобални", "Димитър Н. Стоянов", "55 (Блок 5)")
    )

    val weekA = listOf(
        *(periods(1, "ГО", 1, 2)),
        *(periods(1, "Мрежова и информационна сигурност (ИУЧ - СПП)", 3, 4)),
        *(periods(1, "Комуникационна техника и технологии (ИУЧ - СПП)", 5, 6)),
        *(periods(1, "Програмиране за вградени микрокомпютри (ИУЧ - СПП)", 7, 8)),
        *(periods(2, "Увод в сензорите и сензорните системи (ИУЧ - СПП)", 1, 2)),
        *(periods(2, "ФВС", 3, 4)),
        *(periods(2, "ЧЕП (ОбПП)", 5, 6)),
        *(periods(2, "Управление на киберзаплахите (ФУЧ - ДП/ДруП)", 7, 8, GROUP_ONE)),
        *(periods(3, "УП - Мрежова и информационна сигурност (ИУЧ - СПП)", 1, 2, GROUP_ONE)),
        *(periods(3, "УП - Програмиране за вградени микрокомпютри (ИУЧ - СПП)", 3, 4, GROUP_ONE)),
        *(periods(3, "УП - Изграждане и диагностика на компютърни мрежи (ИУЧ - СПП)", 5, 6, GROUP_ONE)),
        *(periods(4, "БЕЛ", 1, 2)),
        *(periods(4, "Мат", 3, 4)),
        *(periods(4, "Немски", 5, 6)),
        BootstrapSlotDefinition(4, 7, "ЧК"),
        *(periods(5, "УП - Системна администрация (ИУЧ - СПП)", 1, 2, GROUP_ONE)),
        BootstrapSlotDefinition(5, 3, "БЕЛ"),
        BootstrapSlotDefinition(5, 4, "БЕЛ (ИУЧ - РП/УП-А)"),
        *(periods(5, "УП - Комуникационна техника и технологии (ИУЧ - СПП)", 5, 6, GROUP_ONE))
    )

    val weekB = listOf(
        *(periods(1, "Спортни дейности", 1, 2)),
        *(periods(1, "Отраслови стандарти (ИУЧ - РПП)", 3, 4)),
        *(periods(1, "Изграждане и диагностика на компютърни мрежи (ИУЧ - СПП)", 5, 6)),
        *(periods(1, "Глобални мрежи (ИУЧ - СПП)", 7, 8)),
        *(periods(2, "Системна администрация (ИУЧ - СПП)", 1, 2)),
        *(periods(2, "ФВС", 3, 4)),
        *(periods(2, "ЧЕП (ОбПП)", 5, 6)),
        *(periods(2, "Управление на киберзаплахите (ФУЧ - ДП/ДруП)", 7, 8, GROUP_ONE)),
        *(periods(3, "УП - Мрежова и информационна сигурност (ИУЧ - СПП)", 1, 2, GROUP_ONE)),
        *(periods(3, "УП - Програмиране за вградени микрокомпютри (ИУЧ - СПП)", 3, 4, GROUP_ONE)),
        *(periods(3, "УП - Изграждане и диагностика на компютърни мрежи (ИУЧ - СПП)", 5, 6, GROUP_ONE)),
        *(periods(4, "БЕЛ", 1, 2)),
        *(periods(4, "Мат", 3, 4)),
        *(periods(4, "Немски", 5, 6)),
        BootstrapSlotDefinition(4, 7, "ЧК"),
        *(periods(5, "УП - Системна администрация (ИУЧ - СПП)", 1, 2, GROUP_ONE)),
        BootstrapSlotDefinition(5, 3, "БЕЛ"),
        BootstrapSlotDefinition(5, 4, "БЕЛ (ИУЧ - РП/УП-А)"),
        *(periods(5, "УП - Увод в сензорите и сензорните системи (ИУЧ - СПП)", 5, 6, GROUP_ONE)),
        *(periods(5, "УП - Глобални мрежи (ИУЧ - СПП)", 7, 8, GROUP_ONE))
    )

    private fun periods(weekday: Int, subjectName: String, first: Int, second: Int, group: String? = null) =
        arrayOf(BootstrapSlotDefinition(weekday, first, subjectName, group), BootstrapSlotDefinition(weekday, second, subjectName, group))
}

class PersonalTimetableBootstrap(private val database: EduFlowDatabase) {
    suspend fun runIfNeeded(): BootstrapResult = database.withTransaction {
        val existingSubjects = database.subjectDao().getAll()
        val existingTemplates = database.scheduleTemplateDao().getAll()
        val existingSlots = existingTemplates.sumOf { database.scheduleSlotDao().getForTemplate(it.id).size }

        if (existingSubjects.isNotEmpty() || existingSlots > 0) {
            Log.i(TAG, "Personal timetable bootstrap skipped: meaningful existing data detected")
            return@withTransaction BootstrapResult.SKIPPED_EXISTING_DATA
        }

        val weekATemplate = findOrCreateTemplate(existingTemplates, PersonalTimetableData.WEEK_A, "Week A")
        val weekBTemplate = findOrCreateTemplate(existingTemplates, PersonalTimetableData.WEEK_B, "Week B")
        val subjectIds = existingSubjects.associateBy { normalized(it.name) }.toMutableMap()
        val palette = listOf(0xFF1565C0L, 0xFF3949ABL, 0xFF6A1B9AL, 0xFFD81B60L, 0xFFC62828L, 0xFFEF6C00L, 0xFFF9C74FL, 0xFF9CCC65L, 0xFF2E7D32L, 0xFF00838FL)

        PersonalTimetableData.subjects.forEachIndexed { index, definition ->
            if (subjectIds[normalized(definition.name)] == null) {
                val id = database.subjectDao().upsert(
                    Subject(name = definition.name, shortName = definition.shortName, defaultTeacher = definition.teacher, defaultRoom = definition.room, color = palette[index % palette.size])
                )
                subjectIds[normalized(definition.name)] = Subject(id = id, name = definition.name, shortName = definition.shortName, defaultTeacher = definition.teacher, defaultRoom = definition.room, color = palette[index % palette.size])
            }
        }

        insertSlots(weekATemplate.id, PersonalTimetableData.weekA, subjectIds)
        insertSlots(weekBTemplate.id, PersonalTimetableData.weekB, subjectIds)

        database.cycleDao().saveConfiguration(CycleConfiguration(anchorMonday = LocalDate.of(2026, 9, 21), anchorTemplateId = weekATemplate.id))
        database.cycleDao().clearEntries()
        database.cycleDao().insertEntries(listOf(CycleEntry(position = 0, templateId = weekATemplate.id), CycleEntry(position = 1, templateId = weekBTemplate.id)))

        val independenceDay = LocalDate.of(2026, 9, 22)
        val existingException = database.dayExceptionDao().getByDate(independenceDay)
        when {
            existingException == null -> database.dayExceptionDao().upsert(DayException(independenceDay, DayExceptionType.NO_SCHOOL, title = "Ден на независимостта"))
            existingException.type == DayExceptionType.NO_SCHOOL && existingException.title == "Ден на независимостта" -> Unit
            else -> Log.w(TAG, "Personal timetable bootstrap did not replace a conflicting 2026-09-22 exception")
        }

        Log.i(TAG, "Personal timetable bootstrap completed: 25 subjects, ${PersonalTimetableData.weekA.size} Week A slots, ${PersonalTimetableData.weekB.size} Week B slots")
        BootstrapResult.IMPORTED
    }

    private suspend fun findOrCreateTemplate(existing: List<ScheduleTemplate>, desiredName: String, legacyName: String): ScheduleTemplate {
        existing.firstOrNull { normalized(it.name) == normalized(desiredName) }?.let { return it }
        existing.firstOrNull { normalized(it.name) == normalized(legacyName) && database.scheduleSlotDao().getForTemplate(it.id).isEmpty() }?.let {
            database.scheduleTemplateDao().update(it.copy(name = desiredName))
            return it.copy(name = desiredName)
        }
        val id = database.scheduleTemplateDao().upsert(ScheduleTemplate(name = desiredName))
        return ScheduleTemplate(id = id, name = desiredName)
    }

    private suspend fun insertSlots(templateId: Long, definitions: List<BootstrapSlotDefinition>, subjectIds: Map<String, Subject>) {
        val slots = definitions.map { definition ->
            val times = PersonalTimetableData.periodTimes.getValue(definition.period)
            ScheduleSlot(
                scheduleTemplateId = templateId,
                weekday = definition.weekday,
                lessonIndex = definition.period,
                startTime = times.first,
                endTime = times.second,
                subjectId = subjectIds.getValue(normalized(definition.subjectName)).id,
                groupInfo = definition.group
            )
        }
        slots.forEach { database.scheduleSlotDao().upsert(it) }
    }

    private fun normalized(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    companion object {
        private const val TAG = "PersonalTimetable"
    }
}

enum class BootstrapResult { IMPORTED, SKIPPED_EXISTING_DATA }
