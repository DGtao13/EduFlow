package com.eduflow.app.data.local

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class EduFlowConverters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let(LocalTime::parse)

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)

    @TypeConverter
    fun fromLessonKind(value: LessonKind?): String? = value?.name

    @TypeConverter
    fun toLessonKind(value: String?): LessonKind? = value?.let(LessonKind::valueOf)

    @TypeConverter
    fun fromPrivateLessonLocationKind(value: PrivateLessonLocationKind?): String? = value?.name

    @TypeConverter
    fun toPrivateLessonLocationKind(value: String?): PrivateLessonLocationKind? = value?.let(PrivateLessonLocationKind::valueOf)

    @TypeConverter fun fromTaskReminderKind(value: TaskReminderKind?): String? = value?.name
    @TypeConverter fun toTaskReminderKind(value: String?): TaskReminderKind? = value?.let(TaskReminderKind::valueOf)

    @TypeConverter
    fun fromCancellationState(value: CancellationState?): String? = value?.name

    @TypeConverter
    fun toCancellationState(value: String?): CancellationState? = value?.let(CancellationState::valueOf)

    @TypeConverter
    fun fromDayExceptionType(value: DayExceptionType?): String? = value?.name

    @TypeConverter
    fun toDayExceptionType(value: String?): DayExceptionType? = value?.let(DayExceptionType::valueOf)

    @TypeConverter
    fun fromTaskType(value: TaskType?): String? = value?.name

    @TypeConverter
    fun toTaskType(value: String?): TaskType? = value?.let(TaskType::valueOf)

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority?): String? = value?.name

    @TypeConverter
    fun toTaskPriority(value: String?): TaskPriority? = value?.let(TaskPriority::valueOf)

    @TypeConverter
    fun fromTaskStatus(value: TaskStatus?): String? = value?.name

    @TypeConverter
    fun toTaskStatus(value: String?): TaskStatus? = value?.let(TaskStatus::valueOf)
}
