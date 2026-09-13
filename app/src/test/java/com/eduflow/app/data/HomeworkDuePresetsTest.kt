package com.eduflow.app.data

import com.eduflow.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class HomeworkDuePresetsTest {
    private val monday = LocalDate.of(2026,9,21)
    private val year = AcademicYearSettings(monday, monday.plusMonths(3))
    private fun source(id: Long, weekday: Int, period: Int, subject: Long, block: String) = ScheduleSlot(id,1,weekday,period,
        LocalTime.of(9,0).plusMinutes((period-1)*45L),LocalTime.of(9,45).plusMinutes((period-1)*45L),subject,logicalBlockId=block)
    private val slots = listOf(source(1,1,1,1,"origin"),source(2,1,2,2,"origin"),source(3,3,1,1,"next"),source(4,3,2,1,"next"),source(5,5,1,1,"second"),source(6,5,2,2,"second"))
    private val lessons = slots.map { s -> LessonInstance(s.id,monday.plusDays((s.weekday-1).toLong()),s.startTime,s.endTime,s.subjectId,s.id,kind=LessonKind.SCHOOL) }
    private suspend fun resolve(subject: Long, origin: LessonInstance?, ordinal: Int, dated: List<LessonInstance> = lessons) =
        HomeworkDuePresets.resolve(subject, origin?.let { LocalDateTime.of(it.actualDate,it.actualStartTime) } ?: monday.atTime(12,0), origin,ordinal) { id,after,from ->
            if (from == null) NextLessonResolver.chooseUpcomingSchoolSession(id,dated,slots,after,emptySet(),year)
            else NextLessonResolver.chooseSchoolBlockAware(from,id,dated,slots,emptySet(),year)
        }
    @Test fun newHomeworkDefaultsOnlyWhenSubjectContextIsUsableAndUserHasNotOverridden() {
        assertTrue(HomeworkDuePresets.defaultToNext(true,TaskType.HOMEWORK,1,slots,false))
        assertFalse(HomeworkDuePresets.defaultToNext(false,TaskType.HOMEWORK,1,slots,false))
        assertFalse(HomeworkDuePresets.defaultToNext(true,TaskType.HOMEWORK,null,slots,false))
        assertFalse(HomeworkDuePresets.defaultToNext(true,TaskType.HOMEWORK,99,slots,false))
        assertFalse(HomeworkDuePresets.defaultToNext(true,TaskType.HOMEWORK,1,slots,true))
    }
    @Test fun mixedOriginAndFutureBlocksCountAsLogicalSessionsNotMembers() = runBlocking {
        assertEquals(3L,resolve(1,lessons.first(),1)?.id)
        assertEquals(5L,resolve(1,lessons.first(),2)?.id)
        assertEquals(5L,resolve(1,lessons[1],2)?.id)
        assertEquals(1L,lessons.first().subjectId)
    }
    @Test fun sameSubjectOriginSkipsItsEntireSession() = runBlocking {
        val same = lessons.map { it.copy(subjectId=1) }
        assertEquals(3L,resolve(1,same.first(),1,same)?.id)
        assertEquals(5L,resolve(1,same.first(),2,same)?.id)
    }
    @Test fun outsideLessonContextSelectsFirstAndSecondUpcomingSessionsWithoutInventingOrigin() = runBlocking {
        assertEquals(3L,resolve(1,null,1)?.id)
        assertEquals(5L,resolve(1,null,2)?.id)
    }
    @Test fun changingSubjectRecalculatesInsteadOfRetainingOldDestination() = runBlocking {
        assertEquals(3L,resolve(1,lessons.first(),1)?.id)
        assertEquals(5L,resolve(2,lessons.first(),1)?.id)
    }
    @Test fun unavailableSecondSessionReturnsNullAndExistingTaskValueIsUntouched() = runBlocking {
        val stored = Task(id=10,title="Existing",subjectId=1,type=TaskType.HOMEWORK,priority=TaskPriority.MUST,createdAt=monday.atStartOfDay(),dueAt=monday.plusDays(1).atTime(16,0))
        val before = stored.copy()
        assertNull(resolve(1,lessons.first(),2,lessons.take(4)))
        assertEquals(before,stored)
    }
}
