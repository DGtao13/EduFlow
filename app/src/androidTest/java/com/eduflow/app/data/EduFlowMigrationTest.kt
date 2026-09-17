package com.eduflow.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduflow.app.data.local.EduFlowDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EduFlowMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), EduFlowDatabase::class.java)

    @Test fun v7ToV8AddsIntendedDueAndPreservesExistingTaskIdAndDue() {
        val name = "migration-v7-v8-test.db"
        helper.createDatabase(name, 7).apply {
            execSQL("INSERT INTO subjects (id, name, shortName, defaultTeacher, defaultRoom, color) VALUES (1, 'Математика', NULL, NULL, NULL, 0)")
            execSQL("INSERT INTO lesson_instances (id, actualDate, actualStartTime, actualEndTime, subjectId, sourceScheduleSlotId, sourcePrivateLessonId, kind, cancellationState, actualTeacher, actualRoom, topic, notes, privateLessonName, privateLocationKind) VALUES (10, '2026-09-21', '08:00', '08:45', 1, NULL, NULL, 'SCHOOL', 'ACTIVE', NULL, NULL, NULL, NULL, NULL, 'UNSPECIFIED')")
            execSQL("INSERT INTO recurring_private_lessons (id, subjectId, weekday, startTime, endTime, startDate, endDate, intervalWeeks, teacherOverride, roomOverride, label, enabled, privateLocationKind) VALUES (30, 1, 2, '16:00', '17:00', '2026-09-15', NULL, 1, NULL, NULL, 'Подготовка', 1, 'ONLINE')")
            execSQL("INSERT INTO lesson_instances (id, actualDate, actualStartTime, actualEndTime, subjectId, sourceScheduleSlotId, sourcePrivateLessonId, kind, cancellationState, actualTeacher, actualRoom, topic, notes, privateLessonName, privateLocationKind) VALUES (11, '2026-09-22', '16:00', '17:00', 1, NULL, 30, 'PRIVATE', 'ACTIVE', NULL, NULL, NULL, NULL, 'Подготовка', 'ONLINE')")
            execSQL("INSERT INTO tasks (id, title, description, subjectId, originatingLessonInstanceId, dueLessonInstanceId, type, priority, status, dueAt, completedAt, createdAt) VALUES (20, 'Домашна', NULL, 1, NULL, 10, 'HOMEWORK', 'MUST', 'PENDING', '2026-09-21T08:00', NULL, '2026-09-20T09:00')")
            execSQL("INSERT INTO tasks (id, title, description, subjectId, originatingLessonInstanceId, dueLessonInstanceId, type, priority, status, dueAt, completedAt, createdAt) VALUES (21, 'Частна задача', NULL, 1, NULL, 11, 'HOMEWORK', 'MUST', 'PENDING', '2026-09-22T16:00', NULL, '2026-09-20T09:00')")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 8, true, EduFlowDatabase.MIGRATION_7_8)
        migrated.query("SELECT id, dueLessonInstanceId, intendedDueLessonInstanceId FROM tasks WHERE id = 20").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(20L, cursor.getLong(0))
            assertEquals(10L, cursor.getLong(1))
            assertEquals(10L, cursor.getLong(2))
        }
        migrated.query("SELECT dueLessonInstanceId, intendedDueLessonInstanceId FROM tasks WHERE id = 21").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(11L, cursor.getLong(0))
            assertEquals(true, cursor.isNull(1))
        }
        migrated.close()
    }

    @Test fun v10ToV11AddsShareProvenanceWithoutTouchingTasks() {
        val name = "migration-v10-v11-test.db"
        helper.createDatabase(name, 10).apply {
            execSQL("INSERT INTO tasks (id, title, description, subjectId, originatingLessonInstanceId, dueLessonInstanceId, intendedDueLessonInstanceId, type, priority, status, dueAt, completedAt, createdAt) VALUES (1, 'Запази ме', NULL, NULL, NULL, NULL, NULL, 'HOMEWORK', 'MUST', 'PENDING', NULL, NULL, '2026-09-20T09:00')")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 11, true, EduFlowDatabase.MIGRATION_10_11)
        migrated.query("SELECT title FROM tasks WHERE id = 1").use { cursor -> assertEquals(true, cursor.moveToFirst()); assertEquals("Запази ме", cursor.getString(0)) }
        migrated.query("SELECT name FROM sqlite_master WHERE type='table' AND name='imported_task_shares'").use { cursor -> assertEquals(true, cursor.moveToFirst()) }
        migrated.close()
    }

    @Test fun v11ToV12MovesEachLegacyPrivateWeekdayIntoSourceOwnedMembership() {
        val name = "migration-v11-v12-test.db"
        helper.createDatabase(name, 11).apply {
            execSQL("INSERT INTO subjects (id, name, shortName, defaultTeacher, defaultRoom, color) VALUES (1, 'Математика', NULL, NULL, NULL, 0)")
            execSQL("INSERT INTO recurring_private_lessons (id, subjectId, weekday, startTime, endTime, startDate, endDate, intervalWeeks, teacherOverride, roomOverride, label, enabled, privateLocationKind) VALUES (30, 1, 1, '16:00', '17:00', '2026-09-14', NULL, 1, NULL, NULL, 'Подготовка', 1, 'ONLINE')")
            execSQL("INSERT INTO lesson_instances (id, actualDate, actualStartTime, actualEndTime, subjectId, sourceScheduleSlotId, sourcePrivateLessonId, kind, cancellationState, actualTeacher, actualRoom, topic, notes, privateLessonName, privateLocationKind) VALUES (31, '2026-09-14', '16:00', '17:00', 1, NULL, 30, 'PRIVATE', 'ACTIVE', NULL, NULL, NULL, NULL, 'Подготовка', 'ONLINE')")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 12, true, EduFlowDatabase.MIGRATION_11_12)
        migrated.query("SELECT recurringPrivateLessonId, weekday FROM recurring_private_lesson_weekdays").use { cursor ->
            assertEquals(true, cursor.moveToFirst()); assertEquals(30L, cursor.getLong(0)); assertEquals(1, cursor.getInt(1))
        }
        migrated.query("SELECT sourcePrivateLessonId FROM lesson_instances WHERE id = 31").use { cursor -> assertEquals(true, cursor.moveToFirst()); assertEquals(30L, cursor.getLong(0)) }
        migrated.close()
    }
}
