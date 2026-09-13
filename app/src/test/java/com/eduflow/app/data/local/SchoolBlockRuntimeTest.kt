package com.eduflow.app.data.local

import androidx.room.Room
import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.data.NextLessonResolver
import com.eduflow.app.domain.LessonBlockResolver
import com.eduflow.app.domain.TaskIndicatorAggregation
import com.eduflow.app.ui.lessonQuickActionKinds
import com.eduflow.app.ui.LessonQuickActionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SchoolBlockRuntimeTest {
    private lateinit var db: EduFlowDatabase
    private val date = LocalDate.of(2026, 9, 14)
    private fun slot(id: Long, period: Int, subject: Long = id) = ScheduleSlot(id, 1, 1, period,
        LocalTime.of(9, 50).plusMinutes((period - 3) * 45L), LocalTime.of(10, 35).plusMinutes((period - 3) * 45L), subject)
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), EduFlowDatabase::class.java).allowMainThreadQueries().build()
        db.subjectDao().upsert(Subject(1, "A", color = 1))
        db.subjectDao().upsert(Subject(2, "B", color = 2))
        db.scheduleTemplateDao().upsert(ScheduleTemplate(1, "A"))
        db.cycleDao().saveConfiguration(CycleConfiguration(anchorMonday = date, anchorTemplateId = 1))
        db.cycleDao().insertEntries(listOf(CycleEntry(position = 0, templateId = 1)))
    }
    @After fun close() { db.close() }
    private suspend fun seed(subject: Long = 2) {
        listOf(slot(1, 3), slot(2, 4, subject)).forEach { s ->
            db.scheduleSlotDao().upsert(s)
            db.lessonInstanceDao().insertIfAbsent(LessonInstance(s.id, date, s.startTime, s.endTime, s.subjectId, s.id,
                kind = LessonKind.SCHOOL, topic = "topic", notes = "notes"))
        }
    }
    @Test fun editingPersistedMixedSlotsPreservesDatedSourcesAndFeedsEveryBlockConsumer() = runBlocking {
        seed()
        val before = db.lessonInstanceDao().getForDate(date)
        db.scheduleSlotDao().getAll().forEach { db.scheduleSlotDao().upsert(it.copy(logicalBlockId = "linked")) }
        db.scheduleTemplateDao().upsert(ScheduleTemplate(1, "renamed"))
        val day = db.lessonInstanceDao().observeForDate(date).first()
        val slots = db.scheduleSlotDao().observeAll().first()
        assertEquals(before, day)
        val schedule = LessonBlockResolver.resolveAll(day, slots)
        day.forEach { selected ->
            val details = LessonBlockResolver.resolve(selected, day, slots)
            assertEquals(listOf(1L, 2L), details.ids)
            assertEquals(details, schedule[selected.id])
            assertTrue(details.isMultiPeriod)
            assertTrue(LessonQuickActionKind.CANCEL_BLOCK in lessonQuickActionKinds(selected, details))
            assertEquals(1L to 2L, TaskIndicatorAggregation.owners(details))
            assertTrue(selected.id in details.ids)
            assertEquals(LocalTime.of(9, 50), details.startTime)
            assertEquals(LocalTime.of(11, 20), details.endTime)
        }
        val future = day.first().copy(id = 3, actualDate = date.plusWeeks(1))
        assertEquals(3L, NextLessonResolver.chooseSchoolBlockAware(day.first(), 1, day + future, slots, emptySet(),
            AcademicYearSettings(date, date.plusMonths(3)))?.id)
        db.lessonInstanceDao().updateCancellationStates(schedule.getValue(1).ids, CancellationState.CANCELLED)
        assertTrue(db.lessonInstanceDao().getForDate(date).all { it.cancellationState == CancellationState.CANCELLED })
        db.lessonInstanceDao().updateCancellationStates(schedule.getValue(2).ids, CancellationState.ACTIVE)
        assertEquals(before, db.lessonInstanceDao().getForDate(date))
        slots.forEach { db.scheduleSlotDao().upsert(it.copy(logicalBlockId = null)) }
        val unlinked = LessonBlockResolver.resolveAll(db.lessonInstanceDao().getForDate(date), db.scheduleSlotDao().observeAll().first())
        assertTrue(unlinked.values.none { it.isMultiPeriod })
        day.forEach { assertFalse(LessonQuickActionKind.CANCEL_BLOCK in lessonQuickActionKinds(it, unlinked.getValue(it.id))) }
        assertEquals(listOf(1L, 2L), db.lessonInstanceDao().getForDate(date).map { it.sourceScheduleSlotId })
    }
    @Test fun sameSubjectUsesTheSamePersistedRuntimePath() = runBlocking {
        seed(1)
        db.scheduleSlotDao().getAll().forEach { db.scheduleSlotDao().upsert(it.copy(logicalBlockId = "linked")) }
        assertEquals(listOf(1L, 2L), LessonBlockResolver.resolveAll(db.lessonInstanceDao().getForDate(date), db.scheduleSlotDao().getAll()).getValue(2).ids)
        val day = db.lessonInstanceDao().getForDate(date)
        val future = day.first().copy(id = 3, actualDate = date.plusWeeks(1))
        val year = AcademicYearSettings(date, date.plusMonths(3))
        assertEquals(3L, NextLessonResolver.chooseSchoolBlockAware(day.first(), 1, day + future, db.scheduleSlotDao().getAll(), emptySet(), year)?.id)
        db.scheduleSlotDao().getAll().forEach { db.scheduleSlotDao().upsert(it.copy(logicalBlockId = null)) }
        assertEquals(2L, NextLessonResolver.chooseSchoolBlockAware(day.first(), 1, day + future, db.scheduleSlotDao().getAll(), emptySet(), year)?.id)
    }
    @Test fun formerReplaceFailureIsRepairedWithoutChangingHistoricalContent() = runBlocking {
        seed()
        val before = db.lessonInstanceDao().getById(2)!!
        db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO schedule_slots SELECT * FROM schedule_slots WHERE id = 2")
        assertNull(db.lessonInstanceDao().getById(2)!!.sourceScheduleSlotId)
        SchoolLessonSourceRepair.repair(db)
        assertEquals(before, db.lessonInstanceDao().getById(2))
        SchoolLessonSourceRepair.repair(db)
        assertEquals(before, db.lessonInstanceDao().getById(2))
    }
    @Test fun ambiguousOrphansAreNotReassignedOrDiscarded() = runBlocking {
        seed()
        val orphan = db.lessonInstanceDao().getById(2)!!.copy(sourceScheduleSlotId = null)
        db.lessonInstanceDao().update(orphan)
        db.lessonInstanceDao().insertIfAbsent(orphan.copy(id = 3))
        SchoolLessonSourceRepair.repair(db)
        assertEquals(orphan, db.lessonInstanceDao().getById(2))
        assertNull(db.lessonInstanceDao().getById(3)!!.sourceScheduleSlotId)
    }
    @Test fun reusedIdentityAcrossTemplatesOrDaysNeverCombinesMembers() = runBlocking {
        seed()
        val day = db.lessonInstanceDao().getForDate(date)
        val slots = db.scheduleSlotDao().getAll().map { it.copy(logicalBlockId = "reused") }
        listOf(slots.map { if (it.id == 2L) it.copy(scheduleTemplateId = 2) else it },
            slots.map { if (it.id == 2L) it.copy(weekday = 2) else it },
            slots.map { if (it.id == 2L) it.copy(lessonIndex = 5) else it }).forEach {
            assertTrue(LessonBlockResolver.resolveAll(day, it).values.none { block -> block.isMultiPeriod })
        }
    }
    @Test fun detachedDuplicatesCannotInterruptTheSourceBackedScheduleBlock() = runBlocking {
        seed()
        db.scheduleSlotDao().getAll().forEach { db.scheduleSlotDao().upsert(it.copy(logicalBlockId = "linked")) }
        val sibling = db.lessonInstanceDao().getById(2)!!
        db.lessonInstanceDao().insertIfAbsent(sibling.copy(id = 3, sourceScheduleSlotId = null))
        db.lessonInstanceDao().insertIfAbsent(sibling.copy(id = 4, sourceScheduleSlotId = null))
        val day = db.lessonInstanceDao().getForDate(date)
        val blocks = LessonBlockResolver.resolveAll(day, db.scheduleSlotDao().getAll())
        assertEquals(listOf(1L, 2L), blocks.getValue(1).ids)
        assertEquals(listOf(1L, 2L), blocks.getValue(2).ids)
        assertEquals(listOf(3L), blocks.getValue(3).ids)
        assertEquals(listOf(4L), blocks.getValue(4).ids)
        assertEquals(2L, LessonBlockResolver.scheduleMembersByStart(day, db.scheduleSlotDao().getAll()).getValue(sibling.actualStartTime).id)
        assertEquals(2L, LessonBlockResolver.scheduleMembersByStart(day.reversed(), db.scheduleSlotDao().getAll()).getValue(sibling.actualStartTime).id)
    }
    @Test fun activeCombinedDaoObservationSeesLinkAndUnlinkWithoutRestart() = runBlocking {
        seed()
        val emissions = Channel<Boolean>(Channel.UNLIMITED)
        val observer = launch(Dispatchers.Default) {
            combine(db.lessonInstanceDao().observeForDate(date), db.scheduleSlotDao().observeAll()) { day, slots ->
                LessonBlockResolver.resolveAll(day, slots).getValue(1).isMultiPeriod
            }.collect { emissions.send(it) }
        }
        try {
            assertFalse(withTimeout(5000) { emissions.receive() })
            db.scheduleSlotDao().getAll().forEach { db.scheduleSlotDao().upsert(it.copy(logicalBlockId = "live")) }
            withTimeout(5000) { while (!emissions.receive()) { } }
            db.scheduleSlotDao().getAll().forEach { db.scheduleSlotDao().upsert(it.copy(logicalBlockId = null)) }
            withTimeout(5000) { while (emissions.receive()) { } }
        } finally { observer.cancel(); emissions.close() }
    }
    @Test fun reopeningTheDatabasePreservesSourcesAndExplicitBlock() = runBlocking {
        seed()
        val context = RuntimeEnvironment.getApplication()
        val name = "block-regression-${java.util.UUID.randomUUID()}.db"
        val disk = Room.databaseBuilder(context, EduFlowDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            db.subjectDao().getAll().forEach { disk.subjectDao().upsert(it) }
            disk.scheduleTemplateDao().upsert(ScheduleTemplate(1, "A"))
            db.scheduleSlotDao().getAll().forEach { disk.scheduleSlotDao().upsert(it.copy(logicalBlockId = "disk")) }
            db.lessonInstanceDao().getAll().forEach { disk.lessonInstanceDao().insertIfAbsent(it) }
        } finally { disk.close() }
        val reopened = Room.databaseBuilder(context, EduFlowDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            assertEquals(listOf(1L, 2L), LessonBlockResolver.resolveAll(reopened.lessonInstanceDao().getForDate(date),
                reopened.scheduleSlotDao().getAll()).getValue(2).ids)
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
}
