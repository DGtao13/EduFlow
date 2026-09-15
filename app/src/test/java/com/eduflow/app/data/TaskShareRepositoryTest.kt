package com.eduflow.app.data

import android.app.Application
import android.net.Uri
import androidx.room.Room
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskPriority
import com.eduflow.app.data.local.TaskType
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskShareRepositoryTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun readingForPreviewDoesNotPersistAndOversizedStreamIsRejected() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, EduFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val file = File(context.cacheDir, "task-share-preview-test.eduflowtask")
        try {
            val valid = TaskShareCodec.encode(
                Task(title = "Preview only", type = TaskType.HOMEWORK, priority = TaskPriority.MUST, createdAt = LocalDateTime.now()),
                null,
                emptyList(),
                "NONE"
            )
            file.writeText(valid, Charsets.UTF_8)
            val shareId = (TaskShareCodec.decode(valid) as TaskShareReadResult.Success).envelope.shareId

            val result = TaskShareRepository(context, database).read(Uri.fromFile(file))

            assertTrue(result is TaskShareReadResult.Success)
            assertTrue(database.taskDao().getAll().isEmpty())
            assertEquals(null, database.importedTaskShareDao().getByShareId(shareId))

            file.writeBytes(ByteArray(TaskShareFormat.MAX_BYTES + 1))
            assertEquals(TaskShareReadResult.TooLarge, TaskShareRepository(context, database).read(Uri.fromFile(file)))
            assertTrue(database.taskDao().getAll().isEmpty())
        } finally {
            file.delete()
            database.close()
        }
    }
}
