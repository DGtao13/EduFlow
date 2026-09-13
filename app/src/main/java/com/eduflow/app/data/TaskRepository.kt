package com.eduflow.app.data

import androidx.room.withTransaction
import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.DayExceptionType
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskChecklistItem
import com.eduflow.app.data.local.TaskReminder
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.domain.ScheduleCycle
import com.eduflow.app.domain.LessonBlock
import com.eduflow.app.domain.LessonBlockResolver
import java.time.LocalDate
import java.time.LocalDateTime

object TaskLogic {
    fun isOverdue(task: Task, now: LocalDateTime): Boolean = task.status == TaskStatus.PENDING && task.dueAt?.isBefore(now) == true
    fun complete(task: Task, now: LocalDateTime): Task = task.copy(status = TaskStatus.COMPLETED, completedAt = now)
    fun reopen(task: Task): Task = task.copy(status = TaskStatus.PENDING, completedAt = null)
    fun sortedPending(tasks: List<Task>, now: LocalDateTime): List<Task> = tasks.sortedWith(
        compareBy<Task> { listOf(TaskPriority.MUST, TaskPriority.SHOULD, TaskPriority.OPTIONAL).indexOf(it.priority) }
            .thenByDescending { isOverdue(it, now) }
            .thenBy { it.dueAt == null }
            .thenBy { it.dueAt }
            .thenBy { it.createdAt }
            .thenBy { it.id }
    )
}

object NextLessonResolver {
    fun chooseUpcomingSchoolSession(subjectId: Long, lessons: List<LessonInstance>, slots: List<ScheduleSlot>, after: LocalDateTime,
        noSchoolDates: Set<LocalDate>, year: AcademicYearSettings): LessonInstance? = lessons.filter { it.kind == LessonKind.SCHOOL }
        .groupBy { it.actualDate }.values.flatMap { LessonBlockResolver.resolveAll(it, slots).values }
        .distinctBy { it.ids.first() }
        .filter { block -> block.lessons.any { it.subjectId == subjectId } &&
            LocalDateTime.of(block.lessons.first().actualDate, block.startTime).isAfter(after) &&
            block.lessons.first().actualDate !in noSchoolDates && SchoolYear.containsSchoolDate(block.lessons.first().actualDate, year) }
        .sortedWith(compareBy<LessonBlock> { it.lessons.first().actualDate }.thenBy { it.startTime })
        .firstNotNullOfOrNull(::canonicalActiveAnchor)

    fun candidatesForOrigin(origin: LessonInstance?, lessons: List<LessonInstance>): List<LessonInstance> = when {
        origin?.kind == LessonKind.PRIVATE && origin.sourcePrivateLessonId != null -> lessons.filter { it.sourcePrivateLessonId == origin.sourcePrivateLessonId }
        origin?.kind == LessonKind.PRIVATE -> emptyList()
        origin?.kind == LessonKind.SCHOOL -> lessons.filter { it.kind == LessonKind.SCHOOL }
        else -> lessons
    }
    fun choose(lessons: List<LessonInstance>, after: LocalDateTime, noSchoolDates: Set<LocalDate>, schoolOnly: Boolean = false, academicYear: AcademicYearSettings = AcademicYearSettings()): LessonInstance? = lessons
        .sortedWith(compareBy<LessonInstance> { it.actualDate }.thenBy { it.actualStartTime })
        .firstOrNull { it.cancellationState != CancellationState.CANCELLED && (!schoolOnly || it.kind == LessonKind.SCHOOL) && (it.kind != LessonKind.SCHOOL || (it.actualDate !in noSchoolDates && SchoolYear.containsSchoolDate(it.actualDate, academicYear))) && LocalDateTime.of(it.actualDate, it.actualStartTime).isAfter(after) }

