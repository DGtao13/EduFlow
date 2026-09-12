package com.eduflow.app.ui

import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.domain.LessonBlock

enum class LessonQuickActionKind {
    OPEN,
    ADD_TASK,
    CANCEL_LESSON,
    CANCEL_BLOCK,
    RESTORE_LESSON,
    RESTORE_BLOCK
}

fun lessonQuickActionKinds(lesson: LessonInstance, block: LessonBlock): List<LessonQuickActionKind> = buildList {
    add(LessonQuickActionKind.OPEN)
    add(LessonQuickActionKind.ADD_TASK)
    add(if (lesson.cancellationState == CancellationState.CANCELLED) LessonQuickActionKind.RESTORE_LESSON else LessonQuickActionKind.CANCEL_LESSON)
    if (block.isMultiPeriod) {
        add(if (lesson.cancellationState == CancellationState.CANCELLED) LessonQuickActionKind.RESTORE_BLOCK else LessonQuickActionKind.CANCEL_BLOCK)
    }
}
