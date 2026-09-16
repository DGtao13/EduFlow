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
