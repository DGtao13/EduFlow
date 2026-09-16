@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.eduflow.app.ui.screens

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.draw.alpha
import com.eduflow.app.ui.PrivateAppointmentState
import com.eduflow.app.ui.privateAppointmentState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.eduflow.app.data.local.CancellationState
import com.eduflow.app.data.local.CycleConfiguration
import com.eduflow.app.data.local.CycleEntry
import com.eduflow.app.data.local.DayException
import com.eduflow.app.data.local.EduFlowDatabase
import com.eduflow.app.data.local.LessonInstance
import com.eduflow.app.data.local.LessonKind
import com.eduflow.app.data.local.ScheduleTemplate
import com.eduflow.app.data.local.Subject
import com.eduflow.app.data.local.TimetableEvent
import com.eduflow.app.data.local.ScheduleSlot
import com.eduflow.app.domain.LessonBlock
import com.eduflow.app.domain.LessonBlockResolver
import com.eduflow.app.ui.viewmodel.DatabaseViewModelFactory
import com.eduflow.app.ui.viewmodel.ScheduleViewModel
import com.eduflow.app.R
import com.eduflow.app.ui.bulgarianWeekday
import com.eduflow.app.ui.formatBulgarianDate
import com.eduflow.app.ui.formatBulgarianDateWithYear
import com.eduflow.app.data.displayLocation
import com.eduflow.app.data.displayTitle
import com.eduflow.app.data.displayTutor
import com.eduflow.app.ui.formatBulgarianWeekRange
import com.eduflow.app.ui.formatBulgarianWeekRangeWithYear
import com.eduflow.app.data.SchoolYear
import com.eduflow.app.domain.ScheduleCycle
import com.eduflow.app.domain.TaskIndicatorAggregation
import com.eduflow.app.domain.LessonTaskIndicatorState
import com.eduflow.app.ui.AdjacentInnerPosition
import com.eduflow.app.ui.WeekPagerRange
import com.eduflow.app.ui.desiredAdjacentInnerPosition
import com.eduflow.app.ui.pagerBoundaryHandoffDelta
import com.eduflow.app.ui.pagerHandoffTarget
import com.eduflow.app.ui.resolveActiveInnerOffset
import com.eduflow.app.ui.settledWeekMonday
import com.eduflow.app.ui.scheduleDateSelection
import com.eduflow.app.ui.weekdayToTimetableIndex
import com.eduflow.app.ui.timetablePanDelta
import com.eduflow.app.ui.TimetableFlingVelocity
import com.eduflow.app.ui.flingAxisRemainsActive
import com.eduflow.app.ui.shouldStartTimetableFling
import com.eduflow.app.ui.timetableFlingVelocity
import com.eduflow.app.ui.cyclePreview
import com.eduflow.app.ui.moveCycleEntry
import com.eduflow.app.ui.SchoolWeekRangeState
import com.eduflow.app.ui.classifySchoolWeek
import com.eduflow.app.ui.groupPrivateAgendaLessons
import com.eduflow.app.ui.privateAgendaCounts
import com.eduflow.app.ui.privateAgendaTargetDates
import com.eduflow.app.ui.EduFlowRootTopAppBar
import com.eduflow.app.ui.LessonQuickActionKind
import com.eduflow.app.ui.lessonQuickActionKinds
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val eventTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val scheduleContentFrameInset = 6.dp

@Composable
fun ScheduleScreen(database: EduFlowDatabase, navController: NavController, launchToday: Boolean = false) {
    val viewModel: ScheduleViewModel = viewModel(factory = DatabaseViewModelFactory(database))
    val subjects by viewModel.subjects.collectAsState()
    val templates by viewModel.templates.collectAsState()
    val configuration by viewModel.configuration.collectAsState()
    val entries by viewModel.cycleEntries.collectAsState()
    val academicYear by viewModel.academicYear.collectAsState()
    val subjectMap = remember(subjects) { subjects.associateBy { it.id } }
    val cycleValid = configuration?.let { config -> entries.isNotEmpty() && entries.any { it.templateId == config.anchorTemplateId } } == true

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
        EduFlowRootTopAppBar(
            title = stringResource(R.string.schedule_title),
            actions = { IconButton(onClick = { navController.navigate("timetable_setup") }) { Icon(Icons.Default.Edit, stringResource(R.string.manage_timetable)) } }
        )
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val viewportHeight = maxHeight
            val scheduleScrollState = rememberScrollState()
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(scheduleScrollState)
            ) {
                when {
                    templates.isEmpty() -> {
                        Box(Modifier.padding(horizontal = 6.dp)) {
                            SetupState(
                                message = stringResource(R.string.schedule_setup_message),
                                action = stringResource(R.string.create_manually),
                                onAction = { navController.navigate("timetable_setup") },
                                secondaryAction = { navController.navigate("backup") },
                                secondaryActionLabel = stringResource(R.string.import_program)
                            )
                        }
                    }
                    !cycleValid -> Box(Modifier.padding(horizontal = 6.dp)) {
                        SetupState(
                            message = stringResource(R.string.cycle_setup_message),
                            action = stringResource(R.string.configure_cycle),
                            onAction = { navController.navigate("timetable_setup") }
                        )
                    }
                    else -> SchedulePager(viewModel, academicYear, subjectMap, navController, viewportHeight, scheduleScrollState, launchToday)
                }
            }
        }
    }
}

private data class TodayPositionRequest(val id: Int, val date: LocalDate)

