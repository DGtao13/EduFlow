package com.eduflow.app.data

import com.eduflow.app.data.local.*
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure package selection and validation, shared by preview and transactional restore. */
object BackupPackageLogic {
    private val json = Json { encodeDefaults = true }
    fun program(data: BackupData): BackupData {
        val ids = data.slots.mapNotNull { it.subjectId }.toSet()
        return data.copy(subjects = data.subjects.filter { it.id in ids }, privateLessons = emptyList(), lessons = emptyList(),
            exceptions = emptyList(), events = emptyList(), tasks = emptyList(), checklist = emptyList(), reminders = emptyList())
    }
    fun fingerprint(backup: PortableBackup): String {
        val p = program(backup.data)
        // Stable by identity and normalized content, never query/insertion order. Identity is
        // deliberately part of compatibility: study relationships use these exported IDs.
        val canonical = p.copy(subjects = p.subjects.sortedBy { it.id }, templates = p.templates.sortedBy { it.id },
            slots = p.slots.sortedBy { it.id }, cycleEntries = p.cycleEntries.sortedBy { it.position })
        val content = json.encodeToString(BackupData.serializer(), canonical) + "|" +
            SchoolYear.fromStrings(backup.settings.schoolYearStart, backup.settings.schoolYearEnd).toString()
        return MessageDigest.getInstance("SHA-256").digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun scoped(full: PortableBackup, type: PackageType): PortableBackup {
        val data = when (type) {
            PackageType.FULL_ARCHIVE -> full.data
            PackageType.SCHOOL_PROGRAM -> program(full.data)
            PackageType.STUDY_DATA -> full.data.copy(templates = emptyList(), slots = emptyList(), cycleConfig = null, cycleEntries = emptyList())
        }
        val settings = if (type == PackageType.SCHOOL_PROGRAM) full.settings.copy(taskRemindersEnabled = false, dailySummaryEnabled = false, summaryHour = 8, summaryMinute = 0) else full.settings
        return full.copy(manifest = full.manifest.copy(packageType = type, programFingerprint = fingerprint(full)), data = data, settings = settings)
    }
    fun combineStudy(study: BackupData, current: BackupData): BackupData {
        val programSubjectIds = current.slots.mapNotNull { it.subjectId }.toSet()
        require(study.subjects.filter { it.id in programSubjectIds }.all { incoming -> current.subjects.any { it == incoming } })
        require(study.subjects.all { incoming -> current.subjects.none { it.id == incoming.id } || current.subjects.any { it == incoming } })
        return study.copy(subjects = (current.subjects + study.subjects.filter { incoming -> current.subjects.none { it.id == incoming.id } }),
            templates = current.templates, slots = current.slots, cycleConfig = current.cycleConfig, cycleEntries = current.cycleEntries)
    }
    fun validate(backup: PortableBackup, current: PortableBackup? = null) {
        require(backup.manifest.backupFormatVersion in 1..2)
        LocalDateTime.parse(backup.manifest.createdAt)
        val settings = backup.settings
        require(settings.summaryHour in 0..23 && settings.summaryMinute in 0..59)
        val start = settings.schoolYearStart?.let(LocalDate::parse)
        val end = settings.schoolYearEnd?.let(LocalDate::parse)
        require(start == null || end == null || !end.isBefore(start))
        when (backup.manifest.packageType) {
            PackageType.SCHOOL_PROGRAM -> {
                require(backup.data == program(backup.data))
                require(backup.manifest.programFingerprint == fingerprint(backup))
            }
            PackageType.STUDY_DATA -> {
                require(backup.data.templates.isEmpty() && backup.data.slots.isEmpty() && backup.data.cycleConfig == null && backup.data.cycleEntries.isEmpty())
                require(backup.manifest.programFingerprint?.matches(Regex("[0-9a-f]{64}")) == true)
                if (current != null && backup.manifest.programFingerprint != fingerprint(current)) throw BackupException("INCOMPATIBLE")
            }
            PackageType.FULL_ARCHIVE -> Unit
        }
        val d = if (backup.manifest.packageType == PackageType.STUDY_DATA && current != null) combineStudy(backup.data, current.data) else backup.data
        val externalProgram = backup.manifest.packageType == PackageType.STUDY_DATA && current == null
        fun unique(ids: List<Long>) { require(ids.all { it > 0 } && ids.size == ids.toSet().size) }
        listOf(d.subjects.map { it.id }, d.templates.map { it.id }, d.slots.map { it.id }, d.privateLessons.map { it.id }, d.lessons.map { it.id },
            d.tasks.map { it.id }, d.checklist.map { it.id }, d.reminders.map { it.id }, d.events.map { it.id }, d.cycleEntries.map { it.id }).forEach(::unique)
        val subjects = d.subjects.map { it.id }.toSet()
        val templates = d.templates.map { it.id }.toSet()
        val slots = d.slots.map { it.id }.toSet()
        val privateIds = d.privateLessons.map { it.id }.toSet()
        val lessons = d.lessons.map { it.id }.toSet()
        val tasks = d.tasks.map { it.id }.toSet()
        fun reference(id: Long?, ids: Set<Long>) { require(id == null || id in ids) }
        fun range(startTime: String, endTime: String) { require(LocalTime.parse(endTime).isAfter(LocalTime.parse(startTime))) }
        d.slots.forEach { reference(it.templateId, templates); reference(it.subjectId, subjects); require(it.weekday in 1..5 && it.lessonIndex > 0); range(it.start, it.end) }
        d.cycleConfig?.let { require(it.id == 1); reference(it.templateId, templates); require(LocalDate.parse(it.monday).dayOfWeek.value == 1) }
        require(d.cycleEntries.map { it.position }.distinct().size == d.cycleEntries.size)
        d.cycleEntries.forEach { reference(it.templateId, templates); require(it.position >= 0) }
        d.privateLessons.forEach {
            reference(it.subjectId, subjects); require(it.weekday in 1..7 && it.interval in 1..2); range(it.start, it.end)
            val from = LocalDate.parse(it.startDate); it.endDate?.let { to -> require(!LocalDate.parse(to).isBefore(from)) }
            it.privateLocationKind?.let(PrivateLessonLocationKind::valueOf)
        }
        require(d.lessons.filter { it.slotId != null }.map { it.slotId to it.date }.distinct().size == d.lessons.count { it.slotId != null })
        require(d.lessons.filter { it.privateId != null }.map { it.privateId to it.date }.distinct().size == d.lessons.count { it.privateId != null })
        d.lessons.forEach {
            LocalDate.parse(it.date); range(it.start, it.end); LessonKind.valueOf(it.kind); CancellationState.valueOf(it.cancellation)
            reference(it.subjectId, subjects); if (!externalProgram) reference(it.slotId, slots); reference(it.privateId, privateIds)
            it.privateLocationKind?.let(PrivateLessonLocationKind::valueOf)
        }
        require(d.exceptions.map { it.date }.distinct().size == d.exceptions.size)
        d.exceptions.forEach { LocalDate.parse(it.date); DayExceptionType.valueOf(it.type) }
        d.events.forEach { LocalDate.parse(it.date); it.start?.let(LocalTime::parse); it.end?.let(LocalTime::parse); if (it.start != null && it.end != null) range(it.start, it.end) }
        d.tasks.forEach {
            reference(it.subjectId, subjects); reference(it.originId, lessons); reference(it.dueLessonId, lessons); reference(it.intendedDueLessonInstanceId, lessons)
            TaskType.valueOf(it.type); TaskPriority.valueOf(it.priority); TaskStatus.valueOf(it.status)
            LocalDateTime.parse(it.createdAt); it.dueAt?.let(LocalDateTime::parse); it.completedAt?.let(LocalDateTime::parse)
        }
        require(d.checklist.map { it.taskId to it.position }.distinct().size == d.checklist.size)
        d.checklist.forEach { reference(it.taskId, tasks); require(it.position >= 0) }
        d.reminders.forEach { reference(it.taskId, tasks); val kind = TaskReminderKind.valueOf(it.kind); LocalDateTime.parse(it.createdAt); it.customAt?.let(LocalDateTime::parse); require(kind != TaskReminderKind.CUSTOM || it.customAt != null) }
    }
}
