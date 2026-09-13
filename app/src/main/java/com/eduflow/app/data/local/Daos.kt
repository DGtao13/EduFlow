package com.eduflow.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface SubjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(subject: Subject): Long
    @Update suspend fun update(subject: Subject)
    @Delete suspend fun delete(subject: Subject)
    @Query("SELECT * FROM subjects ORDER BY name") fun observeAll(): Flow<List<Subject>>
    @Query("SELECT * FROM subjects WHERE id = :id") fun observeById(id: Long): Flow<Subject?>
    @Query("SELECT * FROM subjects") suspend fun getAll(): List<Subject>
    @Query("DELETE FROM subjects") suspend fun clear()
}

@Dao
interface ScheduleTemplateDao {
    @Upsert suspend fun upsert(template: ScheduleTemplate): Long
    @Update suspend fun update(template: ScheduleTemplate)
    @Delete suspend fun delete(template: ScheduleTemplate)
    @Query("SELECT * FROM schedule_templates ORDER BY name") fun observeAll(): Flow<List<ScheduleTemplate>>
    @Query("SELECT * FROM schedule_templates WHERE id = :id") fun observeById(id: Long): Flow<ScheduleTemplate?>
    @Query("SELECT * FROM schedule_templates") suspend fun getAll(): List<ScheduleTemplate>
    @Query("DELETE FROM schedule_templates") suspend fun clear()
}

@Dao
interface ScheduleSlotDao {
    @Upsert suspend fun upsert(slot: ScheduleSlot): Long
    @Update suspend fun update(slot: ScheduleSlot)
    @Delete suspend fun delete(slot: ScheduleSlot)
    @Query("SELECT * FROM schedule_slots WHERE scheduleTemplateId = :templateId ORDER BY weekday, lessonIndex, startTime")
    fun observeForTemplate(templateId: Long): Flow<List<ScheduleSlot>>
    @Query("SELECT * FROM schedule_slots WHERE scheduleTemplateId = :templateId ORDER BY weekday, lessonIndex, startTime")
    suspend fun getForTemplate(templateId: Long): List<ScheduleSlot>
    @Query("SELECT * FROM schedule_slots WHERE id = :id") fun observeById(id: Long): Flow<ScheduleSlot?>
    @Query("SELECT * FROM schedule_slots") fun observeAll(): Flow<List<ScheduleSlot>>
    @Query("SELECT * FROM schedule_slots") suspend fun getAll(): List<ScheduleSlot>
    @Query("SELECT COUNT(*) FROM schedule_slots WHERE subjectId = :subjectId") suspend fun countForSubject(subjectId: Long): Int
    @Query("DELETE FROM schedule_slots") suspend fun clear()
}

@Dao
interface RecurringPrivateLessonDao {
    @Upsert suspend fun upsert(lesson: RecurringPrivateLesson): Long
    @Update suspend fun update(lesson: RecurringPrivateLesson)
    @Delete suspend fun delete(lesson: RecurringPrivateLesson)
    @Query("SELECT * FROM recurring_private_lessons ORDER BY enabled DESC, weekday, startTime") fun observeAll(): Flow<List<RecurringPrivateLesson>>
    @Query("SELECT * FROM recurring_private_lessons WHERE id = :id") fun observeById(id: Long): Flow<RecurringPrivateLesson?>
    @Query("SELECT * FROM recurring_private_lessons") suspend fun getAll(): List<RecurringPrivateLesson>
    @Query("SELECT * FROM recurring_private_lessons WHERE id = :id") suspend fun getById(id: Long): RecurringPrivateLesson?
    @Query("DELETE FROM recurring_private_lessons") suspend fun clear()
}

