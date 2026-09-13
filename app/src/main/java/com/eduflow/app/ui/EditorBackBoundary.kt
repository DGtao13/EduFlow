package com.eduflow.app.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedCallback
import androidx.activity.BackEventCompat
import androidx.compose.runtime.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collect

private class EditorBackEntry(val action: State<() -> Unit>, started: Boolean) {
    var started by mutableStateOf(started)
}
private class EditorBackRegistry {
    val entries = mutableStateListOf<EditorBackEntry>()
    val current: EditorBackEntry? get() = entries.lastOrNull { it.started }
}
private val LocalEditorBackRegistry = staticCompositionLocalOf<EditorBackRegistry?> { null }

/** Register the editor's canonical toolbar action, not an independent navigation callback. */
@Composable
@NonSkippableComposable
fun EditorBackHandler(attemptToLeave: () -> Unit) {
    val registry = LocalEditorBackRegistry.current
    // Local function references can compare equal while capturing different draft snapshots.
    val action = remember { mutableStateOf(attemptToLeave, referentialEqualityPolicy()) }
    SideEffect { action.value = attemptToLeave }
    val owner = LocalLifecycleOwner.current
    if (registry == null) {
        PredictiveBackHandler { events -> events.collect {}; action.value() }
    } else DisposableEffect(registry, owner) {
        val entry = EditorBackEntry(action, owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        val observer = LifecycleEventObserver { _, _ -> entry.started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        registry.entries.add(entry)
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); registry.entries.remove(entry) }
    }
}

/** Isolate NavHost callbacks so they cannot outrank a staged editor's leave decision. */
@Composable
fun EditorBackBoundary(content: @Composable () -> Unit) {
    val registry = remember { EditorBackRegistry() }
    val outer = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
    var navigationEnabled by remember { mutableStateOf(false) }
    val navigation = remember { OnBackPressedDispatcher({}, { navigationEnabled = it }) }
    val navigationOwner = remember(outer, navigation) {
        object : OnBackPressedDispatcherOwner {
            override val lifecycle get() = outer.lifecycle
            override val onBackPressedDispatcher = navigation
        }
    }
    val callback = remember(registry, navigation) {
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val editor = registry.current
                if (editor != null) editor.action.value() else navigation.onBackPressed()
            }
            override fun handleOnBackStarted(backEvent: BackEventCompat) {
                if (registry.current == null) navigation.dispatchOnBackStarted(backEvent)
            }
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (registry.current == null) navigation.dispatchOnBackProgressed(backEvent)
            }
            override fun handleOnBackCancelled() {
                if (registry.current == null) navigation.dispatchOnBackCancelled()
            }
        }
    }
    val enabled = registry.current != null || navigationEnabled
    SideEffect { callback.isEnabled = enabled }
    DisposableEffect(outer, callback) {
        outer.onBackPressedDispatcher.addCallback(outer, callback)
        onDispose { callback.remove() }
    }
    CompositionLocalProvider(LocalEditorBackRegistry provides registry, LocalOnBackPressedDispatcherOwner provides navigationOwner) { content() }
}
