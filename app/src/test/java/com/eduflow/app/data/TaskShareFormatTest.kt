package com.eduflow.app.data

import com.eduflow.app.data.local.Subject
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskChecklistItem
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TaskShareFormatTest {
    private val task = Task(title = "Домашно", description = "стр. 5", subjectId = 42, type = TaskType.HOMEWORK, priority = TaskPriority.MUST, dueAt = LocalDateTime.of(2026, 9, 20, 10, 30), createdAt = LocalDateTime.of(2026, 9, 15, 8, 0))

    @Test fun roundTripIsPortableAndDoesNotContainRoomIds() {
        val json = TaskShareCodec.encode(task, "Математика", listOf(TaskChecklistItem(id = 7, taskId = 42, text = "Упражнение", isCompleted = true, position = 0)), "ABSOLUTE")
        val result = TaskShareCodec.decode(json) as TaskShareReadResult.Success
        assertEquals(TaskShareFormat.FORMAT, result.envelope.format)
        assertEquals(1, result.envelope.version)
        assertEquals("TASK", result.envelope.itemType)
        assertEquals("Домашно", result.envelope.item.title)
        assertEquals(listOf("Упражнение"), result.envelope.item.checklist)
        assertFalse(json.contains("subjectId")); assertFalse(json.contains("dueLessonInstanceId")); assertFalse(json.contains("taskId"))
    }

    @Test fun unknownAdditiveFieldsAreIgnored() {
        val source = TaskShareCodec.encode(task, null, emptyList(), "ABSOLUTE").removeSuffix("}") + ",\"futureField\":true}"
        assertTrue(TaskShareCodec.decode(source) is TaskShareReadResult.Success)
    }

    @Test fun lessonRelativeDeadlineDoesNotExportSendersResolvedDate() {
        val decoded = TaskShareCodec.decode(TaskShareCodec.encode(task, "Математика", emptyList(), "SECOND_NEXT")) as TaskShareReadResult.Success
        assertEquals("SECOND_NEXT", decoded.envelope.item.deadline.kind)
        assertNull(decoded.envelope.item.deadline.dueAt)
    }

    @Test fun unsafeEnvelopeAndContentFailWithoutThrowing() {
        assertEquals(TaskShareReadResult.Malformed, TaskShareCodec.decode("{"))
        assertEquals(TaskShareReadResult.UnsupportedVersion, TaskShareCodec.decode("""{"format":"eduflow-share","version":2,"itemType":"TASK","shareId":"00000000-0000-0000-0000-000000000000","item":{"title":"x","type":"HOMEWORK","priority":"MUST"}}"""))
        assertEquals(TaskShareReadResult.UnsupportedItemType, TaskShareCodec.decode("""{"format":"eduflow-share","version":1,"itemType":"EVENT","shareId":"00000000-0000-0000-0000-000000000000","item":{"title":"x","type":"HOMEWORK","priority":"MUST"}}"""))
        assertEquals(TaskShareReadResult.InvalidContent, TaskShareCodec.decode("""{"format":"eduflow-share","version":1,"itemType":"TASK","shareId":"bad","item":{"title":"x","type":"BAD","priority":"MUST"}}"""))
    }

    @Test fun inboundMimeAndFilenameHintsOnlyRouteEduFlowArtifactsToTheExistingValidator() {
        val valid = TaskShareCodec.encode(task, "Математика", emptyList(), "ABSOLUTE")
        val custom = TaskShareIntentRouting.shouldOpen("android.intent.action.VIEW", TaskShareFormat.MIME_TYPE, null, "/opaque/123")
        assertTrue(custom)
        assertTrue(TaskShareCodec.decode(valid) is TaskShareReadResult.Success)

        val octetStream = TaskShareIntentRouting.shouldOpen("android.intent.action.VIEW", "application/octet-stream", "EduFlow-task.eduflowtask", "/document/42")
        assertTrue(octetStream)
        assertTrue(TaskShareCodec.decode(valid) is TaskShareReadResult.Success)

        val textTransfer = TaskShareIntentRouting.shouldOpen("android.intent.action.VIEW", "text/plain; charset=utf-8", "task.EDUFLOWTASK", null)
        assertTrue(textTransfer)
        assertFalse(TaskShareIntentRouting.shouldOpen("android.intent.action.VIEW", "application/json", "notes.json", "/Download/notes.json"))
        assertFalse(TaskShareIntentRouting.shouldOpen("android.intent.action.SEND", "application/octet-stream", "task.eduflowtask", null))
    }

    @Test fun extensionOnlyIsJustARoutingHintAndOutboundContractStaysStable() {
        assertTrue(TaskShareIntentRouting.shouldOpen("android.intent.action.VIEW", "application/octet-stream", "fake.eduflowtask", null))
        assertEquals(TaskShareReadResult.Malformed, TaskShareCodec.decode("not an EduFlow share"))
        assertEquals("eduflowtask", TaskShareFormat.EXTENSION)
        assertEquals("application/vnd.eduflow.task+json", TaskShareFormat.MIME_TYPE)
    }

    @Test fun subjectMappingIsConservative() {
        val one = listOf(Subject(id = 1, name = "  Математика ", color = 0))
        assertEquals(1L, SharedSubjectMapping.match("математика", one).selected?.id)
        assertNull(SharedSubjectMapping.match("Физика", one).selected)
        val duplicate = SharedSubjectMapping.match("Математика", one + Subject(id = 2, name = "математика", color = 1))
        assertTrue(duplicate.ambiguous); assertNull(duplicate.selected)
    }
}
