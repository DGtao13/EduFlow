package com.eduflow.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eduflow.app.data.TaskLogic
import com.eduflow.app.data.TaskLifecycleRepository
import com.eduflow.app.EduFlowApplication
import com.eduflow.app.data.AcademicYearSettings
import com.eduflow.app.data.AcademicYearSettingsRepository
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.RecurringPrivateLesson
import com.eduflow.app.data.local.Task
import com.eduflow.app.data.local.TaskStatus
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.requireKindInvariant
import com.eduflow.app.ui.PrivateLessonFormValues
import com.eduflow.app.ui.updateOneOffPrivateLesson
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class PrivateLessonsViewModel(private val database: EduFlowDatabase) : ViewModel() {
    val lessons = database.recurringPrivateLessonDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val subjects = database.subjectDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun toggle(lesson: RecurringPrivateLesson) = viewModelScope.launch { database.recurringPrivateLessonDao().update(lesson.copy(enabled = !lesson.enabled)) }
    fun delete(lesson: RecurringPrivateLesson) = viewModelScope.launch {
        val future = database.lessonInstanceDao().getForPrivateSourceInRange(lesson.id, LocalDate.now(), LocalDate.of(2100,1,1))
        com.eduflow.app.data.NotificationSettingsRepository(EduFlowApplication.appContext).suppressDeletedPrivate(future)
        database.recurringPrivateLessonDao().delete(lesson)
    }
}

class PrivateLessonEditorViewModel(private val database: EduFlowDatabase, id: Long?, private val oneOffId: Long? = null) : ViewModel() {
    val lesson = if (id == null) kotlinx.coroutines.flow.MutableStateFlow<RecurringPrivateLesson?>(null) else database.recurringPrivateLessonDao().observeById(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val oneOffLesson = if (oneOffId == null) kotlinx.coroutines.flow.MutableStateFlow<LessonInstance?>(null) else database.lessonInstanceDao().observeById(oneOffId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val subjects = database.subjectDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(lesson: RecurringPrivateLesson, onSaved: () -> Unit) = viewModelScope.launch {
        require(!lesson.privateLessonName.isNullOrBlank()) { "PRIVATE recurring lessons require a name" }
        require(lesson.weekday in 1..7)
        require(lesson.intervalWeeks in 1..2)
        require(lesson.endTime.isAfter(lesson.startTime))
        require(lesson.endDate == null || !lesson.endDate.isBefore(lesson.startDate))
        database.recurringPrivateLessonDao().upsert(lesson)
        onSaved()
    }
    fun createOneOff(lesson: LessonInstance, onSaved: () -> Unit) = viewModelScope.launch {
        require(lesson.kind == LessonKind.PRIVATE)
        require(!lesson.privateLessonName.isNullOrBlank()) { "PRIVATE lessons require a name" }
        lesson.requireKindInvariant()
        database.lessonInstanceDao().upsert(lesson)
        onSaved()
    }
    fun updateOneOff(values: PrivateLessonFormValues, onSaved: () -> Unit) = viewModelScope.launch {
        val current = oneOffId?.let { database.lessonInstanceDao().getById(it) } ?: return@launch
        require(current.kind == LessonKind.PRIVATE)
        database.lessonInstanceDao().update(updateOneOffPrivateLesson(current, values))
        com.eduflow.app.data.NotificationSettingsRepository(EduFlowApplication.appContext).allowPrivate(current.id)
        onSaved()
    }
}

class SubjectDetailsViewModel(private val database: EduFlowDatabase, subjectId: Long) : ViewModel() {
    val subject = database.subjectDao().observeById(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val tasks = database.taskDao().observeForSubject(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lessons = database.lessonInstanceDao().observeForSubject(subjectId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val academicYear = AcademicYearSettingsRepository(EduFlowApplication.appContext).settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AcademicYearSettings())
    fun toggleTask(task: Task) = viewModelScope.launch { TaskLifecycleRepository(EduFlowApplication.appContext, database).toggle(task) }
}

class PrivateLessonEditorFactory(private val database: EduFlowDatabase, private val id: Long?, private val oneOff: Boolean = false) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PrivateLessonEditorViewModel(database, id = if (oneOff) null else id, oneOffId = if (oneOff) id else null) as T
}
class SubjectDetailsFactory(private val database: EduFlowDatabase, private val id: Long) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = SubjectDetailsViewModel(database, id) as T
}
