package com.eduflow.app.data.local

enum class LessonKind { SCHOOL, PRIVATE }
enum class PrivateLessonLocationKind { UNSPECIFIED, IN_PERSON, ONLINE }
enum class TaskReminderKind { AT_DEADLINE, ONE_HOUR_BEFORE, ONE_DAY_BEFORE, THREE_DAYS_BEFORE, CUSTOM }

enum class CancellationState { ACTIVE, CANCELLED }

enum class DayExceptionType { NO_SCHOOL }

enum class TaskType { HOMEWORK, ASSIGNMENT, PRESENTATION, REVISION, GENERAL }

enum class TaskPriority { MUST, SHOULD, OPTIONAL }

enum class TaskStatus { PENDING, COMPLETED }
