package com.eduflow.app.data

import android.app.Application
import androidx.room.Room
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.RecurringPrivateLesson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PrivateLessonEditorSavePathTest {
    @Test fun multiWeekdayReplacementIsSourceOwnedIdempotentAndPreservesPast() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, EduFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val monday = LocalDate.of(2026, 9, 14)
            val repository = PrivateLessonRepository(db)
            val source = RecurringPrivateLesson(subjectId = null, weekday = 1, startTime = LocalTime.of(17, 0), endTime = LocalTime.of(18, 0), startDate = monday, privateLessonName = "Английски")
            val id = repository.save(source, setOf(1, 3), monday)
            repository.materializeWeek(monday); repository.materializeWeek(monday.plusWeeks(1)); repository.materializeWeek(monday.plusWeeks(1))
            assertEquals(setOf(1, 3), repository.selectedWeekdays(id))
            assertEquals(1, db.recurringPrivateLessonDao().getAll().size)
            assertEquals(4, ownedDates(db, id).size)

            // Monday + Wednesday -> Tuesday + Thursday removes only future source-owned rows.
            db.lessonInstanceDao().upsert(LessonInstance(actualDate = monday.minusWeeks(1), actualStartTime = LocalTime.of(17, 0), actualEndTime = LocalTime.of(18, 0), subjectId = null, sourcePrivateLessonId = id, kind = com.eduflow.app.data.local.LessonKind.PRIVATE))
            repository.save(source.copy(id = id), setOf(2, 4), monday)
            repository.materializeWeek(monday.plusWeeks(1))
            assertEquals(setOf(2, 4), repository.selectedWeekdays(id))
            assertTrue(ownedDates(db, id).filter { !it.isBefore(monday) }.all { it.dayOfWeek.value in setOf(2, 4) })
            assertTrue(ownedDates(db, id).contains(monday.minusWeeks(1)))

            repository.setEnabled(db.recurringPrivateLessonDao().getById(id)!!, false, monday)
            assertTrue(ownedDates(db, id).filter { !it.isBefore(monday) }.isEmpty())
            repository.setEnabled(db.recurringPrivateLessonDao().getById(id)!!, true, monday)
            assertEquals(1, db.recurringPrivateLessonDao().getAll().size)
            assertTrue(ownedDates(db, id).any { !it.isBefore(monday) && it.dayOfWeek.value in setOf(2, 4) })
        } finally { db.close() }
    }

    @Test fun removingWeekdaysAndDeletingSeriesNeverTouchesUnrelatedPrivateLessons() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, EduFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val monday = LocalDate.of(2026, 9, 14)
            val repository = PrivateLessonRepository(db)
            val source = RecurringPrivateLesson(subjectId = null, weekday = 1, startTime = LocalTime.of(17, 0), endTime = LocalTime.of(18, 0), startDate = monday, privateLessonName = "Серия")
            val sourceId = repository.save(source, setOf(1, 3, 5), monday)
            val unrelated = db.lessonInstanceDao().upsert(LessonInstance(actualDate = monday.plusDays(1), actualStartTime = LocalTime.NOON, actualEndTime = LocalTime.NOON.plusHours(1), subjectId = null, kind = com.eduflow.app.data.local.LessonKind.PRIVATE, privateLessonName = "Еднократен"))
            repository.materializeWeek(monday)
            repository.save(source.copy(id = sourceId), setOf(3), monday)
            assertEquals(setOf(3), repository.selectedWeekdays(sourceId))
            assertTrue(ownedDates(db, sourceId).all { it.dayOfWeek.value == 3 })
            assertTrue(ownedDates(db, sourceId).contains(monday.plusDays(2)))
            repository.deleteSeries(db.recurringPrivateLessonDao().getById(sourceId)!!, monday)
            assertNull(db.recurringPrivateLessonDao().getById(sourceId))
            assertTrue(ownedDates(db, sourceId).isEmpty())
            assertEquals(unrelated, db.lessonInstanceDao().getById(unrelated)?.id)
        } finally { db.close() }
    }

    @Test fun editorSaveImmediatelyReconcilesOwnedFutureRowsAndDeleteTargetsTheSource() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val db = Room.inMemoryDatabaseBuilder(context, EduFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val monday = LocalDate.of(2026, 9, 14)
            val source = RecurringPrivateLesson(
                subjectId = null, weekday = 1, startTime = LocalTime.of(17, 0), endTime = LocalTime.of(18, 0),
                startDate = monday, privateLessonName = "Английски"
            )
            val repository = PrivateLessonRepository(db)
            val sourceId = repository.save(source, monday)
            repository.materializeWeek(monday)
            repository.materializeWeek(monday.plusWeeks(1))
            assertEquals(listOf(monday, monday.plusWeeks(1)), ownedDates(db, sourceId))

            // This is the transaction used by PrivateLessonEditorViewModel.save;
            // Room is queried before any later UI/materializer/toggle action.
            repository.save(source.copy(id = sourceId, weekday = 3), monday)

            assertEquals(3, db.recurringPrivateLessonDao().getById(sourceId)?.weekday)
            assertEquals(1, db.recurringPrivateLessonDao().getAll().size)
            assertFalse(ownedDates(db, sourceId).any { it.dayOfWeek.value == 1 })
            assertEquals(listOf(monday.plusDays(2), monday.plusWeeks(1).plusDays(2)), ownedDates(db, sourceId))

            repository.deleteSeries(db.recurringPrivateLessonDao().getById(sourceId)!!, monday)
            assertNull(db.recurringPrivateLessonDao().getById(sourceId))
            assertTrue(ownedDates(db, sourceId).isEmpty())
        } finally {
            db.close()
        }
    }

    private suspend fun ownedDates(db: EduFlowDatabase, sourceId: Long): List<LocalDate> =
        db.lessonInstanceDao().getForPrivateSourceInRange(sourceId, LocalDate.of(2026, 1, 1), LocalDate.of(2100, 1, 1))
            .map(LessonInstance::actualDate)
}
