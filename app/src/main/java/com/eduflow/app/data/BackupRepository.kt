package com.eduflow.app.data

import android.content.Context
import androidx.room.withTransaction
import com.eduflow.app.data.local.*
import com.eduflow.app.notifications.TaskReminderScheduler
import com.eduflow.app.widget.EduFlowWidgetUpdater
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String) : Exception(message)
class BackupRepository(private val context: Context, private val database: EduFlowDatabase = EduFlowDatabase.getInstance(context)) {
    private val json = Json { ignoreUnknownKeys = false; encodeDefaults = true }
    suspend fun snapshot(): PortableBackup = database.withTransaction { snapshotInTransaction() }

    private suspend fun snapshotInTransaction(): PortableBackup {
        val settings = NotificationSettingsRepository(context).snapshot()
        val academicYear = AcademicYearSettingsRepository(context).snapshot()
        val d = BackupData(
            database.subjectDao().getAll().map { SubjectDto(it.id,it.name,it.shortName,it.defaultTeacher,it.defaultRoom,it.color) }, database.scheduleTemplateDao().getAll().map { TemplateDto(it.id,it.name) }, database.cycleDao().getConfiguration()?.let { CycleConfigDto(it.id,it.anchorMonday.toString(),it.anchorTemplateId) }, database.cycleDao().getEntries().map { CycleEntryDto(it.id,it.position,it.templateId) }, database.scheduleSlotDao().getAll().map { SlotDto(it.id,it.scheduleTemplateId,it.weekday,it.lessonIndex,it.startTime.toString(),it.endTime.toString(),it.subjectId,it.teacherOverride,it.roomOverride,it.groupInfo) }, database.recurringPrivateLessonDao().getAll().map { PrivateDto(it.id,it.subjectId,it.weekday,it.startTime.toString(),it.endTime.toString(),it.startDate.toString(),it.endDate?.toString(),it.intervalWeeks,it.teacherOverride,it.roomOverride,it.enabled,it.privateLessonName,it.privateLocationKind.name) }, database.lessonInstanceDao().getAll().map { LessonDto(it.id,it.actualDate.toString(),it.actualStartTime.toString(),it.actualEndTime.toString(),it.subjectId,it.sourceScheduleSlotId,it.sourcePrivateLessonId,it.kind.name,it.cancellationState.name,it.actualTeacher,it.actualRoom,it.topic,it.notes,it.privateLessonName,it.privateLocationKind.name) }, database.dayExceptionDao().getAll().map { ExceptionDto(it.date.toString(),it.type.name,it.title,it.reason,it.note) }, database.timetableEventDao().getAll().map { EventDto(it.id,it.date.toString(),it.title,it.description,it.startTime?.toString(),it.endTime?.toString(),it.isAllDay) }, database.taskDao().getAll().map { TaskDto(it.id,it.title,it.description,it.subjectId,it.originatingLessonInstanceId,it.dueLessonInstanceId,it.type.name,it.priority.name,it.status.name,it.dueAt?.toString(),it.completedAt?.toString(),it.createdAt.toString(),it.intendedDueLessonInstanceId) }, database.taskChecklistDao().getAll().map { ChecklistDto(it.id,it.taskId,it.text,it.isCompleted,it.position) }, database.taskReminderDao().getAll().map { ReminderDto(it.id,it.taskId,it.kind.name,it.customTriggerAt?.toString(),it.enabled,it.createdAt.toString()) })
        return PortableBackup(BackupManifest(createdAt = LocalDateTime.now().toString()), d, BackupSettings(settings.taskRemindersEnabled,settings.dailySummaryEnabled,settings.summaryHour,settings.summaryMinute,academicYear.startDate.toString(),academicYear.endDate.toString()))
    }
    suspend fun write(output: OutputStream, type: PackageType = PackageType.FULL_ARCHIVE) = withContext(Dispatchers.IO) {
        val backup = BackupPackageLogic.scoped(snapshot(), type)
        BackupPackageLogic.validate(backup)
        ZipOutputStream(output.buffered()).use { zip ->
            entry(zip,"manifest.json",json.encodeToString(BackupManifest.serializer(),backup.manifest))
            entry(zip,"data.json",json.encodeToString(BackupData.serializer(),backup.data))
            entry(zip,"settings.json",json.encodeToString(BackupSettings.serializer(),backup.settings))
        }
    }
    fun read(input: InputStream): PortableBackup {
        try {
            val files = mutableMapOf<String,String>()
            var total = 0L
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    require(item.name in setOf("manifest.json", "data.json", "settings.json") && item.name !in files && !item.isDirectory)
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = zip.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 32 * 1024 * 1024)
                        bytes.write(buffer, 0, count)
                    }
                    files[item.name] = bytes.toString("UTF-8")
                }
            }
            val manifest = json.decodeFromString(BackupManifest.serializer(),files["manifest.json"] ?: error("manifest"))
            if (manifest.backupFormatVersion > 2) throw BackupException("NEWER")
            val backup = PortableBackup(manifest,
                json.decodeFromString(BackupData.serializer(),files["data.json"] ?: error("data")),
                json.decodeFromString(BackupSettings.serializer(),files["settings.json"] ?: error("settings")))
            BackupPackageLogic.validate(backup)
            return backup
        } catch (e: BackupException) { throw e } catch (_: Exception) { throw BackupException("INVALID") }
    }

    suspend fun preview(backup: PortableBackup) = withContext(Dispatchers.IO) {
        val current = snapshot()
        BackupPackageLogic.validate(backup, current)
        ensureImportAllowed(backup, current)
    }

    private fun ensureImportAllowed(backup: PortableBackup, current: PortableBackup) {
        if (backup.manifest.packageType == PackageType.SCHOOL_PROGRAM) {
            val d = current.data
            if (d.subjects.isNotEmpty() || d.templates.isNotEmpty() || d.slots.isNotEmpty() || d.cycleConfig != null ||
                d.cycleEntries.isNotEmpty() || d.lessons.isNotEmpty() || d.tasks.isNotEmpty() || d.checklist.isNotEmpty() ||
                d.reminders.isNotEmpty() || d.privateLessons.isNotEmpty() || d.exceptions.isNotEmpty() || d.events.isNotEmpty()) {
                throw BackupException("NOT_EMPTY")
            }
        }
    }

    suspend fun restore(backup: PortableBackup) = withContext(Dispatchers.IO) { AppDataSession.runtimeMutex.withLock {
        val scheduler = TaskReminderScheduler(context,database)
        // Revalidate compatibility inside the write transaction, including after an open preview.
        database.withTransaction {
            val current = snapshotInTransaction()
            BackupPackageLogic.validate(backup, current)
            ensureImportAllowed(backup, current)
            when (backup.manifest.packageType) {
                PackageType.FULL_ARCHIVE -> { clear(); insert(backup.data) }
                PackageType.SCHOOL_PROGRAM -> insert(backup.data)
                PackageType.STUDY_DATA -> {
                    val combined = BackupPackageLogic.combineStudy(backup.data, current.data)
                    clearStudy()
                    insert(combined, personalOnly = true)
                }
            }
        }
        if (backup.manifest.packageType != PackageType.STUDY_DATA) {
            AcademicYearSettingsRepository(context).set(SchoolYear.fromStrings(backup.settings.schoolYearStart, backup.settings.schoolYearEnd))
        }
        if (backup.manifest.packageType == PackageType.FULL_ARCHIVE) {
            NotificationSettingsRepository(context).restore(NotificationSettings(backup.settings.taskRemindersEnabled, backup.settings.dailySummaryEnabled, backup.settings.summaryHour, backup.settings.summaryMinute))
        }
        androidx.work.WorkManager.getInstance(context).cancelAllWork().result.get()
        context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        database.withTransaction {
            val repository = TaskRepository(database)
            database.taskDao().getPending().forEach { task ->
                repository.recomputeEffectiveDue(task).takeIf { it != task }?.let { database.taskDao().update(it) }
            }
        }
        scheduler.syncAllPending()
        val settings = NotificationSettingsRepository(context).snapshot()
        if (settings.dailySummaryEnabled) scheduler.scheduleDailySummary(settings.summaryHour,settings.summaryMinute)
        EduFlowWidgetUpdater.update(context)
    } }

    private suspend fun clearStudy() {
        database.taskReminderDao().clear()
        database.taskChecklistDao().clear()
        database.taskDao().clear()
        database.lessonInstanceDao().clear()
        database.recurringPrivateLessonDao().clear()
        database.timetableEventDao().clear()
        database.dayExceptionDao().clear()
    }
    private suspend fun clear() { database.taskReminderDao().clear(); database.taskChecklistDao().clear(); database.taskDao().clear(); database.lessonInstanceDao().clear(); database.scheduleSlotDao().clear(); database.cycleDao().clearEntries(); database.cycleDao().clearConfiguration(); database.recurringPrivateLessonDao().clear(); database.timetableEventDao().clear(); database.dayExceptionDao().clear(); database.scheduleTemplateDao().clear(); database.subjectDao().clear() }
    private suspend fun insert(d: BackupData, personalOnly: Boolean = false) {
        val subjectNames = d.subjects.associate { it.id to it.name }
        val existingSubjects = if (personalOnly) database.subjectDao().getAll().map { it.id }.toSet() else emptySet()
        d.subjects.filter { it.id !in existingSubjects }.forEach { database.subjectDao().upsert(Subject(it.id,it.name,it.shortName,it.teacher,it.room,it.color)) }
        if (!personalOnly) d.templates.forEach { database.scheduleTemplateDao().upsert(ScheduleTemplate(it.id,it.name)) }
        d.privateLessons.forEach { dto ->
            val privateName = dto.privateName?.trim()?.takeIf { it.isNotEmpty() }
                ?: dto.label?.trim()?.takeIf { it.isNotEmpty() }
                ?: dto.subjectId?.let(subjectNames::get)
                ?: "Частен урок"
            val locationKind = dto.privateLocationKind?.let { runCatching { PrivateLessonLocationKind.valueOf(it) }.getOrNull() }
                ?: if (dto.room.isNullOrBlank()) PrivateLessonLocationKind.UNSPECIFIED else PrivateLessonLocationKind.IN_PERSON
            database.recurringPrivateLessonDao().upsert(RecurringPrivateLesson(dto.id,dto.subjectId,dto.weekday,LocalTime.parse(dto.start),LocalTime.parse(dto.end),LocalDate.parse(dto.startDate),dto.endDate?.let(LocalDate::parse),dto.interval,dto.teacher,dto.room,privateName,dto.enabled,locationKind))
        }
        if (!personalOnly) d.cycleConfig?.let { database.cycleDao().saveConfiguration(CycleConfiguration(it.id,LocalDate.parse(it.monday),it.templateId)) }
        if (!personalOnly) database.cycleDao().insertEntries(d.cycleEntries.map { CycleEntry(it.id,it.position,it.templateId) })
        if (!personalOnly) d.slots.forEach { database.scheduleSlotDao().upsert(ScheduleSlot(it.id,it.templateId,it.weekday,it.lessonIndex,LocalTime.parse(it.start),LocalTime.parse(it.end),it.subjectId,it.teacher,it.room,it.group)) }
        d.lessons.forEach { dto ->
            val kind = LessonKind.valueOf(dto.kind)
            val privateName = restoredPrivateLessonName(kind, dto.privateName, dto.subjectId?.let(subjectNames::get))
            val locationKind = restoredPrivateLocationKind(kind, dto.privateLocationKind, dto.room)
            database.lessonInstanceDao().upsert(LessonInstance(dto.id,LocalDate.parse(dto.date),LocalTime.parse(dto.start),LocalTime.parse(dto.end),dto.subjectId,dto.slotId,dto.privateId,kind,CancellationState.valueOf(dto.cancellation),dto.teacher,dto.room,dto.topic,dto.notes,privateName,locationKind))
        }
        d.exceptions.forEach { database.dayExceptionDao().upsert(DayException(LocalDate.parse(it.date),DayExceptionType.valueOf(it.type),it.title,it.reason,it.note)) }
        d.events.forEach { database.timetableEventDao().upsert(TimetableEvent(it.id,LocalDate.parse(it.date),it.title,it.description,it.start?.let(LocalTime::parse),it.end?.let(LocalTime::parse),it.allDay)) }
        d.tasks.forEach {
            val intendedSchoolId = it.intendedDueLessonInstanceId?.takeIf { intendedId -> d.lessons.firstOrNull { lesson -> lesson.id == intendedId }?.kind == LessonKind.SCHOOL.name }
            database.taskDao().upsert(Task(it.id,it.title,it.description,it.subjectId,it.originId,it.dueLessonId,TaskType.valueOf(it.type),TaskPriority.valueOf(it.priority),TaskStatus.valueOf(it.status),it.dueAt?.let(LocalDateTime::parse),it.completedAt?.let(LocalDateTime::parse),LocalDateTime.parse(it.createdAt),intendedSchoolId))
        }
        d.checklist.forEach { database.taskChecklistDao().upsert(TaskChecklistItem(it.id,it.taskId,it.text,it.completed,it.position)) }
        d.reminders.forEach { database.taskReminderDao().upsert(TaskReminder(it.id,it.taskId,TaskReminderKind.valueOf(it.kind),it.customAt?.let(LocalDateTime::parse),it.enabled,LocalDateTime.parse(it.createdAt))) }
    }
    private fun entry(zip: ZipOutputStream, name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
}
