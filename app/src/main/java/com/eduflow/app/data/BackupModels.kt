package com.eduflow.app.data

import kotlinx.serialization.Serializable

@Serializable enum class PackageType { FULL_ARCHIVE, SCHOOL_PROGRAM, STUDY_DATA }
@Serializable data class BackupManifest(val backupFormatVersion: Int = 2, val createdAt: String, val roomVersion: Int = 8, val appVersion: String = "1.0", val packageType: PackageType = PackageType.FULL_ARCHIVE, val programFingerprint: String? = null)
@Serializable data class BackupSettings(val taskRemindersEnabled: Boolean, val dailySummaryEnabled: Boolean, val summaryHour: Int, val summaryMinute: Int, val schoolYearStart: String? = null, val schoolYearEnd: String? = null)
@Serializable data class SubjectDto(val id: Long, val name: String, val shortName: String?, val teacher: String?, val room: String?, val color: Long)
@Serializable data class TemplateDto(val id: Long, val name: String)
@Serializable data class CycleConfigDto(val id: Int, val monday: String, val templateId: Long)
@Serializable data class CycleEntryDto(val id: Long, val position: Int, val templateId: Long)
@Serializable data class SlotDto(val id: Long, val templateId: Long, val weekday: Int, val lessonIndex: Int, val start: String, val end: String, val subjectId: Long?, val teacher: String?, val room: String?, val group: String?)
@Serializable data class PrivateDto(val id: Long, val subjectId: Long?, val weekday: Int, val start: String, val end: String, val startDate: String, val endDate: String?, val interval: Int, val teacher: String?, val room: String?, val enabled: Boolean, val privateName: String? = null, val privateLocationKind: String? = null, val label: String? = null)
@Serializable data class LessonDto(val id: Long, val date: String, val start: String, val end: String, val subjectId: Long?, val slotId: Long?, val privateId: Long?, val kind: String, val cancellation: String, val teacher: String?, val room: String?, val topic: String?, val notes: String?, val privateName: String? = null, val privateLocationKind: String? = null)
@Serializable data class ExceptionDto(val date: String, val type: String, val title: String?, val reason: String?, val note: String?)
@Serializable data class EventDto(val id: Long, val date: String, val title: String, val description: String?, val start: String?, val end: String?, val allDay: Boolean)
@Serializable data class TaskDto(val id: Long, val title: String, val description: String?, val subjectId: Long?, val originId: Long?, val dueLessonId: Long?, val type: String, val priority: String, val status: String, val dueAt: String?, val completedAt: String?, val createdAt: String, val intendedDueLessonInstanceId: Long? = dueLessonId)
@Serializable data class ChecklistDto(val id: Long, val taskId: Long, val text: String, val completed: Boolean, val position: Int)
@Serializable data class ReminderDto(val id: Long, val taskId: Long, val kind: String, val customAt: String?, val enabled: Boolean, val createdAt: String)
@Serializable data class BackupData(val subjects: List<SubjectDto>, val templates: List<TemplateDto>, val cycleConfig: CycleConfigDto?, val cycleEntries: List<CycleEntryDto>, val slots: List<SlotDto>, val privateLessons: List<PrivateDto>, val lessons: List<LessonDto>, val exceptions: List<ExceptionDto>, val events: List<EventDto>, val tasks: List<TaskDto>, val checklist: List<ChecklistDto>, val reminders: List<ReminderDto>)
@Serializable data class PortableBackup(val manifest: BackupManifest, val data: BackupData, val settings: BackupSettings)