@Dao
interface LessonInstanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(lesson: LessonInstance): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertIfAbsent(lesson: LessonInstance): Long
    @Update suspend fun update(lesson: LessonInstance)
    @Query("UPDATE lesson_instances SET cancellationState = :state WHERE id IN (:ids)")
    suspend fun updateCancellationStates(ids: List<Long>, state: CancellationState)
    @Delete suspend fun delete(lesson: LessonInstance)
    @Query("SELECT * FROM lesson_instances WHERE id = :id") fun observeById(id: Long): Flow<LessonInstance?>
    @Query("SELECT * FROM lesson_instances WHERE actualDate = :date ORDER BY actualStartTime")
    fun observeForDate(date: LocalDate): Flow<List<LessonInstance>>
    @Query("SELECT * FROM lesson_instances WHERE actualDate = :date ORDER BY actualStartTime")
    suspend fun getForDate(date: LocalDate): List<LessonInstance>
    @Query("SELECT * FROM lesson_instances WHERE actualDate BETWEEN :from AND :to ORDER BY actualDate, actualStartTime")
    fun observeForDateRange(from: LocalDate, to: LocalDate): Flow<List<LessonInstance>>
    @Query("SELECT * FROM lesson_instances WHERE actualDate BETWEEN :from AND :to ORDER BY actualDate, actualStartTime")
    suspend fun getForDateRange(from: LocalDate, to: LocalDate): List<LessonInstance>
    @Query("SELECT * FROM lesson_instances WHERE id = :id") suspend fun getById(id: Long): LessonInstance?
    @Query("SELECT * FROM lesson_instances WHERE subjectId = :subjectId AND actualDate BETWEEN :from AND :to ORDER BY actualDate, actualStartTime")
    suspend fun getForSubjectInRange(subjectId: Long, from: LocalDate, to: LocalDate): List<LessonInstance>
    @Query("SELECT * FROM lesson_instances WHERE kind = 'PRIVATE' AND actualDate BETWEEN :from AND :to ORDER BY actualDate, actualStartTime")
    fun observePrivateForDateRange(from: LocalDate, to: LocalDate): Flow<List<LessonInstance>>
    @Query("SELECT * FROM lesson_instances WHERE sourcePrivateLessonId = :sourceId AND actualDate BETWEEN :from AND :to ORDER BY actualDate, actualStartTime")
    suspend fun getForPrivateSourceInRange(sourceId: Long, from: LocalDate, to: LocalDate): List<LessonInstance>
    @Query("SELECT * FROM lesson_instances WHERE subjectId = :subjectId ORDER BY actualDate DESC, actualStartTime DESC")
    fun observeForSubject(subjectId: Long): Flow<List<LessonInstance>>
    @Query("SELECT * FROM lesson_instances") fun observeAll(): Flow<List<LessonInstance>>
    @Query("SELECT * FROM lesson_instances") suspend fun getAll(): List<LessonInstance>
    @Query("DELETE FROM lesson_instances") suspend fun clear()
}

@Dao
interface CycleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveConfiguration(configuration: CycleConfiguration)
    @Query("SELECT * FROM cycle_configuration WHERE id = 1") fun observeConfiguration(): Flow<CycleConfiguration?>
    @Query("SELECT * FROM cycle_configuration WHERE id = 1") suspend fun getConfiguration(): CycleConfiguration?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEntries(entries: List<CycleEntry>)
    @Query("DELETE FROM cycle_entries") suspend fun clearEntries()
    @Query("SELECT * FROM cycle_entries ORDER BY position") fun observeEntries(): Flow<List<CycleEntry>>
    @Query("SELECT * FROM cycle_entries ORDER BY position") suspend fun getEntries(): List<CycleEntry>
    @Query("DELETE FROM cycle_configuration") suspend fun clearConfiguration()
}

@Dao
interface DayExceptionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(exception: DayException)
    @Delete suspend fun delete(exception: DayException)
    @Query("DELETE FROM day_exceptions WHERE date = :date") suspend fun deleteForDate(date: LocalDate)
    @Query("SELECT * FROM day_exceptions WHERE date = :date") suspend fun getByDate(date: LocalDate): DayException?
    @Query("SELECT * FROM day_exceptions WHERE date = :date") fun observeForDate(date: LocalDate): Flow<DayException?>
    @Query("SELECT * FROM day_exceptions WHERE date BETWEEN :from AND :to ORDER BY date")
    fun observeForDateRange(from: LocalDate, to: LocalDate): Flow<List<DayException>>
    @Query("SELECT * FROM day_exceptions WHERE date BETWEEN :from AND :to") suspend fun getForDateRange(from: LocalDate, to: LocalDate): List<DayException>
    @Query("SELECT * FROM day_exceptions") suspend fun getAll(): List<DayException>
    @Query("DELETE FROM day_exceptions") suspend fun clear()
}