@Composable
private fun SchedulePager(
    viewModel: ScheduleViewModel,
    academicYear: com.eduflow.app.data.AcademicYearSettings,
    subjects: Map<Long, Subject>,
    navController: NavController,
    viewportHeight: Dp,
    scheduleScrollState: ScrollState,
    launchToday: Boolean
) {
    val scheduleSlots by viewModel.scheduleSlots.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val selectedMonday by viewModel.weekMonday.collectAsState()
    val initialDate = remember { LocalDate.now() }
    val initialMonday = remember { ScheduleCycle.mondayOf(initialDate) }
    val range = remember(academicYear.startDate, academicYear.endDate) {
        WeekPagerRange.around(initialDate, academicYear)
    }
    val initialPage = range.indexFor(selectedMonday).coerceIn(0, range.pageCount - 1)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage, pageCount = { range.pageCount })
    val settledPage = pagerState.settledPage
    val settledMonday = settledWeekMonday(range, settledPage)
    val settledData by viewModel.weekData(settledMonday).collectAsState()
    val settledTaskIndicators = remember(settledData.lessons, scheduleSlots, tasks) {
        TaskIndicatorAggregation.statesByLessonId(settledData.lessons, scheduleSlots, tasks)
    }
    val assignedCounts = remember(settledData.lessons, scheduleSlots, tasks) {
        TaskIndicatorAggregation.assignedCountsByLessonId(settledData.lessons, scheduleSlots, tasks)
    }
    val privateTargetDates = remember(settledData.privateLessons) { privateAgendaTargetDates(settledData.privateLessons) }
    val privateAnchors = remember(privateTargetDates) { privateTargetDates.associateWith { BringIntoViewRequester() } }
    val previousData by viewModel.weekData(range.mondayFor((settledPage - 1).coerceAtLeast(0))).collectAsState()
    val nextData by viewModel.weekData(range.mondayFor((settledPage + 1).coerceAtMost(range.pageCount - 1))).collectAsState()
    val innerStates = remember { mutableStateMapOf<LocalDate, ScrollState>() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val flingDecay = rememberSplineBasedDecay<Offset>()
    val timetableFlingController = remember(scope) { TimetableFlingController(scope) }
    val minimumFlingVelocity = remember(context) {
        android.view.ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    }
    var todayRequest by remember { mutableStateOf<TodayPositionRequest?>(null) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var privateQuickActionLesson by remember { mutableStateOf<LessonInstance?>(null) }
    var privateDeleteConfirmation by remember { mutableStateOf<LessonInstance?>(null) }

    DisposableEffect(timetableFlingController) {
        onDispose { timetableFlingController.cancel() }
    }

    LaunchedEffect(settledPage) {
        // A velocity belongs only to the week where its gesture ended.
        timetableFlingController.cancel()
    }

    LaunchedEffect(launchToday) {
        if (launchToday) {
            val date = LocalDate.now()
            todayRequest = TodayPositionRequest((todayRequest?.id ?: 0) + 1, date)
            pagerState.scrollToPage(range.indexFor(date).coerceIn(0, range.pageCount - 1))
        }
    }

    fun targetOffsetFor(date: LocalDate): Int = with(density) {
        (timetableColumnWidth * weekdayToTimetableIndex(date.dayOfWeek)).toPx().roundToInt()
    }
    val initialInnerOffset = targetOffsetFor(initialDate)

    val periodRows = remember(previousData.lessons, settledData.lessons, nextData.lessons, academicYear) {
        listOf(previousData, settledData, nextData)
            .flatMap { it.lessons }
            .filter { it.kind == LessonKind.SCHOOL && SchoolYear.containsSchoolDate(it.actualDate, academicYear) }
            .map { it.actualStartTime to it.actualEndTime }
            .distinct()
            .sortedBy { it.first }
            .mapIndexed { index, (start, end) -> TimetablePeriodRow(index + 1, start, end) }
    }
    val textMeasurer = rememberTextMeasurer()
    val noSchoolReasonStyle = MaterialTheme.typography.labelSmall
    val headerHeight = remember(
        previousData.exceptions,
        settledData.exceptions,
        nextData.exceptions,
        density,
        noSchoolReasonStyle
    ) {
        val reasonWidthPx = with(density) { (timetableColumnWidth - 14.dp).roundToPx() }
        val maxReasonHeightPx = listOf(previousData, settledData, nextData)
            .flatMap { it.exceptions }
            .filter { it.type == com.eduflow.app.data.local.DayExceptionType.NO_SCHOOL }
            .map { it.title ?: "" }
            .maxOfOrNull { reason ->
                textMeasurer.measure(
                    text = AnnotatedString(reason.ifBlank { "Няма учебни занятия" }),
                    style = noSchoolReasonStyle,
                    constraints = Constraints(maxWidth = reasonWidthPx),
                    maxLines = noSchoolReasonMaxLines,
                    overflow = TextOverflow.Ellipsis
                ).size.height
            } ?: 0
        with(density) {
            maxOf(
                timetableHeaderMinHeight.toPx(),
                timetableHeaderTopRowHeight.toPx() + timetableHeaderContentVerticalPadding.toPx() + maxReasonHeightPx
            ).toDp()
        }
    }
    val eventAreaHeight = remember(previousData.events, settledData.events, nextData.events) {
        val weeks = listOf(previousData, settledData, nextData)
        timetableEventLaneHeight(
            weeks.maxOfOrNull { data -> data.events.groupBy { it.date }.values.maxOfOrNull { it.size } ?: 0 } ?: 0
        )
    }
    val gridHeight = headerHeight + eventAreaHeight + (timetableCellHeight * periodRows.size)
    val settledWeekRangeState = remember(settledMonday, academicYear) {
        classifySchoolWeek(settledMonday, academicYear.startDate, academicYear.endDate)
    }
    val outsideSchoolAreaHeight = maxOf((132 + settledData.events.size * 48).dp, viewportHeight)
    val schoolAreaHeight = if (settledWeekRangeState == SchoolWeekRangeState.ACTIVE_RANGE) gridHeight else outsideSchoolAreaHeight

    LaunchedEffect(pagerState, range) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collectLatest { page -> viewModel.showWeek(range.mondayFor(page)) }
    }

    Box(Modifier.padding(horizontal = 6.dp)) {
        WeekNavigator(
            monday = settledMonday,
            onPrevious = { scope.launch { pagerState.animateScrollToPage((settledPage - 1).coerceAtLeast(0)) } },
            onNext = { scope.launch { pagerState.animateScrollToPage((settledPage + 1).coerceAtMost(range.pageCount - 1)) } },
            onToday = {
                val date = LocalDate.now()
                todayRequest = TodayPositionRequest((todayRequest?.id ?: 0) + 1, date)
                scope.launch { pagerState.animateScrollToPage(range.indexFor(date).coerceIn(0, range.pageCount - 1)) }
            },
            onSelectDate = { datePickerOpen = true }
        )
    }

    val handlePagerHandoffEnd: (Int, Float) -> Unit = { startPage, accumulatedPagerScroll ->
        val target = pagerHandoffTarget(
            startPage = startPage,
            accumulatedPagerScroll = accumulatedPagerScroll,
            pageSizePx = pagerState.layoutInfo.pageSize,
            minimumPage = 0,
            maximumPage = range.pageCount - 1
        )
        scope.launch { pagerState.animateScrollToPage(target) }
    }

    if (datePickerOpen) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = settledMonday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        val selection = scheduleDateSelection(date)
                        todayRequest = selection.schoolWeekdayIndex?.let { TodayPositionRequest((todayRequest?.id ?: 0) + 1, date) }
                        scope.launch { pagerState.animateScrollToPage(range.indexFor(selection.monday).coerceIn(0, range.pageCount - 1)) }
                    }
                    datePickerOpen = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { datePickerOpen = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(picker) }
    }

    HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(schoolAreaHeight),
                userScrollEnabled = false,
                beyondViewportPageCount = 1,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(1),
                    // Foundation still commits any sufficiently fast fling. This lower bound only
                    // governs low-residual-velocity releases after inner-scroll handoff.
                    snapPositionalThreshold = 0.30f
                ),
                key = { range.mondayFor(it).toEpochDay() }
            ) { page ->
                val pageMonday = range.mondayFor(page)
                val pageData by viewModel.weekData(pageMonday).collectAsState()
                val weekRangeState = remember(pageMonday, academicYear) {
                    classifySchoolWeek(pageMonday, academicYear.startDate, academicYear.endDate)
                }
                Box(Modifier.fillMaxSize().padding(horizontal = scheduleContentFrameInset)) {
                if (weekRangeState == SchoolWeekRangeState.ACTIVE_RANGE) {
                val savedOffset = if (pageMonday == selectedMonday) viewModel.savedInnerOffset(pageMonday) else null
                val explicitTodayOffset = todayRequest
                    ?.takeIf { pageMonday == ScheduleCycle.mondayOf(it.date) }
                    ?.let { targetOffsetFor(it.date) }
                val freshActiveOffset = if (pageMonday == initialMonday) initialInnerOffset else 0
                val innerScroll = innerStates.getOrPut(pageMonday) {
                    ScrollState(
                        if (pageMonday == selectedMonday) {
                            resolveActiveInnerOffset(savedOffset, explicitTodayOffset, freshActiveOffset)
                        } else {
                            0
                        }
                    )
                }
                LaunchedEffect(pageMonday, innerScroll) {
                    snapshotFlow { innerScroll.value }
                        .distinctUntilChanged()
                        .collectLatest { viewModel.saveInnerOffset(pageMonday, it) }
                }
                LaunchedEffect(pageMonday, settledPage) {
                    when (desiredAdjacentInnerPosition(page, settledPage)) {
                        AdjacentInnerPosition.LEFT -> innerScroll.scrollTo(0)
                        AdjacentInnerPosition.RIGHT -> {
                            val maxValue = snapshotFlow { innerScroll.maxValue }.first { it > 0 }
                            innerScroll.scrollTo(maxValue)
                        }
                        AdjacentInnerPosition.PRESERVE -> Unit
                    }
                }
                LaunchedEffect(pageMonday, todayRequest?.id) {
                    val request = todayRequest
                    if (request != null && pageMonday == ScheduleCycle.mondayOf(request.date)) {
                        val target = targetOffsetFor(request.date)
                        if (target > 0) snapshotFlow { innerScroll.maxValue }.first { it >= target }
                        innerScroll.scrollTo(target.coerceAtMost(innerScroll.maxValue))
                        todayRequest = null
                    }
                }
                    ActualWeekGrid(
                        monday = pageMonday,
                        lessons = pageData.lessons.filter { it.kind == LessonKind.SCHOOL && SchoolYear.containsSchoolDate(it.actualDate, academicYear) },
                        events = pageData.events,
                        exceptions = pageData.exceptions,
                        subjects = subjects,
                        scheduleSlots = scheduleSlots,
                        taskIndicators = if (pageMonday == settledMonday) settledTaskIndicators else emptyMap(),
                        assignedCounts = if (pageMonday == settledMonday) assignedCounts else emptyMap(),
                        onOpenTaskSection = { id, section -> navController.navigate("lesson/$id?focus=$section") },
                        onPrivateDay = { date -> scope.launch { privateAnchors[date]?.bringIntoView() } },
                        privateLessonCounts = privateAgendaCounts(pageData.privateLessons),
                        periodRows = periodRows,
                        headerHeight = headerHeight,
                        eventAreaHeight = eventAreaHeight,
                        innerScrollState = innerScroll,
                        verticalScrollState = scheduleScrollState,
                        pagerState = pagerState,
                        flingDecay = flingDecay,
                        minimumFlingVelocity = minimumFlingVelocity,
                        timetableFlingController = timetableFlingController,
                        onPagerHandoffEnd = handlePagerHandoffEnd,
                        onOpenLesson = { navController.navigate("lesson/$it") },
                        onAddTask = { navController.navigate("task/new/$it") },
                        onToggleLesson = viewModel::toggleCancellation,
                        onSetBlockCancellation = viewModel::setBlockCancellation,
                        onCancelBlock = viewModel::cancelLessons,
                        onMarkNoSchool = viewModel::markNoSchool,
                        onRestoreSchool = viewModel::restoreSchoolDay,
                        onSaveEvent = viewModel::saveEvent,
                        onDeleteEvent = viewModel::deleteEvent
                    )
                } else {
                    LaunchedEffect(pageMonday, todayRequest?.id) {
                        if (todayRequest?.let { ScheduleCycle.mondayOf(it.date) == pageMonday } == true) {
                            todayRequest = null
                        }
                    }
                    val outsideInnerScroll = innerStates.getOrPut(pageMonday) { ScrollState(0) }
                    Box(
                        Modifier.fillMaxSize()
                            .coordinatedTimetablePan(
                                horizontalScrollState = outsideInnerScroll,
                                verticalScrollState = scheduleScrollState,
                                pagerState = pagerState,
                                flingDecay = flingDecay,
                                minimumFlingVelocity = minimumFlingVelocity,
                                timetableFlingController = timetableFlingController,
                                onPagerHandoffEnd = handlePagerHandoffEnd
                            )
                            .horizontalScroll(outsideInnerScroll, enabled = false)
                    ) {
                        OutsideAcademicWeekContent(
                            state = weekRangeState,
                            weekMonday = pageMonday,
                            academicYear = academicYear,
                            events = pageData.events,
                            onSaveEvent = viewModel::saveEvent,
                            onDeleteEvent = viewModel::deleteEvent
                        )
                    }
                }
                }
            }

    if (settledWeekRangeState == SchoolWeekRangeState.ACTIVE_RANGE && settledData.lessons.isEmpty() && settledData.events.isEmpty()) {
        Text(stringResource(R.string.no_lessons_week), modifier = Modifier.padding(horizontal = scheduleContentFrameInset, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Box(Modifier.padding(horizontal = scheduleContentFrameInset)) {
        PrivateLessonsWeekSection(
            lessons = settledData.privateLessons,
            subjects = subjects,
            anchors = privateAnchors,
            onOpen = { navController.navigate("lesson/$it") },
            onLongPress = { privateQuickActionLesson = it }
        )
    }
    privateQuickActionLesson?.let { lesson ->
        PrivateLessonActionsSheet(
            lesson = lesson,
            onDismiss = { privateQuickActionLesson = null },
            onOpen = { privateQuickActionLesson = null; navController.navigate("lesson/${lesson.id}") },
            onEdit = { privateQuickActionLesson = null; navController.navigate("private/oneoff/edit/${lesson.id}") },
            onAddSimilar = { privateQuickActionLesson = null; navController.navigate("private/oneoff/similar/${lesson.id}") },
            onToggleCancellation = { privateQuickActionLesson = null; viewModel.toggleCancellation(lesson) },
            onDelete = { privateDeleteConfirmation = lesson; privateQuickActionLesson = null }
        )
    }
    privateDeleteConfirmation?.let { lesson ->
        AlertDialog(
            onDismissRequest = { privateDeleteConfirmation = null },
            title = { Text(stringResource(R.string.delete_private_lesson_instance_question)) },
            text = { Text(stringResource(R.string.delete_private_lesson_instance_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        privateDeleteConfirmation = null
                        viewModel.deletePrivateOneOff(lesson)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { privateDeleteConfirmation = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun PrivateLessonsWeekSection(
    lessons: List<LessonInstance>,
    subjects: Map<Long, Subject>,
    anchors: Map<LocalDate, BringIntoViewRequester>,
    onOpen: (Long) -> Unit,
    onLongPress: (LessonInstance) -> Unit
) {
    val groups = remember(lessons) { groupPrivateAgendaLessons(lessons) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    // One wakeup at the next visible lesson's end, plus refresh when the app resumes.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) now = LocalDateTime.now() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lessons, now) {
        lessons.map { LocalDateTime.of(it.actualDate, it.actualEndTime) }.filter { !it.isBefore(now) }.minOrNull()?.let {
            kotlinx.coroutines.delay(java.time.Duration.between(LocalDateTime.now(), it).toMillis().coerceAtLeast(0) + 1)
            now = LocalDateTime.now()
        }
    }
    if (groups.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.private_lessons), style = MaterialTheme.typography.titleMedium)
        groups.forEach { group ->
            val target = anchors[group.date]
            Column(
                Modifier.fillMaxWidth()
                    .padding(top = 4.dp)
                    .then(if (target != null) Modifier.bringIntoViewRequester(target) else Modifier)
            ) {
                Text(privateAgendaDateHeading(group.date), style = MaterialTheme.typography.titleSmall)
                group.lessons.forEach { lesson ->
                    key(lesson.id) {
                        PrivateAppointmentCard(
                            lesson = lesson,
                            subject = subjects[lesson.subjectId],
                            now = now,
                            onOpen = { onOpen(lesson.id) },
                            onLongPress = { onLongPress(lesson) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateAppointmentCard(
    lesson: LessonInstance,
    subject: Subject?,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onLongPress: () -> Unit
) {
    val temporal = privateAppointmentState(lesson, now)
    Card(
        modifier = modifier.fillMaxWidth().alpha(if (temporal == PrivateAppointmentState.PAST) .64f else 1f)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
        shape = RoundedCornerShape(10.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = if (temporal == PrivateAppointmentState.CANCELLED) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(52.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("${lesson.actualStartTime.format(eventTimeFormatter)}–${lesson.actualEndTime.format(eventTimeFormatter)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Text(lesson.displayTitle(subject, stringResource(R.string.private_lesson), stringResource(R.string.lesson)), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (temporal != PrivateAppointmentState.UPCOMING) Text(stringResource(if (temporal == PrivateAppointmentState.CANCELLED) R.string.cancelled else R.string.private_past), style = MaterialTheme.typography.labelSmall)
                val metadata = listOfNotNull(lesson.displayTutor(), lesson.displayLocation()).joinToString(" · ").takeIf { it.isNotBlank() }
                metadata?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

private fun privateAgendaDateHeading(date: LocalDate): String {
    val weekday = bulgarianWeekday(date).replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale("bg")) else it.toString() }
    return "$weekday, ${formatBulgarianDate(date)}"
}

@Composable
private fun OutsideAcademicWeekContent(
    state: SchoolWeekRangeState,
    weekMonday: LocalDate,
    academicYear: com.eduflow.app.data.AcademicYearSettings,
    events: List<TimetableEvent>,
    onSaveEvent: (TimetableEvent) -> Unit,
    onDeleteEvent: (TimetableEvent) -> Unit
) {
    var eventEditor by remember(weekMonday) { mutableStateOf<TimetableEvent?>(null) }
    var eventActions by remember(weekMonday) { mutableStateOf<TimetableEvent?>(null) }
    var eventDeleteConfirmation by remember(weekMonday) { mutableStateOf<TimetableEvent?>(null) }
    var addMenuOpen by remember(weekMonday) { mutableStateOf(false) }
    val weekDates = remember(weekMonday) { List(5) { weekMonday.plusDays(it.toLong()) } }
    val sortedEvents = remember(events) {
        events.sortedWith(compareBy<TimetableEvent> { it.date }.thenBy { it.startTime })
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(
                    stringResource(if (state == SchoolWeekRangeState.BEFORE_YEAR) R.string.school_year_not_started else R.string.school_year_finished),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    stringResource(
                        if (state == SchoolWeekRangeState.BEFORE_YEAR) R.string.school_year_starts_on else R.string.school_year_finished_on,
                        formatBulgarianDate(if (state == SchoolWeekRangeState.BEFORE_YEAR) academicYear.startDate else academicYear.endDate)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        if (sortedEvents.isNotEmpty()) {
            Text(
                stringResource(R.string.school_week_events),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)
            )
            sortedEvents.forEach { event ->
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { eventEditor = event }, onLongClick = { eventActions = event })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(event.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatBulgarianDate(event.date)} · ${if (event.isAllDay) stringResource(R.string.school_event_all_day) else stringResource(R.string.school_event_time, event.startTime ?: stringResource(R.string.time_to_be_announced))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = { addMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(stringResource(R.string.add_timetable_event), modifier = Modifier.padding(start = 6.dp))
            }
            DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                weekDates.forEach { date ->
                    DropdownMenuItem(
                        text = { Text("${bulgarianWeekday(date)} · ${formatBulgarianDate(date)}") },
                        onClick = {
                            eventEditor = TimetableEvent(date = date, title = "")
                            addMenuOpen = false
                        }
                    )
                }
            }
        }
    }
    eventEditor?.let { event ->
        EventEditorDialog(
            event = event,
            onDismiss = { eventEditor = null },
            onSave = { onSaveEvent(it); eventEditor = null },
            onDelete = { onDeleteEvent(event); eventEditor = null }
        )
    }
    eventActions?.let { event ->
        EventActionsSheet(
            event = event,
            onDismiss = { eventActions = null },
            onEdit = { eventActions = null; eventEditor = event },
            onDelete = { eventActions = null; eventDeleteConfirmation = event }
        )
    }
    eventDeleteConfirmation?.let { event ->
        AlertDialog(
            onDismissRequest = { eventDeleteConfirmation = null },
            title = { Text(stringResource(R.string.delete_event_question)) },
            text = { Text(stringResource(R.string.delete_event_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { onDeleteEvent(event); eventDeleteConfirmation = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { eventDeleteConfirmation = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun WeekNavigator(
    monday: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelectDate: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous_week), modifier = Modifier.size(30.dp)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TextButton(onClick = onSelectDate, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(formatBulgarianWeekRange(monday), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedButton(onClick = onToday, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(stringResource(R.string.current_week)) }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next_week), modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun SetupState(message: String, action: String, onAction: () -> Unit, secondaryAction: (() -> Unit)? = null, secondaryActionLabel: String? = null) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.schedule_setup_needed), style = MaterialTheme.typography.titleMedium)
        Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp), color = com.eduflow.app.ui.theme.eduFlowColor(com.eduflow.app.ui.theme.EduFlowColorRole.NEUTRAL))
        Button(onClick = onAction, modifier = Modifier.padding(top = 14.dp)) { Text(action) }
        if (secondaryAction != null && secondaryActionLabel != null) {
            OutlinedButton(onClick = secondaryAction, modifier = Modifier.padding(top = 8.dp), colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = com.eduflow.app.ui.theme.eduFlowColor(com.eduflow.app.ui.theme.EduFlowColorRole.NEUTRAL), containerColor = com.eduflow.app.ui.theme.eduFlowColor(com.eduflow.app.ui.theme.EduFlowColorRole.SUPPORTING_BRAND))) { Text(secondaryActionLabel) }
        }
    }
}

private val timetableColumnWidth = 196.dp
private val timetableHeaderMinHeight = 72.dp
private val timetableHeaderTopRowHeight = 48.dp
private val timetableHeaderContentVerticalPadding = 11.dp
private const val noSchoolReasonMaxLines = 4
private val timetableCellHeight = 86.dp
private val timetableCellShape = RoundedCornerShape(8.dp)
private val timetableEventCardHeight = 48.dp
private val timetableEventLaneVerticalPadding = 2.dp
private val timetableEventLaneSpacing = 2.dp

private fun timetableEventLaneHeight(eventCount: Int): Dp = when {
    eventCount <= 0 -> 0.dp
    else -> timetableEventCardHeight * eventCount +
        timetableEventLaneVerticalPadding * 2 +
        timetableEventLaneSpacing * (eventCount - 1)
}

private data class TimetablePeriodRow(
    val displayNumber: Int,
    val startTime: LocalTime,
    val endTime: LocalTime
)

@Composable
private fun ActualWeekGrid(
    monday: LocalDate,
    lessons: List<LessonInstance>,
    events: List<TimetableEvent>,
    exceptions: List<DayException>,
    subjects: Map<Long, Subject>,
    scheduleSlots: List<ScheduleSlot>,
    taskIndicators: Map<Long, LessonTaskIndicatorState>,
    privateLessonCounts: Map<LocalDate, Int>,
    assignedCounts: Map<Long, Int>,
    onOpenTaskSection: (Long, String) -> Unit,
    onPrivateDay: (LocalDate) -> Unit,
    onOpenLesson: (Long) -> Unit,
    onAddTask: (Long) -> Unit,
    onToggleLesson: (LessonInstance) -> Unit,
    onSetBlockCancellation: (LessonInstance, CancellationState) -> Unit,
    onCancelBlock: (List<LessonInstance>) -> Unit,
    onMarkNoSchool: (LocalDate, String?) -> Unit,
    onRestoreSchool: (LocalDate) -> Unit,
    onSaveEvent: (TimetableEvent) -> Unit,
    onDeleteEvent: (TimetableEvent) -> Unit,
    periodRows: List<TimetablePeriodRow>,
    eventAreaHeight: androidx.compose.ui.unit.Dp,
    headerHeight: androidx.compose.ui.unit.Dp,
    innerScrollState: ScrollState,
    verticalScrollState: ScrollState,
    pagerState: PagerState,
    flingDecay: DecayAnimationSpec<Offset>,
    minimumFlingVelocity: Float,
    timetableFlingController: TimetableFlingController,
    onPagerHandoffEnd: (startPage: Int, accumulatedPagerScroll: Float) -> Unit
) {
    val dates = remember(monday) { List(5) { monday.plusDays(it.toLong()) } }
    val lessonsByDate = remember(lessons) { lessons.groupBy { it.actualDate } }
    val eventsByDate = remember(events) { events.groupBy { it.date } }
    Column(
        Modifier.fillMaxWidth()
            .coordinatedTimetablePan(
                horizontalScrollState = innerScrollState,
                verticalScrollState = verticalScrollState,
                pagerState = pagerState,
                flingDecay = flingDecay,
                minimumFlingVelocity = minimumFlingVelocity,
                timetableFlingController = timetableFlingController,
                onPagerHandoffEnd = onPagerHandoffEnd
            )
            // The pan modifier owns touch input; this modifier still supplies bounded layout.
            .horizontalScroll(innerScrollState, enabled = false)
    ) {
        Row {
            dates.forEach { date ->
                TimetableDayColumn(
                    date = date,
                    lessons = lessonsByDate[date].orEmpty(),
                    events = eventsByDate[date].orEmpty(),
                    exception = exceptions.firstOrNull { it.date == date },
                    privateLessonCount = privateLessonCounts[date] ?: 0,
                    periodRows = periodRows,
                    subjects = subjects,
                    scheduleSlots = scheduleSlots,
                    taskIndicators = taskIndicators,
                    assignedCounts = assignedCounts,
                    onOpenTaskSection = onOpenTaskSection,
                    onPrivateDay = { onPrivateDay(date) },
                    onOpenLesson = onOpenLesson,
                    onAddTask = onAddTask,
                    eventAreaHeight = eventAreaHeight,
                    headerHeight = headerHeight,
                    onToggleLesson = onToggleLesson,
                    onSetBlockCancellation = onSetBlockCancellation,
                    onCancelBlock = onCancelBlock,
                    onMarkNoSchool = onMarkNoSchool,
                    onRestoreSchool = onRestoreSchool,
                    onSaveEvent = onSaveEvent,
                    onDeleteEvent = onDeleteEvent
                )
            }
        }
    }
}

/**
 * One pan owner for the timetable. It waits for the platform's touch slop, then updates both
 * bounded scroll states for every pointer delta. Child click and long-press modifiers therefore
 * receive untouched pointer input until a deliberate drag starts.
 */
private fun Modifier.coordinatedTimetablePan(
    horizontalScrollState: ScrollState,
    verticalScrollState: ScrollState,
    pagerState: PagerState,
    flingDecay: DecayAnimationSpec<Offset>,
    minimumFlingVelocity: Float,
    timetableFlingController: TimetableFlingController,
    onPagerHandoffEnd: (startPage: Int, accumulatedPagerScroll: Float) -> Unit
): Modifier = pointerInput(
    horizontalScrollState,
    verticalScrollState,
    pagerState,
    flingDecay,
    minimumFlingVelocity,
    timetableFlingController
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        // Direct contact has priority over a running decay, even before touch slop.
        timetableFlingController.cancel()
        val pointerId = down.id
        val velocityTracker = VelocityTracker().also { it.addPointerInputChange(down) }
        var accumulatedFingerDelta = Offset.Zero
        var dragging = false
        var releasedNormally = false
        val pagerStartPage = pagerState.settledPage
        var accumulatedPagerScroll = 0f

        while (true) {
            // Consume in Initial so child combinedClickable handlers see a deliberate drag as
            // cancelled during their Main-pass processing.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
            if (!change.pressed) {
                velocityTracker.addPointerInputChange(change)
                releasedNormally = change.changedToUpIgnoreConsumed()
                break
            }

            velocityTracker.addPointerInputChange(change)

            val fingerDelta = change.positionChange()
            if (!dragging) {
                accumulatedFingerDelta += fingerDelta
                val distance = accumulatedFingerDelta.getDistance()
                if (distance <= viewConfiguration.touchSlop) continue

                dragging = true
                val overSlop = accumulatedFingerDelta *
                    ((distance - viewConfiguration.touchSlop) / distance)
                accumulatedPagerScroll += applyCoordinatedPanDelta(
                    fingerDelta = overSlop,
                    horizontalScrollState = horizontalScrollState,
                    verticalScrollState = verticalScrollState,
                    pagerState = pagerState
                )
                change.consume()
            } else if (fingerDelta != Offset.Zero) {
                accumulatedPagerScroll += applyCoordinatedPanDelta(
                    fingerDelta = fingerDelta,
                    horizontalScrollState = horizontalScrollState,
                    verticalScrollState = verticalScrollState,
                    pagerState = pagerState
                )
                change.consume()
            }
        }

        if (dragging && releasedNormally) {
            if (accumulatedPagerScroll != 0f) {
                // The existing direct edge handoff owns this release and pager settling.
                onPagerHandoffEnd(pagerStartPage, accumulatedPagerScroll)
            } else {
                val pointerVelocity = velocityTracker.calculateVelocity(
                    Velocity(viewConfiguration.maximumFlingVelocity, viewConfiguration.maximumFlingVelocity)
                )
                val scrollVelocity = timetableFlingVelocity(
                    pointerVelocityX = pointerVelocity.x,
                    pointerVelocityY = pointerVelocity.y,
                    minimumFlingVelocity = minimumFlingVelocity
                )
                if (shouldStartTimetableFling(
                        dragCrossedTouchSlop = dragging,
                        releasedNormally = releasedNormally,
                        pagerHandoffActive = false,
                        velocity = scrollVelocity
                    )
                ) {
                    timetableFlingController.start(
                        horizontalScrollState = horizontalScrollState,
                        verticalScrollState = verticalScrollState,
                        initialVelocity = scrollVelocity,
                        decay = flingDecay
                    )
                }
            }
        }
    }
}

private fun applyCoordinatedPanDelta(
    fingerDelta: Offset,
    horizontalScrollState: ScrollState,
    verticalScrollState: ScrollState,
    pagerState: PagerState
): Float {
    val requested = timetablePanDelta(fingerDelta.x, fingerDelta.y)
    val horizontalConsumed = horizontalScrollState.dispatchRawDelta(requested.horizontal)
    verticalScrollState.dispatchRawDelta(requested.vertical)
    val pagerDelta = pagerBoundaryHandoffDelta(
        fingerDeltaX = fingerDelta.x,
        fingerDeltaY = fingerDelta.y,
        requestedHorizontal = requested.horizontal,
        consumedByTimetable = horizontalConsumed
    )
    return if (pagerDelta != 0f) pagerState.dispatchRawDelta(pagerDelta) else 0f
}

/** Owns the single transient decay job for the currently settled timetable page. */
private class TimetableFlingController(private val scope: CoroutineScope) {
    private var flingJob: Job? = null

    fun cancel() {
        flingJob?.cancel()
        flingJob = null
    }

    fun start(
        horizontalScrollState: ScrollState,
        verticalScrollState: ScrollState,
        initialVelocity: TimetableFlingVelocity,
        decay: DecayAnimationSpec<Offset>
    ) {
        cancel()
        flingJob = scope.launch {
            horizontalScrollState.scroll(MutatePriority.Default) {
                val horizontalScope = this
                verticalScrollState.scroll(MutatePriority.Default) {
                    val verticalScope = this
                    var previousValue = Offset.Zero
                    var horizontalActive = initialVelocity.horizontal != 0f
                    var verticalActive = initialVelocity.vertical != 0f
                    AnimationState(
                        Offset.VectorConverter,
                        Offset.Zero,
                        Offset(initialVelocity.horizontal, initialVelocity.vertical)
                    ).animateDecay(decay) {
                        val frameDelta = value - previousValue
                        previousValue = value
                        if (horizontalActive) {
                            val consumed = horizontalScope.scrollBy(frameDelta.x)
                            horizontalActive = flingAxisRemainsActive(frameDelta.x, consumed)
                        }
                        if (verticalActive) {
                            val consumed = verticalScope.scrollBy(frameDelta.y)
                            verticalActive = flingAxisRemainsActive(frameDelta.y, consumed)
                        }
                        if (!horizontalActive && !verticalActive) cancelAnimation()
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableDayColumn(
    date: LocalDate,
    lessons: List<LessonInstance>,
    events: List<TimetableEvent>,
    exception: DayException?,
    privateLessonCount: Int,
    assignedCounts: Map<Long, Int>,
    onOpenTaskSection: (Long, String) -> Unit,
    onPrivateDay: () -> Unit,
    periodRows: List<TimetablePeriodRow>,
    subjects: Map<Long, Subject>,
    scheduleSlots: List<ScheduleSlot>,
    taskIndicators: Map<Long, LessonTaskIndicatorState>,
    onOpenLesson: (Long) -> Unit,
    onAddTask: (Long) -> Unit,
    eventAreaHeight: androidx.compose.ui.unit.Dp,
    headerHeight: androidx.compose.ui.unit.Dp,
    onToggleLesson: (LessonInstance) -> Unit,
    onSetBlockCancellation: (LessonInstance, CancellationState) -> Unit,
    onCancelBlock: (List<LessonInstance>) -> Unit,
    onMarkNoSchool: (LocalDate, String?) -> Unit,
    onRestoreSchool: (LocalDate) -> Unit,
    onSaveEvent: (TimetableEvent) -> Unit,
    onDeleteEvent: (TimetableEvent) -> Unit
) {
    var menuOpen by remember(date) { mutableStateOf(false) }
    var blockDialog by remember(date) { mutableStateOf(false) }
    var noSchoolDialog by remember(date) { mutableStateOf(false) }
    var eventEditor by remember(date) { mutableStateOf<TimetableEvent?>(null) }
    var eventActions by remember(date) { mutableStateOf<TimetableEvent?>(null) }
    var eventDeleteConfirmation by remember(date) { mutableStateOf<TimetableEvent?>(null) }
    var quickActionLesson by remember(date) { mutableStateOf<LessonInstance?>(null) }
    var blockCancellationConfirmation by remember(date) { mutableStateOf<LessonBlock?>(null) }
    val isNoSchool = exception?.type == com.eduflow.app.data.local.DayExceptionType.NO_SCHOOL
    val lessonsByStart = remember(lessons, scheduleSlots) { LessonBlockResolver.scheduleMembersByStart(lessons, scheduleSlots) }
    val blocksByLessonId = remember(lessons, scheduleSlots) { LessonBlockResolver.resolveAll(lessons, scheduleSlots) }

    Column(Modifier.width(timetableColumnWidth)) {
        TimetableDayHeader(date, exception, privateLessonCount, menuOpen, { menuOpen = true }, { menuOpen = false },
            headerHeight = headerHeight,
            onPrivateDay = onPrivateDay,
            onRestoreSchool = { onRestoreSchool(date) },
            onMarkNoSchool = { noSchoolDialog = true },
            onCancelBlock = { blockDialog = true },
            onAddEvent = { eventEditor = TimetableEvent(date = date, title = "") },
            canCancel = lessons.any { it.cancellationState != CancellationState.CANCELLED }
        )
        DayEventsArea(
            events = events,
            height = eventAreaHeight,
            onClick = { eventEditor = it },
            onLongClick = { eventActions = it }
        )
        periodRows.forEach { row ->
            val lesson = lessonsByStart[row.startTime]
            if (lesson == null) {
                EmptyTimetableCell()
            } else {
                TimetableLessonCell(
                    periodNumber = row.displayNumber,
                    lesson = lesson,
                    subject = subjects[lesson.subjectId],
                    block = blocksByLessonId[lesson.id] ?: LessonBlock(listOf(lesson)),
                    taskIndicatorState = taskIndicators[lesson.id],
                    assignedCount = assignedCounts[lesson.id] ?: 0,
                    onDue = { onOpenTaskSection(lesson.id, "due") },
                    onAssigned = { onOpenTaskSection(lesson.id, "assigned") },
                    noSchool = isNoSchool,
                    onClick = { onOpenLesson(lesson.id) },
                    onLongClick = { quickActionLesson = lesson }
                )
            }
        }
    }

    if (blockDialog) CancelBlockDialog(lessons, { blockDialog = false }, { onCancelBlock(it); blockDialog = false })
    quickActionLesson?.let { lesson ->
        LessonQuickActionsSheet(
            lesson = lesson,
            block = blocksByLessonId[lesson.id] ?: LessonBlock(listOf(lesson)),
            subject = subjects[lesson.subjectId],
            onDismiss = { quickActionLesson = null },
            onOpen = { onOpenLesson(lesson.id); quickActionLesson = null },
            onAddTask = { onAddTask(lesson.id); quickActionLesson = null },
            onToggle = { onToggleLesson(lesson); quickActionLesson = null },
            onCancelBlock = { blockCancellationConfirmation = it; quickActionLesson = null },
            onRestoreBlock = { onSetBlockCancellation(lesson, CancellationState.ACTIVE); quickActionLesson = null }
        )
    }
    blockCancellationConfirmation?.let { block ->
        BlockCancellationDialog(block, subjects[block.lessons.first().subjectId], { blockCancellationConfirmation = null }) {
            onSetBlockCancellation(block.lessons.first(), CancellationState.CANCELLED)
            blockCancellationConfirmation = null
        }
    }
    if (noSchoolDialog) NoSchoolDialog({ noSchoolDialog = false }) { reason -> onMarkNoSchool(date, reason); noSchoolDialog = false }
    eventEditor?.let { event -> EventEditorDialog(event, { eventEditor = null }, { onSaveEvent(it); eventEditor = null }, { onDeleteEvent(event); eventEditor = null }) }
    eventActions?.let { event ->
        EventActionsSheet(
            event = event,
            onDismiss = { eventActions = null },
            onEdit = { eventActions = null; eventEditor = event },
            onDelete = { eventActions = null; eventDeleteConfirmation = event }
        )
    }
    eventDeleteConfirmation?.let { event ->
        AlertDialog(
            onDismissRequest = { eventDeleteConfirmation = null },
            title = { Text(stringResource(R.string.delete_event_question)) },
            text = { Text(stringResource(R.string.delete_event_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { onDeleteEvent(event); eventDeleteConfirmation = null },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { eventDeleteConfirmation = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun TimetableDayHeader(
    date: LocalDate,
    exception: DayException?,
    privateLessonCount: Int,
    menuOpen: Boolean,
    openMenu: () -> Unit,
    closeMenu: () -> Unit,
    onRestoreSchool: () -> Unit,
    onMarkNoSchool: () -> Unit,
    onCancelBlock: () -> Unit,
    onAddEvent: () -> Unit,
    canCancel: Boolean,
    onPrivateDay: () -> Unit,
    headerHeight: androidx.compose.ui.unit.Dp
) {
    val noSchool = exception?.type == com.eduflow.app.data.local.DayExceptionType.NO_SCHOOL
    val headerColor = if (noSchool) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Column(
        Modifier.height(headerHeight)
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .background(headerColor, timetableCellShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .34f), timetableCellShape)
            .padding(start = 10.dp, end = 2.dp, top = 7.dp, bottom = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(bulgarianWeekday(date), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBulgarianDate(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (privateLessonCount > 0) {
                val description = pluralStringResource(R.plurals.private_day_indicator, privateLessonCount, privateLessonCount)
                Box(
                    Modifier.size(48.dp).clickable(onClick = onPrivateDay)
                        .semantics { contentDescription = description }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
                            if (privateLessonCount > 1) Text(privateLessonCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
            Box {
                IconButton(onClick = openMenu, modifier = Modifier.height(32.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.day_actions)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = closeMenu) {
                    if (noSchool) DropdownMenuItem(text = { Text(stringResource(R.string.restore_school_day)) }, onClick = { closeMenu(); onRestoreSchool() })
                    else DropdownMenuItem(text = { Text(stringResource(R.string.mark_no_school)) }, onClick = { closeMenu(); onMarkNoSchool() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.cancel_selected_lessons)) }, enabled = canCancel, onClick = { closeMenu(); onCancelBlock() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.add_timetable_event)) }, onClick = { closeMenu(); onAddEvent() })
                }
            }
        }
        if (noSchool) Text(
            exception?.title ?: stringResource(R.string.no_school),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = noSchoolReasonMaxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DayEventsArea(
    events: List<TimetableEvent>,
    height: androidx.compose.ui.unit.Dp,
    onClick: (TimetableEvent) -> Unit,
    onLongClick: (TimetableEvent) -> Unit
) {
    if (height == 0.dp) return
    Column(
        Modifier.height(height).padding(vertical = timetableEventLaneVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(timetableEventLaneSpacing)
    ) {
        events.sortedWith(compareBy<TimetableEvent> { !it.isAllDay }.thenBy { it.startTime }).forEach { event ->
            Box(
                Modifier.fillMaxWidth().height(timetableEventCardHeight).padding(horizontal = 2.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, timetableCellShape)
                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = .45f), timetableCellShape)
                    .combinedClickable(onClick = { onClick(event) }, onLongClick = { onLongClick(event) })
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(event.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (event.isAllDay) stringResource(R.string.school_event_all_day) else stringResource(R.string.school_event_time, event.startTime ?: stringResource(R.string.time_to_be_announced)), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EmptyTimetableCell() {
    Box(
        Modifier.fillMaxWidth().height(timetableCellHeight).padding(2.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .38f), timetableCellShape)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .22f), timetableCellShape)
    )
}

@Composable
private fun TimetableLessonCell(periodNumber: Int, lesson: LessonInstance, subject: Subject?, block: LessonBlock, noSchool: Boolean, taskIndicatorState: LessonTaskIndicatorState?, assignedCount: Int, onDue: () -> Unit, onAssigned: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit) {
    val cancelled = lesson.cancellationState == CancellationState.CANCELLED
    val schoolClosed = noSchool && lesson.kind == LessonKind.SCHOOL
    val green = !cancelled && !schoolClosed && lesson.kind == LessonKind.SCHOOL && isGreenLesson(lesson)
    val cellColor = when {
        cancelled || schoolClosed -> MaterialTheme.colorScheme.surfaceVariant
        green -> Color(0xFF3C8D68)
        else -> Color(subject?.color ?: 0xFF607D8B)
    }
    val contentColor = if (cancelled || schoolClosed || cellColor.luminance() > .48f) MaterialTheme.colorScheme.onSurface else Color.White
    val blockPosition = block.lessons.indexOfFirst { it.id == lesson.id }
    val connected = block.isMultiPeriod && blockPosition >= 0
    val shape = when {
        !connected -> timetableCellShape
        blockPosition == 0 -> RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
        blockPosition == block.lessons.lastIndex -> RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
        else -> RoundedCornerShape(0.dp)
    }
    val cellPadding = when {
        !connected -> Modifier.padding(2.dp)
        blockPosition == 0 -> Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp)
        blockPosition == block.lessons.lastIndex -> Modifier.padding(start = 2.dp, end = 2.dp, bottom = 2.dp)
        else -> Modifier.padding(horizontal = 2.dp)
    }
    Box(
        Modifier.fillMaxWidth().height(timetableCellHeight).then(cellPadding)
            .background(cellColor, shape)
            .border(1.dp, contentColor.copy(alpha = .24f), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        val owners = TaskIndicatorAggregation.owners(block)
        val badgeOwner = lesson.id == owners.first
        val assignedOwner = lesson.id == owners.second && assignedCount > 0
        val hasIndicator = taskIndicatorState != null && taskIndicatorState != LessonTaskIndicatorState.None
        Box(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(end = if (badgeOwner && hasIndicator && assignedOwner) 78.dp else if (badgeOwner && hasIndicator || assignedOwner) 40.dp else 0.dp)) {
                Text("$periodNumber · ${subject?.shortName ?: subject?.name ?: stringResource(R.string.lesson)}", color = contentColor, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${lesson.actualStartTime}–${lesson.actualEndTime}", color = contentColor.copy(alpha = .94f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                lesson.actualRoom?.let { Text(compactRoom(it), color = contentColor.copy(alpha = .9f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                when {
                    schoolClosed -> Text(stringResource(R.string.no_school), color = contentColor.copy(alpha = .9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    cancelled -> Text(stringResource(R.string.cancelled), color = contentColor.copy(alpha = .9f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    else -> Unit
                }
            }
            Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (badgeOwner && hasIndicator) LessonTaskBadge(taskIndicatorState, Modifier.clickable(onClick = onDue))
                if (assignedOwner) {
                    val label = pluralStringResource(R.plurals.assigned_tasks_indicator, assignedCount, assignedCount)
                    Surface(modifier = Modifier.heightIn(min = 30.dp).clickable(onClick = onAssigned).semantics { contentDescription = label },
                        color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Row(Modifier.padding(horizontal = 5.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.List, null, modifier = Modifier.size(18.dp))
                            if (assignedCount > 1) Text(assignedCount.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonTaskBadge(state: LessonTaskIndicatorState?, modifier: Modifier = Modifier) {
    val pending = state as? LessonTaskIndicatorState.Pending
    val description = when (state) {
        is LessonTaskIndicatorState.Pending -> pluralStringResource(R.plurals.pending_tasks_due_for_lesson, state.count, state.count)
        LessonTaskIndicatorState.Completed -> stringResource(R.string.all_tasks_completed_for_lesson)
        else -> return
    }
    val containerColor = if (pending != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (pending != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier.semantics { contentDescription = description }.heightIn(min = 30.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (pending != null) Icons.Default.Edit else Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            if (pending != null && pending.count > 1) Text(pending.count.toString(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 3.dp), maxLines = 1)
        }
    }
}

@Composable
private fun LessonQuickActionsSheet(
    lesson: LessonInstance,
    block: LessonBlock,
    subject: Subject?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onAddTask: () -> Unit,
    onToggle: () -> Unit,
    onCancelBlock: (LessonBlock) -> Unit,
    onRestoreBlock: () -> Unit
) {
    val title = subject?.name ?: stringResource(R.string.lesson)
    val timeRange = if (block.isMultiPeriod) "${block.startTime}–${block.endTime}" else "${lesson.actualStartTime}–${lesson.actualEndTime}"
    val metadata = listOfNotNull(lesson.actualTeacher, lesson.actualRoom).joinToString(" · ")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(timeRange, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (block.isMultiPeriod) Text(stringResource(R.string.lesson_block_context, block.lessons.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            LessonActionRow(stringResource(R.string.open_lesson), Icons.Default.Info, onOpen)
            LessonActionRow(stringResource(R.string.add_task), Icons.Default.Add, onAddTask)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            lessonQuickActionKinds(lesson, block).drop(2).forEach { action ->
                when (action) {
                    LessonQuickActionKind.CANCEL_LESSON -> LessonActionRow(stringResource(if (block.isMultiPeriod) R.string.cancel_this_lesson else R.string.cancel_lesson), Icons.Default.Delete, onToggle, destructive = true)
                    LessonQuickActionKind.CANCEL_BLOCK -> LessonActionRow(stringResource(R.string.cancel_whole_block), Icons.Default.Delete, { onCancelBlock(block) }, destructive = true)
                    LessonQuickActionKind.RESTORE_LESSON -> LessonActionRow(stringResource(if (block.isMultiPeriod) R.string.restore_this_lesson else R.string.restore_lesson), Icons.Default.CheckCircle, onToggle)
                    LessonQuickActionKind.RESTORE_BLOCK -> LessonActionRow(stringResource(R.string.restore_whole_block), Icons.Default.CheckCircle, onRestoreBlock)
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun LessonActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun BlockCancellationDialog(block: LessonBlock, subject: Subject?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cancel_whole_block)) },
        text = { Text(stringResource(R.string.cancel_whole_block_question, subject?.shortName ?: subject?.name ?: stringResource(R.string.lesson), block.startTime.toString(), block.endTime.toString())) },
        confirmButton = { DestructiveConfirmationButton(stringResource(R.string.cancel_whole_block), onConfirm) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private fun compactRoom(room: String): String = room.replace(" (", " · ").removeSuffix(")")

private fun isGreenLesson(lesson: LessonInstance): Boolean {
    val now = LocalDateTime.now()
    return lesson.actualDate.isBefore(now.toLocalDate()) || (lesson.actualDate == now.toLocalDate() && !lesson.actualStartTime.isAfter(now.toLocalTime()))
}

@Composable
private fun LessonActionDialog(lesson: LessonInstance, onDismiss: () -> Unit, onToggle: () -> Unit) {
    val cancelled = lesson.cancellationState == CancellationState.CANCELLED
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (cancelled) R.string.restore_lesson_question else R.string.cancel_lesson_question)) }, text = { Text(stringResource(if (cancelled) R.string.restore_lesson_message else R.string.cancel_lesson_message)) }, confirmButton = { if (cancelled) TextButton(onClick = onToggle) { Text(stringResource(R.string.restore)) } else DestructiveConfirmationButton(stringResource(R.string.cancel_lesson), onToggle) }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun CancelBlockDialog(lessons: List<LessonInstance>, onDismiss: () -> Unit, onConfirm: (List<LessonInstance>) -> Unit) {
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.cancel_lessons)) }, text = {
        Column {
            lessons.filter { it.cancellationState != CancellationState.CANCELLED }.forEach { lesson ->
                Row(Modifier.fillMaxWidth().clickable { selectedIds = if (lesson.id in selectedIds) selectedIds - lesson.id else selectedIds + lesson.id }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = lesson.id in selectedIds, onCheckedChange = { checked -> selectedIds = if (checked) selectedIds + lesson.id else selectedIds - lesson.id })
                    Text("${lesson.actualStartTime}–${lesson.actualEndTime}")
                }
            }
        }
    }, confirmButton = { DestructiveConfirmationButton(stringResource(R.string.cancel_selected), { onConfirm(lessons.filter { it.id in selectedIds }) }, enabled = selectedIds.isNotEmpty()) }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun NoSchoolDialog(onDismiss: () -> Unit, onConfirm: (String?) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.mark_no_school_title)) }, text = { OutlinedTextField(reason, { reason = it }, label = { Text(stringResource(R.string.reason_optional)) }, singleLine = false, maxLines = noSchoolReasonMaxLines) }, confirmButton = { TextButton(onClick = { onConfirm(reason.trim().ifBlank { null }) }) { Text(stringResource(R.string.mark_no_school)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun QuickAddEventScreen(database: EduFlowDatabase, navController: NavController) {
    val viewModel: ScheduleViewModel = viewModel(factory = com.eduflow.app.ui.viewmodel.DatabaseViewModelFactory(database))
    EventEditorDialog(
        event = TimetableEvent(date = LocalDate.now(), title = ""),
        onDismiss = { navController.popBackStack() },
        onSave = { viewModel.saveEvent(it); navController.navigate("schedule") { popUpTo("schedule") { inclusive = false }; launchSingleTop = true } },
        onDelete = { navController.popBackStack() }
    )
}

@Composable
private fun EventEditorDialog(event: TimetableEvent, onDismiss: () -> Unit, onSave: (TimetableEvent) -> Unit, onDelete: () -> Unit) {
    var title by remember(event) { mutableStateOf(event.title) }
    var description by remember(event) { mutableStateOf(event.description.orEmpty()) }
    var start by remember(event) { mutableStateOf(event.startTime?.format(eventTimeFormatter).orEmpty()) }
    var end by remember(event) { mutableStateOf(event.endTime?.format(eventTimeFormatter).orEmpty()) }
    var allDay by remember(event) { mutableStateOf(event.isAllDay) }
    var error by remember { mutableStateOf<String?>(null) }
    val titleRequired = stringResource(R.string.title_required)
    val timeFormatError = stringResource(R.string.use_time_format)
    val endAfterStart = stringResource(R.string.end_after_start)
    val draft = event.copy(title = title.trim(), description = description.trim().ifBlank { null }, isAllDay = allDay,
        startTime = if (allDay) null else runCatching { LocalTime.parse(start, eventTimeFormatter) }.getOrNull(),
        endTime = if (allDay) null else runCatching { LocalTime.parse(end, eventTimeFormatter) }.getOrNull())
    val leave = protectedEditorExit(draft != event || (!allDay && ((start.isNotBlank() && draft.startTime == null) || (end.isNotBlank() && draft.endTime == null))), onDismiss)
    AlertDialog(onDismissRequest = leave, title = { Text(stringResource(if (event.id == 0L) R.string.add_event else R.string.edit_event)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.event_title)) }, singleLine = true, isError = error != null)
            OutlinedTextField(description, { description = it }, label = { Text(stringResource(R.string.description_optional)) }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(allDay, { allDay = it }); Text(stringResource(R.string.all_day)) }
            if (!allDay) {
                TimeRangeField(runCatching { LocalTime.parse(start) }.getOrNull(), runCatching { LocalTime.parse(end) }.getOrNull()) { s, e -> start = s.format(eventTimeFormatter); end = e.format(eventTimeFormatter); error = null }
                if (start.isNotBlank() || end.isNotBlank()) TextButton(onClick = { start = ""; end = "" }) { Text(stringResource(R.string.none)) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val parsedStart = if (allDay || start.isBlank()) null else runCatching { LocalTime.parse(start, eventTimeFormatter) }.getOrNull()
        val parsedEnd = if (allDay || end.isBlank()) null else runCatching { LocalTime.parse(end, eventTimeFormatter) }.getOrNull()
        error = when { title.isBlank() -> titleRequired; (!allDay && start.isNotBlank() && parsedStart == null) || (!allDay && end.isNotBlank() && parsedEnd == null) -> timeFormatError; parsedStart != null && parsedEnd != null && !parsedEnd.isAfter(parsedStart) -> endAfterStart; else -> null }
        if (error == null) onSave(event.copy(title = title.trim(), description = description.trim().ifBlank { null }, startTime = parsedStart, endTime = parsedEnd, isAllDay = allDay))
    }) { Text(stringResource(R.string.save)) } }, dismissButton = { Row { if (event.id != 0L) DestructiveConfirmationButton(stringResource(R.string.delete), onDelete); TextButton(onClick = leave) { Text(stringResource(R.string.cancel)) } } })
}

@Composable
fun CycleConfigurationDialog(
    monday: LocalDate,
    templates: List<ScheduleTemplate>,
    existingConfiguration: CycleConfiguration?,
    existingEntries: List<CycleEntry>,
    onDismiss: () -> Unit,
    onSave: suspend (LocalDate, Long, List<Long>) -> Unit
) {
    val initialAnchorDate = remember(existingConfiguration, monday) {
        existingConfiguration?.anchorMonday ?: monday
    }
    var anchorDate by remember(initialAnchorDate) { mutableStateOf(initialAnchorDate) }
    var orderedIds by remember(templates, existingEntries) {
        mutableStateOf(
            if (existingEntries.isEmpty()) templates.map { it.id }
            else existingEntries.map { it.templateId }.filter { id -> templates.any { it.id == id } }
        )
    }
    var anchorId by remember(existingConfiguration, templates) {
        mutableStateOf(existingConfiguration?.anchorTemplateId?.takeIf { it in orderedIds } ?: orderedIds.firstOrNull())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var choosingAnchor by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val templatesById = remember(templates) { templates.associateBy { it.id } }
    val persistenceError = stringResource(R.string.cycle_save_error)
    val validationError = when {
        orderedIds.isEmpty() -> stringResource(R.string.cycle_no_entries_error)
        orderedIds.any { it !in templatesById } -> stringResource(R.string.cycle_invalid_entry_error)
        orderedIds.toSet().size != orderedIds.size -> stringResource(R.string.cycle_duplicate_error)
        anchorId == null -> stringResource(R.string.cycle_anchor_template_error)
        anchorId !in orderedIds -> stringResource(R.string.cycle_anchor_template_error)
        else -> null
    }
    val preview = cyclePreview(anchorDate, anchorId, orderedIds)
    val anchorMonday = ScheduleCycle.mondayOf(anchorDate)

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(stringResource(R.string.cycle_dialog_title), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.cycle_dialog_message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text(stringResource(R.string.cycle_order), style = MaterialTheme.typography.titleMedium)
                if (orderedIds.isEmpty()) {
                    Text(stringResource(R.string.cycle_no_entries_error), color = MaterialTheme.colorScheme.error)
                } else {
                    orderedIds.forEachIndexed { index, templateId ->
                        key("$templateId-$index") {
                            templatesById[templateId]?.let { template ->
                                CycleEntryRow(
                                    template = template,
                                    index = index,
                                    total = orderedIds.size,
                                    onMoveUp = { orderedIds = moveCycleEntry(orderedIds, index, -1) },
                                    onMoveDown = { orderedIds = moveCycleEntry(orderedIds, index, 1) },
                                    onRemove = {
                                        orderedIds = orderedIds - template.id
                                        if (anchorId == template.id) anchorId = orderedIds.firstOrNull()
                                    }
                                )
                                if (index < orderedIds.lastIndex) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.cycle_repeat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Button(onClick = { choosingAnchor = false; showTemplatePicker = true }, enabled = templates.any { it.id !in orderedIds }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(stringResource(R.string.cycle_add_entry), modifier = Modifier.padding(start = 8.dp))
                }

                Text(stringResource(R.string.cycle_anchor_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.cycle_anchor_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = formatBulgarianDateWithYear(anchorDate),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.cycle_anchor_date)) },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.DateRange, contentDescription = stringResource(R.string.cycle_choose_week))
                        }
                    }
                )
                Text(
                    stringResource(R.string.cycle_anchor_week, formatBulgarianWeekRangeWithYear(anchorMonday)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.cycle_anchor_template), style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { choosingAnchor = true; showTemplatePicker = true }, enabled = orderedIds.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text(templatesById[anchorId]?.name ?: stringResource(R.string.cycle_choose_anchor_template), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.cycle_choose_anchor_template))
                }

                Text(stringResource(R.string.cycle_preview_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.cycle_preview_message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preview.isEmpty()) {
                    Text(stringResource(R.string.cycle_preview_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    preview.forEach { item ->
                        val templateName = templatesById[item.templateId]?.name ?: return@forEach
                        Text(
                            stringResource(R.string.cycle_preview_item, formatBulgarianWeekRangeWithYear(item.monday), templateName),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                    TextButton(
                        onClick = {
                            if (validationError == null && anchorId != null) {
                                saving = true
                                saveError = null
                                scope.launch {
                                    runCatching { onSave(anchorMonday, anchorId!!, orderedIds) }
                                        .onSuccess { onDismiss() }
                                        .onFailure {
                                            saving = false
                                            saveError = persistenceError
                                        }
                                }
                            }
                        },
                        enabled = validationError == null && !saving
                    ) { Text(stringResource(R.string.save_cycle)) }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = anchorDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        anchorDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        ) { DatePicker(state = state) }
    }

    if (showTemplatePicker) {
        val available = if (choosingAnchor) {
            orderedIds.mapNotNull(templatesById::get)
        } else {
            templates.filter { it.id !in orderedIds }
        }
        AlertDialog(
            onDismissRequest = { showTemplatePicker = false },
            title = { Text(stringResource(if (choosingAnchor) R.string.cycle_choose_anchor_template else R.string.cycle_add_entry_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (available.isEmpty()) Text(stringResource(R.string.cycle_no_available_templates))
                    available.forEach { template ->
                        TextButton(onClick = {
                            if (choosingAnchor) {
                                anchorId = template.id
                            } else {
                                orderedIds = orderedIds + template.id
                                if (anchorId == null) anchorId = template.id
                            }
                            showTemplatePicker = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(template.name, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTemplatePicker = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun CycleEntryRow(
    template: ScheduleTemplate,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember(template.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = (index + 1).toString(),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(template.name, style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.cycle_entry_position, index + 1, total), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.cycle_move_up))
            }
            IconButton(onClick = onMoveDown, enabled = index < total - 1) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.cycle_move_down))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.cycle_entry_actions))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cycle_remove_entry)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onRemove() }
                    )
                }
            }
        }
    }
}
