package com.eduflow.app.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val shortName: String? = null,
    val defaultTeacher: String? = null,
    val defaultRoom: String? = null,
    val color: Long
)

@Entity(tableName = "schedule_templates")
data class ScheduleTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "schedule_slots",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTemplate::class,
            parentColumns = ["id"],
            childColumns = ["scheduleTemplateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("scheduleTemplateId"), Index("subjectId")]
)
data class ScheduleSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduleTemplateId: Long,
    val weekday: Int,
    val lessonIndex: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val subjectId: Long?,
    val teacherOverride: String? = null,
    val roomOverride: String? = null,
    val groupInfo: String? = null,
    /** Explicit shared identity for adjacent logical blocks whose subjects may differ. */
    val logicalBlockId: String? = null
)

@Entity(
    tableName = "recurring_private_lessons",
    foreignKeys = [ForeignKey(
        entity = Subject::class,
        parentColumns = ["id"],
        childColumns = ["subjectId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("subjectId"), Index("enabled")]
)
data class RecurringPrivateLesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val weekday: Int,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val intervalWeeks: Int = 1,
    val teacherOverride: String? = null,
    val roomOverride: String? = null,
    @ColumnInfo(name = "label")
    val privateLessonName: String? = null,
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "'UNSPECIFIED'")
    val privateLocationKind: PrivateLessonLocationKind = PrivateLessonLocationKind.UNSPECIFIED
)

/** Authoritative weekday membership for one recurring PRIVATE series. */
@Entity(
    tableName = "recurring_private_lesson_weekdays",
    primaryKeys = ["recurringPrivateLessonId", "weekday"],
    foreignKeys = [ForeignKey(
        entity = RecurringPrivateLesson::class,
        parentColumns = ["id"],
        childColumns = ["recurringPrivateLessonId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("weekday")]
)
data class RecurringPrivateLessonWeekday(
    val recurringPrivateLessonId: Long,
    val weekday: Int
)

@Entity(
    tableName = "lesson_instances",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ScheduleSlot::class,
            parentColumns = ["id"],
            childColumns = ["sourceScheduleSlotId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = RecurringPrivateLesson::class,
            parentColumns = ["id"],
            childColumns = ["sourcePrivateLessonId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("actualDate"),
        Index("subjectId"),
        Index("sourceScheduleSlotId"),
        Index("sourcePrivateLessonId"),
        Index(value = ["sourceScheduleSlotId", "actualDate"], unique = true),
        Index(value = ["sourcePrivateLessonId", "actualDate"], unique = true)
    ]
)
data class LessonInstance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actualDate: LocalDate,
    val actualStartTime: LocalTime,
    val actualEndTime: LocalTime,
    val subjectId: Long?,
    val sourceScheduleSlotId: Long? = null,
    val sourcePrivateLessonId: Long? = null,
    val kind: LessonKind,
    val cancellationState: CancellationState = CancellationState.ACTIVE,
    val actualTeacher: String? = null,
    val actualRoom: String? = null,
    val topic: String? = null,
    val notes: String? = null,
    val privateLessonName: String? = null,
    @ColumnInfo(defaultValue = "'UNSPECIFIED'")
    val privateLocationKind: PrivateLessonLocationKind = PrivateLessonLocationKind.UNSPECIFIED
)

@Entity(tableName = "cycle_configuration")
data class CycleConfiguration(
    @PrimaryKey val id: Int = 1,
    val anchorMonday: LocalDate,
    val anchorTemplateId: Long
)

@Entity(
    tableName = "cycle_entries",
    foreignKeys = [
        ForeignKey(
            entity = ScheduleTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["position"], unique = true), Index("templateId")]
)
data class CycleEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    val templateId: Long
)

@Entity(tableName = "day_exceptions")
data class DayException(
    @PrimaryKey val date: LocalDate,
    val type: DayExceptionType,
    val title: String? = null,
    val reason: String? = null,
    val note: String? = null
)

@Entity(tableName = "timetable_events", indices = [Index("date")])
data class TimetableEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val title: String,
    val description: String? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val isAllDay: Boolean = false
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = Subject::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LessonInstance::class,
            parentColumns = ["id"],
            childColumns = ["originatingLessonInstanceId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LessonInstance::class,
            parentColumns = ["id"],
            childColumns = ["dueLessonInstanceId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = LessonInstance::class,
            parentColumns = ["id"],
            childColumns = ["intendedDueLessonInstanceId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("subjectId"), Index("originatingLessonInstanceId"), Index("dueLessonInstanceId"), Index("intendedDueLessonInstanceId"), Index("status")]
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val subjectId: Long? = null,
    val originatingLessonInstanceId: Long? = null,
    val dueLessonInstanceId: Long? = null,
    val type: TaskType,
    val priority: TaskPriority,
    val status: TaskStatus = TaskStatus.PENDING,
    val dueAt: LocalDateTime? = null,
    val completedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime,
    val intendedDueLessonInstanceId: Long? = null
)

@Entity(
    tableName = "task_checklist_items",
    foreignKeys = [ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index(value = ["taskId", "position"], unique = true)]
)
data class TaskChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val text: String,
    val isCompleted: Boolean = false,
    val position: Int
)

@Entity(
    tableName = "task_reminders",
    foreignKeys = [ForeignKey(entity = Task::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId"), Index("enabled")]
)
data class TaskReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val kind: TaskReminderKind,
    val customTriggerAt: LocalDateTime? = null,
    val enabled: Boolean = true,
    val createdAt: LocalDateTime
)

/** Receiver-side provenance only.  The share id is never a Task identity. */
@Entity(tableName = "imported_task_shares", indices = [Index(value = ["shareId"], unique = true), Index("taskId")])
data class ImportedTaskShare(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val shareId: String,
    val taskId: Long,
    val importedAt: LocalDateTime
)
