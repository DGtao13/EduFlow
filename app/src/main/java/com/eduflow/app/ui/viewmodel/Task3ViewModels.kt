package com.eduflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.EduFlowApplication
import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.data.local.ScheduleTemplate
import com.eduflow.app.data.local.Subject
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.DayException
import com.eduflow.app.data.local.DayExceptionType
import com.eduflow.app.data.local.TimetableEvent
import com.eduflow.app.data.WeekMaterializer
import com.eduflow.app.data.SchoolLessonCancellationRepository
import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.data.AcademicYearSettingsRepository
import com.eduflow.app.data.local.SchoolBlockNormalization
import com.eduflow.app.domain.ScheduleCycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi

class SubjectViewModel(private val database: EduFlowDatabase) : ViewModel() {
    val subjects: StateFlow<List<Subject>> = database.subjectDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(subject: Subject) = viewModelScope.launch { database.subjectDao().upsert(subject) }
    /** A subject that still defines timetable slots must be edited out of the program first.
     * Other links are nullable foreign keys and detach without deleting history or private lessons. */
    fun delete(subject: Subject, onBlocked: () -> Unit, onDeleted: () -> Unit) = viewModelScope.launch {
        if (database.scheduleSlotDao().countForSubject(subject.id) > 0) onBlocked()
        else { database.subjectDao().delete(subject); onDeleted() }
    }
}