    /** SCHOOL deadlines persist the first active member of the next derived lesson block. */
    fun chooseSchoolBlockAware(
        origin: LessonInstance,
        subjectId: Long,
        lessons: List<LessonInstance>,
        scheduleSlots: List<ScheduleSlot>,
        noSchoolDates: Set<LocalDate>,
        academicYear: AcademicYearSettings
    ): LessonInstance? {
        require(origin.kind == LessonKind.SCHOOL)
        val blocksByLessonId = lessons.filter { it.kind == LessonKind.SCHOOL }
            .groupBy { it.actualDate }
            .flatMap { (_, dayLessons) -> LessonBlockResolver.resolveAll(dayLessons, scheduleSlots).toList() }
            .toMap()
        val originBlock = blocksByLessonId[origin.id] ?: LessonBlock(listOf(origin))
        val excludedOriginIds = originBlock.ids.toSet()
        return blocksByLessonId.values
            .distinctBy { block -> block.lessons.first().id }
            .asSequence()
            .filter { block -> block.lessons.any { it.subjectId == subjectId } }
            .filter { block -> block.ids.none(excludedOriginIds::contains) }
            .filter { block -> isAtOrAfterBlockEnd(block, originBlock) }
            .filter { block ->
                val date = block.lessons.first().actualDate
                date !in noSchoolDates && SchoolYear.containsSchoolDate(date, academicYear)
            }
            .sortedWith(compareBy<LessonBlock> { it.lessons.first().actualDate }.thenBy { it.startTime })
            .mapNotNull(::canonicalActiveAnchor)
            .firstOrNull()
    }

    fun canonicalActiveAnchor(block: LessonBlock): LessonInstance? = block.lessons
        .filter { it.cancellationState != CancellationState.CANCELLED }
        .minWithOrNull(compareBy<LessonInstance> { it.actualDate }.thenBy { it.actualStartTime })

    fun intendedBlockAnchor(origin: LessonInstance, lessons: List<LessonInstance>, slots: List<ScheduleSlot>, noSchoolDates: Set<LocalDate>, year: AcademicYearSettings): LessonInstance? {
        if (origin.actualDate in noSchoolDates || !SchoolYear.containsSchoolDate(origin.actualDate, year)) return null
        return canonicalActiveAnchor(LessonBlockResolver.resolve(origin, lessons.filter { it.actualDate == origin.actualDate && it.kind == LessonKind.SCHOOL }, slots))
    }

    private fun isAtOrAfterBlockEnd(candidate: LessonBlock, origin: LessonBlock): Boolean {
        val candidateStart = LocalDateTime.of(candidate.lessons.first().actualDate, candidate.startTime)
        val originEnd = LocalDateTime.of(origin.lessons.first().actualDate, origin.endTime)
        return !candidateStart.isBefore(originEnd)
    }
}

class TaskRepository(private val database: EduFlowDatabase) {
    private val weekMaterializer = WeekMaterializer(database)

    /** Nested materialization shares the caller's Room transaction. */
    suspend fun recomputeEffectiveDue(task: Task): Task {
        if (task.status != TaskStatus.PENDING) return task
        val intended = task.intendedDueLessonInstanceId?.let { database.lessonInstanceDao().getById(it) } ?: return task
        if (intended.kind != LessonKind.SCHOOL) return task
        val year = AcademicYearSettingsRepository(com.eduflow.app.EduFlowApplication.appContext).snapshot()
        val noSchool = database.dayExceptionDao().getByDate(intended.actualDate)?.takeIf { it.type == DayExceptionType.NO_SCHOOL }?.let { setOf(it.date) }.orEmpty()
        val target = NextLessonResolver.intendedBlockAnchor(intended, database.lessonInstanceDao().getForDate(intended.actualDate), database.scheduleSlotDao().getAll(), noSchool, year)
            ?: intended.subjectId?.let { resolveNextLesson(it, LocalDateTime.of(intended.actualDate, intended.actualStartTime), intended) }
        return EffectiveTaskDue.apply(task, target)
    }