@Dao
interface TimetableEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(event: TimetableEvent): Long
    @Update suspend fun update(event: TimetableEvent)
    @Delete suspend fun delete(event: TimetableEvent)
    @Query("SELECT * FROM timetable_events WHERE date = :date ORDER BY startTime, title")
    fun observeForDate(date: LocalDate): Flow<List<TimetableEvent>>
    @Query("SELECT * FROM timetable_events WHERE date BETWEEN :from AND :to ORDER BY date, startTime, title")
    fun observeForDateRange(from: LocalDate, to: LocalDate): Flow<List<TimetableEvent>>
    @Query("SELECT * FROM timetable_events") suspend fun getAll(): List<TimetableEvent>
    @Query("DELETE FROM timetable_events") suspend fun clear()
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(task: Task): Long
    @Update suspend fun update(task: Task)
    @Delete suspend fun delete(task: Task)
    @Query("SELECT * FROM tasks WHERE id = :id") fun observeById(id: Long): Flow<Task?>
    @Query("SELECT * FROM tasks ORDER BY dueAt, createdAt") fun observeAll(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE status = 'PENDING' ORDER BY dueAt, priority") fun observePending(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE status = 'COMPLETED' ORDER BY completedAt DESC") fun observeCompleted(): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE subjectId = :subjectId ORDER BY dueAt, createdAt") fun observeForSubject(subjectId: Long): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE originatingLessonInstanceId = :lessonId ORDER BY dueAt, createdAt")
    fun observeForLesson(lessonId: Long): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE dueLessonInstanceId = :lessonId ORDER BY dueAt, createdAt") fun observeDueForLesson(lessonId: Long): Flow<List<Task>>
    @Query("SELECT * FROM tasks WHERE status = 'PENDING' AND dueLessonInstanceId IN (:lessonIds)") suspend fun getPendingDueForLessonIds(lessonIds: List<Long>): List<Task>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun getById(id: Long): Task?
    @Query("SELECT * FROM tasks WHERE status = 'PENDING'") suspend fun getPending(): List<Task>
    @Query("SELECT * FROM tasks") suspend fun getAll(): List<Task>
    @Query("DELETE FROM tasks") suspend fun clear()
}

@Dao
interface TaskReminderDao {
    @Query("SELECT * FROM task_reminders") fun observeAll(): Flow<List<TaskReminder>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(reminder: TaskReminder): Long
    @Update suspend fun update(reminder: TaskReminder)
    @Delete suspend fun delete(reminder: TaskReminder)
    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId ORDER BY createdAt, id") fun observeForTask(taskId: Long): Flow<List<TaskReminder>>
    @Query("SELECT * FROM task_reminders WHERE taskId = :taskId ORDER BY createdAt, id") suspend fun getForTask(taskId: Long): List<TaskReminder>
    @Query("SELECT * FROM task_reminders WHERE id = :id") suspend fun getById(id: Long): TaskReminder?
    @Query("SELECT * FROM task_reminders") suspend fun getAll(): List<TaskReminder>
    @Query("DELETE FROM task_reminders") suspend fun clear()
}

@Dao
interface TaskChecklistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(item: TaskChecklistItem): Long
    @Update suspend fun update(item: TaskChecklistItem)
    @Delete suspend fun delete(item: TaskChecklistItem)
    @Query("SELECT * FROM task_checklist_items WHERE taskId = :taskId ORDER BY position, id") fun observeForTask(taskId: Long): Flow<List<TaskChecklistItem>>
    @Query("SELECT * FROM task_checklist_items ORDER BY taskId, position, id") fun observeAll(): Flow<List<TaskChecklistItem>>
    @Query("SELECT * FROM task_checklist_items WHERE taskId = :taskId ORDER BY position, id") suspend fun getForTask(taskId: Long): List<TaskChecklistItem>
    @Query("SELECT * FROM task_checklist_items") suspend fun getAll(): List<TaskChecklistItem>
    @Query("DELETE FROM task_checklist_items") suspend fun clear()
}