class TimetableViewModel(private val database: EduFlowDatabase) : ViewModel() {
    val templates: StateFlow<List<ScheduleTemplate>> = database.scheduleTemplateDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun slots(templateId: Long): StateFlow<List<ScheduleSlot>> = database.scheduleSlotDao()
        .observeForTemplate(templateId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveTemplate(template: ScheduleTemplate) = viewModelScope.launch {
        database.scheduleTemplateDao().upsert(template)
    }

    fun deleteTemplate(template: ScheduleTemplate) = viewModelScope.launch {
        database.scheduleTemplateDao().delete(template)
    }

    fun saveSlot(slot: ScheduleSlot, previousSlot: ScheduleSlot? = null) = viewModelScope.launch {
        database.withTransaction {
            val previous = previousSlot?.let { source -> database.scheduleSlotDao().getForTemplate(slot.scheduleTemplateId).firstOrNull { it.id == source.id } }
            val blockId = if (slot.logicalBlockId != null && previous != null) {
                previous.logicalBlockId?.trim()?.takeIf { it.isNotEmpty() } ?: "slot-block-${previous.id}"
            } else slot.logicalBlockId
            database.scheduleSlotDao().upsert(slot.copy(logicalBlockId = blockId))
            if (blockId != null && previous != null && previous.logicalBlockId != blockId) {
                database.scheduleSlotDao().update(previous.copy(logicalBlockId = blockId))
            }
            SchoolBlockNormalization.repairTopology(database, slot.scheduleTemplateId)
            com.eduflow.app.data.local.SchoolLessonSourceRepair.repair(database)
        }
    }
    fun deleteSlot(slot: ScheduleSlot) = viewModelScope.launch {
        database.scheduleSlotDao().delete(slot)
        SchoolBlockNormalization.repairTopology(database, slot.scheduleTemplateId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModel(private val database: EduFlowDatabase) : ViewModel() {
    private val repository = WeekMaterializer(database)
    private val cancellationRepository = SchoolLessonCancellationRepository(EduFlowApplication.appContext, database)
    private val dayExceptionRepository = com.eduflow.app.data.SchoolDayExceptionRepository(EduFlowApplication.appContext, database)
    private val selectedMonday = MutableStateFlow(ScheduleCycle.mondayOf(java.time.LocalDate.now()))
    val weekMonday = selectedMonday.asStateFlow()
    val subjects = database.subjectDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scheduleSlots = database.scheduleSlotDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates = database.scheduleTemplateDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val configuration = database.cycleDao().observeConfiguration().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val cycleEntries = database.cycleDao().observeEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tasks = database.taskDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val privateDefinitions = database.recurringPrivateLessonDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val academicYear = AcademicYearSettingsRepository(EduFlowApplication.appContext).settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademicYearSettings())
    val lessons = selectedMonday.flatMapLatest { database.lessonInstanceDao().observeForDateRange(it, it.plusDays(4)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val privateLessons = selectedMonday.flatMapLatest { database.lessonInstanceDao().observePrivateForDateRange(it, it.plusDays(6)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val events = selectedMonday.flatMapLatest { database.timetableEventDao().observeForDateRange(it, it.plusDays(4)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exceptions = selectedMonday.flatMapLatest { database.dayExceptionDao().observeForDateRange(it, it.plusDays(4)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val weekDataCache = mutableMapOf<java.time.LocalDate, kotlinx.coroutines.flow.StateFlow<ScheduleWeekData>>()
    private val scheduleInnerOffsets = mutableMapOf<java.time.LocalDate, Int>()

    /** Session-only UI state; it intentionally does not leave the Schedule ViewModel. */
    fun savedInnerOffset(monday: java.time.LocalDate): Int? = synchronized(scheduleInnerOffsets) {
        scheduleInnerOffsets[ScheduleCycle.mondayOf(monday)]
    }

    fun saveInnerOffset(monday: java.time.LocalDate, offset: Int) {
        synchronized(scheduleInnerOffsets) {
            scheduleInnerOffsets[ScheduleCycle.mondayOf(monday)] = offset.coerceAtLeast(0)
        }
    }

    /**
     * A bounded pager asks for only the visible week and its neighbors. Keeping the
     * Room flows here means page composition remains presentation-only and week
     * changes never perform database work on the drag path.
     */
    fun weekData(monday: java.time.LocalDate): kotlinx.coroutines.flow.StateFlow<ScheduleWeekData> {
        val normalized = ScheduleCycle.mondayOf(monday)
        return synchronized(weekDataCache) {
            weekDataCache.getOrPut(normalized) {
                combine(
                    database.lessonInstanceDao().observeForDateRange(normalized, normalized.plusDays(4)),
                    database.lessonInstanceDao().observePrivateForDateRange(normalized, normalized.plusDays(6)),
                    database.timetableEventDao().observeForDateRange(normalized, normalized.plusDays(4)),
                    database.dayExceptionDao().observeForDateRange(normalized, normalized.plusDays(4))
                ) { lessons, privateLessons, events, exceptions ->
                    ScheduleWeekData(lessons, privateLessons, events, exceptions)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleWeekData())
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            combine(selectedMonday, configuration, cycleEntries, privateDefinitions) { monday, config, entries, privateLessons ->
                WeekMaterializationInput(monday, config, entries, privateLessons)
            }.distinctUntilChanged().collectLatest { input ->
                listOf(input.monday.minusWeeks(1), input.monday, input.monday.plusWeeks(1))
                    .distinct()
                    .forEach { repository.materializeWeek(it) }
            }
        }
    }

    fun showWeek(monday: java.time.LocalDate) {
        val normalized = ScheduleCycle.mondayOf(monday)
        selectedMonday.value = normalized
    }

    suspend fun configureCycle(anchorMonday: java.time.LocalDate, anchorTemplateId: Long, templateIds: List<Long>) = withContext(Dispatchers.IO) {
        repository.configureCycle(anchorMonday, anchorTemplateId, templateIds)
    }

    fun toggleCancellation(lesson: LessonInstance) = viewModelScope.launch(Dispatchers.IO) {
        if (lesson.kind == LessonKind.PRIVATE && lesson.cancellationState == CancellationState.CANCELLED) {
            com.eduflow.app.data.NotificationSettingsRepository(EduFlowApplication.appContext).allowPrivate(lesson.id)
        }
        cancellationRepository.setLessonCancellation(lesson, if (lesson.cancellationState == CancellationState.CANCELLED) CancellationState.ACTIVE else CancellationState.CANCELLED)
    }

    fun deletePrivateOneOff(lesson: LessonInstance) = viewModelScope.launch(Dispatchers.IO) {
        val current = database.lessonInstanceDao().getById(lesson.id)
        if (current?.kind == LessonKind.PRIVATE && current.sourcePrivateLessonId == null) {
            com.eduflow.app.data.NotificationSettingsRepository(EduFlowApplication.appContext).suppressDeletedPrivate(listOf(current))
            database.lessonInstanceDao().delete(current)
        }
    }

    fun cancelLessons(lessons: List<LessonInstance>) = viewModelScope.launch(Dispatchers.IO) {
        cancellationRepository.setCancellationForLessons(lessons, CancellationState.CANCELLED)
    }

    fun setBlockCancellation(lesson: LessonInstance, state: CancellationState) = viewModelScope.launch(Dispatchers.IO) {
        cancellationRepository.setCancellationForBlock(lesson, state)
    }

    fun markNoSchool(date: java.time.LocalDate, reason: String?) = viewModelScope.launch(Dispatchers.IO) {
        dayExceptionRepository.markNoSchool(date, reason)
    }

    fun restoreSchoolDay(date: java.time.LocalDate) = viewModelScope.launch(Dispatchers.IO) { dayExceptionRepository.restoreSchoolDay(date) }
    fun saveEvent(event: TimetableEvent) = viewModelScope.launch(Dispatchers.IO) { database.timetableEventDao().upsert(event) }
    fun deleteEvent(event: TimetableEvent) = viewModelScope.launch(Dispatchers.IO) { database.timetableEventDao().delete(event) }
}

data class ScheduleWeekData(
    val lessons: List<LessonInstance> = emptyList(),
    val privateLessons: List<LessonInstance> = emptyList(),
    val events: List<com.eduflow.app.data.local.TimetableEvent> = emptyList(),
    val exceptions: List<com.eduflow.app.data.local.DayException> = emptyList()
)

private data class WeekMaterializationInput(
    val monday: java.time.LocalDate,
    val configuration: com.eduflow.app.data.local.CycleConfiguration?,
    val cycleEntries: List<com.eduflow.app.data.local.CycleEntry>,
    val privateLessons: List<com.eduflow.app.data.local.RecurringPrivateLesson>
)

class DatabaseViewModelFactory(
    private val database: EduFlowDatabase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(SubjectViewModel::class.java) -> SubjectViewModel(database)
        modelClass.isAssignableFrom(TimetableViewModel::class.java) -> TimetableViewModel(database)
        modelClass.isAssignableFrom(ScheduleViewModel::class.java) -> ScheduleViewModel(database)
        modelClass.isAssignableFrom(TasksViewModel::class.java) -> TasksViewModel(database)
        modelClass.isAssignableFrom(PrivateLessonsViewModel::class.java) -> PrivateLessonsViewModel(database)
        else -> error("Unknown ViewModel: ${modelClass.name}")
    } as T
}