    suspend fun resolveNextLesson(subjectId: Long, after: LocalDateTime, origin: LessonInstance? = null, horizonWeeks: Long = 16): LessonInstance? {
        val startMonday = ScheduleCycle.mondayOf(after.toLocalDate())
        val academicYear = AcademicYearSettingsRepository(com.eduflow.app.EduFlowApplication.appContext).snapshot()
        if (origin?.kind == LessonKind.SCHOOL) {
            return resolveNextSchoolBlock(subjectId, origin, startMonday, academicYear)
        }
        repeat(horizonWeeks.toInt() + 1) { offset -> weekMaterializer.materializeWeek(startMonday.plusWeeks(offset.toLong())) }
        val until = startMonday.plusWeeks(horizonWeeks).plusDays(6)
        val exceptions = database.dayExceptionDao().getForDateRange(after.toLocalDate(), until)
            .filter { it.type == DayExceptionType.NO_SCHOOL }.map { it.date }.toSet()
        val lessons = database.lessonInstanceDao().getForSubjectInRange(subjectId, after.toLocalDate(), until)
        if (origin == null) {
            val allLessons = database.lessonInstanceDao().getForDateRange(after.toLocalDate(), until)
            return NextLessonResolver.chooseUpcomingSchoolSession(subjectId, allLessons, database.scheduleSlotDao().getAll(), after, exceptions, academicYear)
        }
        return NextLessonResolver.choose(NextLessonResolver.candidatesForOrigin(origin, lessons), after, exceptions, academicYear = academicYear)
    }

    suspend fun resolveFutureSession(subjectId: Long, after: LocalDateTime, origin: LessonInstance?, ordinal: Int): LessonInstance? {
        return HomeworkDuePresets.resolve(subjectId, after, origin, ordinal) { subject, time, source -> resolveNextLesson(subject, time, source) }
    }

    /** Materializes one future week at a time and stops as soon as a valid SCHOOL block is found. */
    private suspend fun resolveNextSchoolBlock(
        subjectId: Long,
        origin: LessonInstance,
        startMonday: LocalDate,
        academicYear: AcademicYearSettings
    ): LessonInstance? {
        var week = startMonday
        val finalWeek = ScheduleCycle.mondayOf(academicYear.endDate)
        val slots = database.scheduleSlotDao().getAll()
        while (!week.isAfter(finalWeek)) {
            weekMaterializer.materializeWeek(week)
            val until = minOf(week.plusDays(6), academicYear.endDate)
            val exceptions = database.dayExceptionDao().getForDateRange(origin.actualDate, until)
                .filter { it.type == DayExceptionType.NO_SCHOOL }.map { it.date }.toSet()
            val lessons = database.lessonInstanceDao().getForDateRange(origin.actualDate, until)
            NextLessonResolver.chooseSchoolBlockAware(origin, subjectId, lessons, slots, exceptions, academicYear)?.let { return it }
            week = week.plusWeeks(1)
        }
        return null
    }

    suspend fun saveWithChecklist(task: Task, checklist: List<TaskChecklistItem>, reminders: List<TaskReminder> = emptyList()): Long = database.withTransaction {
        val taskId = database.taskDao().upsert(task)
        val incomingIds = checklist.mapNotNull { it.id.takeIf { id -> id != 0L } }.toSet()
        database.taskChecklistDao().getForTask(taskId)
            .filter { it.id !in incomingIds }
            .forEach { database.taskChecklistDao().delete(it) }
        checklist.forEachIndexed { index, item ->
            database.taskChecklistDao().upsert(item.copy(taskId = taskId, position = index))
        }
        val reminderIds = reminders.map { it.id }.toSet()
        database.taskReminderDao().getForTask(taskId).filter { it.id !in reminderIds }
            .forEach { database.taskReminderDao().delete(it) }
        reminders.forEach { reminder -> database.taskReminderDao().upsert(reminder.copy(taskId = taskId)) }
        taskId
    }
}
