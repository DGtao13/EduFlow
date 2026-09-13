package com.eduflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eduflow.app.data.TaskLogic
import com.eduflow.app.data.TaskRepository
import com.eduflow.app.data.TaskLifecycleRepository
import com.eduflow.app.data.SchoolLessonCancellationRepository
import com.eduflow.app.EduFlowApplication
import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskChecklistItem
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.TaskReminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LessonDetailsViewModel(private val database: EduFlowDatabase, lessonId: Long) : ViewModel() {
    private val lifecycle = TaskLifecycleRepository(EduFlowApplication.appContext, database)
    private val cancellationRepository = SchoolLessonCancellationRepository(EduFlowApplication.appContext, database)
    val lesson = database.lessonInstanceDao().observeById(lessonId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val subjects = database.subjectDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val block = lesson.flatMapLatest { selected ->
        if (selected == null) flowOf(null)
        else combine(database.lessonInstanceDao().observeForDate(selected.actualDate), database.scheduleSlotDao().observeAll()) { day, slots ->
            com.eduflow.app.domain.LessonBlockResolver.resolve(selected, day, slots)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val allTasks = database.taskDao().observeAll()
    val dueTasks = combine(block, allTasks) { resolved, tasks -> tasks.filter { it.dueLessonInstanceId in resolved?.ids.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val originTasks = combine(block, allTasks) { resolved, tasks -> tasks.filter { it.originatingLessonInstanceId in resolved?.ids.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun saveLesson(lesson: LessonInstance, onSaved: () -> Unit = {}) = viewModelScope.launch {
        database.lessonInstanceDao().getById(lesson.id)?.let { current ->
            database.lessonInstanceDao().update(current.copy(topic = lesson.topic, notes = lesson.notes))
            onSaved()
        }
    }
    fun deletePrivateOneOff(lesson: LessonInstance, onDeleted: () -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val current = database.lessonInstanceDao().getById(lesson.id)
        if (current?.kind == LessonKind.PRIVATE && current.sourcePrivateLessonId == null) {
            database.lessonInstanceDao().delete(current)
            onDeleted()
        }
    }
    fun toggleCancellation(lesson: LessonInstance) = viewModelScope.launch(Dispatchers.IO) {
        if (lesson.kind == LessonKind.PRIVATE && lesson.cancellationState == CancellationState.CANCELLED) com.eduflow.app.data.NotificationSettingsRepository(EduFlowApplication.appContext).allowPrivate(lesson.id)
        cancellationRepository.setLessonCancellation(lesson, if (lesson.cancellationState == CancellationState.CANCELLED) CancellationState.ACTIVE else CancellationState.CANCELLED)
    }
    fun setBlockCancellation(lesson: LessonInstance, state: CancellationState) = viewModelScope.launch(Dispatchers.IO) {
        cancellationRepository.setCancellationForBlock(lesson, state)
    }
    fun toggleTask(task: Task) = viewModelScope.launch { lifecycle.toggle(task) }
}

class TasksViewModel(private val database: EduFlowDatabase) : ViewModel() {
    private val lifecycle = TaskLifecycleRepository(EduFlowApplication.appContext, database)
    val pending = database.taskDao().observePending().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val completed = database.taskDao().observeCompleted().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subjects = database.subjectDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val checklist = database.taskChecklistDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lessons = database.lessonInstanceDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scheduleSlots = database.scheduleSlotDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun toggleTask(task: Task) = viewModelScope.launch { lifecycle.toggle(task) }
    fun toggleChecklist(item: TaskChecklistItem) = viewModelScope.launch { database.taskChecklistDao().update(item.copy(isCompleted = !item.isCompleted)) }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TaskEditorViewModel(private val database: EduFlowDatabase, private val taskId: Long?, private val originLessonId: Long?) : ViewModel() {
    private val repository = TaskRepository(database)
    private val lifecycle = TaskLifecycleRepository(EduFlowApplication.appContext, database)
    val task = if (taskId == null) MutableStateFlow<Task?>(null) else database.taskDao().observeById(taskId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val subjects = database.subjectDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scheduleSlots = database.scheduleSlotDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val originLesson = if (originLessonId == null) MutableStateFlow<LessonInstance?>(null) else database.lessonInstanceDao().observeById(originLessonId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val checklist: StateFlow<List<TaskChecklistItem>> = task.flatMapLatest { current -> if (current == null) MutableStateFlow(emptyList()) else database.taskChecklistDao().observeForTask(current.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reminders: StateFlow<List<TaskReminder>> = task.flatMapLatest { current -> if (current == null) MutableStateFlow(emptyList()) else database.taskReminderDao().observeForTask(current.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val error = MutableStateFlow<String?>(null)
    private val duePresets = com.eduflow.app.data.TaskDuePresetRepository(EduFlowApplication.appContext)
    // Hydrate all persisted editor values together before the form can be edited.
    val seed = flow {
        val value = taskId?.let { database.taskDao().getById(it) }
        emit(TaskEditorSeed(value, originLessonId?.let { database.lessonInstanceDao().getById(it) },
            value?.let { database.taskChecklistDao().getForTask(it.id) }.orEmpty(),
            value?.let { database.taskReminderDao().getForTask(it.id) }.orEmpty(),
            value?.let { duePresets.ordinal(it).first() } ?: 1))
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val savedSessionOrdinal = task.flatMapLatest { value -> if (value == null) kotlinx.coroutines.flow.flowOf(1) else duePresets.ordinal(value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)
    suspend fun savedDestination(value: Task): LessonInstance? = value.dueLessonInstanceId?.let { database.lessonInstanceDao().getById(it) }

    suspend fun previewSession(subjectId: Long, origin: LessonInstance?, ordinal: Int): LessonInstance? = withContext(Dispatchers.IO) {
        val contextOrigin = origin ?: task.value?.originatingLessonInstanceId?.let { database.lessonInstanceDao().getById(it) }
        repository.resolveFutureSession(subjectId, contextOrigin?.let { LocalDateTime.of(it.actualDate, it.actualStartTime) } ?: LocalDateTime.now(), contextOrigin, ordinal)
    }

    fun save(task: Task, items: List<TaskChecklistItem>, reminders: List<TaskReminder>, useNextLesson: Boolean, onSaved: () -> Unit, sessionOrdinal: Int = 1) = viewModelScope.launch {
        val previous = if (task.id == 0L) null else database.taskDao().getById(task.id)
        val needsResolution = useNextLesson && (previous?.intendedDueLessonInstanceId == null || previous.subjectId != task.subjectId || task.intendedDueLessonInstanceId == null)
        val routeOrigin = originLesson.value
        val resolved = if (needsResolution && task.subjectId != null) withContext(Dispatchers.IO) {
            val origin = routeOrigin ?: task.originatingLessonInstanceId?.let { database.lessonInstanceDao().getById(it) }
            repository.resolveFutureSession(task.subjectId, origin?.let { LocalDateTime.of(it.actualDate, it.actualStartTime) } ?: LocalDateTime.now(), origin, sessionOrdinal)
        } else null
        if (needsResolution && resolved == null) { error.value = "NO_NEXT"; return@launch }
        val finalTask = when {
            !useNextLesson -> task.copy(intendedDueLessonInstanceId = null, dueLessonInstanceId = null)
            resolved != null -> task.copy(intendedDueLessonInstanceId = resolved.id.takeIf { resolved.kind == LessonKind.SCHOOL }, dueLessonInstanceId = resolved.id, dueAt = LocalDateTime.of(resolved.actualDate, resolved.actualStartTime))
            else -> task
        }
        val id = lifecycle.save(finalTask, items, com.eduflow.app.ui.saveableTaskReminders(reminders, finalTask.dueAt != null))
        duePresets.record(finalTask.copy(id = id), sessionOrdinal.takeIf { useNextLesson })
        onSaved()
    }
    fun deleteReminder(reminder: TaskReminder) = viewModelScope.launch { lifecycle.deleteReminder(reminder) }
    fun deleteTask(task: Task, onDeleted: () -> Unit) = viewModelScope.launch {
        lifecycle.delete(task)
        onDeleted()
    }
}

data class TaskEditorSeed(val task: Task?, val origin: LessonInstance?, val checklist: List<TaskChecklistItem>,
                          val reminders: List<TaskReminder>, val ordinal: Int)

class LessonDetailsFactory(private val database: EduFlowDatabase, private val lessonId: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = LessonDetailsViewModel(database, lessonId) as T
}
class TaskEditorFactory(private val database: EduFlowDatabase, private val taskId: Long?, private val originId: Long?) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskEditorViewModel(database, taskId, originId) as T
}
