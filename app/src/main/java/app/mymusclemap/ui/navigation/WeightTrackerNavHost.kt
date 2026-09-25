package app.mymusclemap.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mymusclemap.R
import app.mymusclemap.WeightViewModelFactory
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.onboarding.OnboardingResumeTarget
import app.mymusclemap.domain.workout.WorkoutCompletionSummary
import app.mymusclemap.ui.dashboard.DashboardScreen
import app.mymusclemap.ui.dashboard.DashboardViewModel
import app.mymusclemap.ui.dashboard.WeightDetailsScreen
import app.mymusclemap.ui.dashboard.WeightDetailsViewModel
import app.mymusclemap.ui.exercises.ExerciseEditorScreen
import app.mymusclemap.ui.exercises.ExerciseEditorViewModel
import app.mymusclemap.ui.exercises.ExerciseListScreen
import app.mymusclemap.ui.exercises.ExerciseListViewModel
import app.mymusclemap.ui.history.HistoryScreen
import app.mymusclemap.ui.history.HistoryViewModel
import app.mymusclemap.ui.history.WorkoutDetailScreen
import app.mymusclemap.ui.history.WorkoutDetailViewModel
import app.mymusclemap.ui.settings.FeedbackComposer
import app.mymusclemap.ui.onboarding.OnboardingGuideViewModel
import app.mymusclemap.ui.onboarding.OnboardingWorkoutSpotlight
import app.mymusclemap.ui.settings.SettingsScreen
import app.mymusclemap.ui.settings.SettingsViewModel
import app.mymusclemap.ui.templates.TemplateEditorScreen
import app.mymusclemap.ui.templates.TemplateEditorViewModel
import app.mymusclemap.ui.templates.TemplateListScreen
import app.mymusclemap.ui.templates.TemplateListViewModel
import app.mymusclemap.ui.workout.ActiveWorkoutScreen
import app.mymusclemap.ui.workout.ActiveWorkoutViewModel
import app.mymusclemap.ui.workout.WorkoutCompletionScreen
import app.mymusclemap.ui.workout.WorkoutHubViewModel
import app.mymusclemap.ui.workout.WorkoutPrimaryAction
import app.mymusclemap.ui.workout.WorkoutStartPickerSheet
import app.mymusclemap.ui.workout.labelRes
import app.mymusclemap.ui.workoutimport.WorkoutImportScreen
import app.mymusclemap.ui.workoutimport.WorkoutImportViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.LocalDate

private const val ARG_EXERCISE_ID = "exerciseId"
private const val ARG_TEMPLATE_ID = "templateId"
private const val ARG_SESSION_ID = "sessionId"
private const val ARG_EXERCISES = "exercises"
private const val ARG_COMPLETED_SETS = "completedSets"
private const val ARG_DURATION_MILLIS = "durationMillis"
private const val KEY_CATALOG_SAVED = "catalog_saved"
private const val KEY_TEMPLATE_SAVED = "template_saved"
private const val KEY_WORKOUT_DELETED = "workout_deleted"

private fun editorRoute(exerciseId: Long?): String {
    return "${AppRoutes.EXERCISE_EDITOR}?$ARG_EXERCISE_ID=${exerciseId ?: -1L}"
}

private fun templateEditorRoute(templateId: Long?): String {
    return "${AppRoutes.TEMPLATE_EDITOR}?$ARG_TEMPLATE_ID=${templateId ?: -1L}"
}

internal fun activeWorkoutRoute(sessionId: Long): String {
    return "${AppRoutes.ACTIVE_WORKOUT}?$ARG_SESSION_ID=$sessionId"
}

internal fun workoutCompleteRoute(summary: WorkoutCompletionSummary): String {
    return "${AppRoutes.WORKOUT_COMPLETE}?" +
        "$ARG_EXERCISES=${summary.exerciseCount}&" +
        "$ARG_COMPLETED_SETS=${summary.completedSetCount}&" +
        "$ARG_DURATION_MILLIS=${summary.durationMillis}"
}

private fun workoutDetailRoute(sessionId: Long): String {
    return "${AppRoutes.WORKOUT_DETAIL}?$ARG_SESSION_ID=$sessionId"
}

@Composable
fun WeightTrackerNavHost(
    factory: WeightViewModelFactory,
    dateProvider: DateProvider,
    openNewTemplate: Boolean = false,
    onOpenedNewTemplate: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = AppNavigation.showsBottomBar(currentRoute)
    val workoutHubViewModel: WorkoutHubViewModel = viewModel(factory = factory)
    val hubState by workoutHubViewModel.uiState.collectAsStateWithLifecycle()
    val onboardingGuideViewModel: OnboardingGuideViewModel = viewModel(factory = factory)
    val onboardingGuide by onboardingGuideViewModel.guide.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var workoutActionBounds by remember { mutableStateOf(Rect.Zero) }
    val onPrimaryWorkoutClick = {
        onboardingGuideViewModel.dismissStartWorkoutCoach()
        when (val action = workoutHubViewModel.onPrimaryWorkoutAction()) {
            is WorkoutPrimaryAction.Resume -> {
                navController.openActiveWorkout(action.sessionId)
            }
            WorkoutPrimaryAction.ShowPicker,
            WorkoutPrimaryAction.Ignored -> Unit
        }
    }

    LaunchedEffect(hubState.message) {
        val message = hubState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(resources.getString(message.labelRes()))
        workoutHubViewModel.consumeMessage()
    }
    LaunchedEffect(hubState.startedSessionId) {
        val sessionId = hubState.startedSessionId ?: return@LaunchedEffect
        workoutHubViewModel.consumeStartedSession()
        navController.openActiveWorkout(sessionId)
    }
    LaunchedEffect(openNewTemplate) {
        if (!openNewTemplate) return@LaunchedEffect
        navController.navigateInternal(templateEditorRoute(null))
        onOpenedNewTemplate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    selectedRoute = AppNavigation.canonicalRoute(currentRoute),
                    hasActiveSession = hubState.activeSession != null,
                    onOverview = { navController.navigateRoot(AppRoutes.OVERVIEW) },
                    onJournal = { navController.navigateRoot(AppRoutes.JOURNAL) },
                    onWorkoutAction = onPrimaryWorkoutClick,
                    onWorkoutActionBounds = { bounds ->
                        if (bounds != workoutActionBounds) {
                            workoutActionBounds = bounds
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.OVERVIEW,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
        ) {
            composable(AppRoutes.OVERVIEW) {
                val viewModel: DashboardViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                DashboardScreen(
                    state = state,
                    onPreviousMonth = viewModel::onPreviousMonth,
                    onNextMonth = viewModel::onNextMonth,
                    onDaySelected = viewModel::selectDay,
                    onDismissDaySheet = viewModel::dismissDaySheet,
                    onRecordSelectedDay = viewModel::recordSelectedDay,
                    onRequestDayDelete = viewModel::requestDayDelete,
                    onDismissDayDelete = viewModel::dismissDayDelete,
                    onConfirmDayDelete = viewModel::confirmDayDelete,
                    onEditorDateChange = viewModel::onEditorDateChange,
                    onEditorWeightChange = viewModel::onEditorWeightChange,
                    onSave = viewModel::saveEditor,
                    onDismissEditor = viewModel::dismissEditor,
                    onDeleteRequest = viewModel::requestDelete,
                    onDeleteDismiss = viewModel::dismissDelete,
                    onDeleteConfirm = viewModel::confirmDelete,
                    onMessageConsumed = viewModel::consumeMessage,
                    onOpenSettings = { navController.navigateInternal(AppRoutes.SETTINGS) },
                    onOpenTemplates = { navController.navigateInternal(AppRoutes.TEMPLATES) },
                    onOpenCatalog = { navController.navigateInternal(AppRoutes.EXERCISES) },
                    onOpenWorkout = { id ->
                        viewModel.dismissDaySheet()
                        navController.navigate(workoutDetailRoute(id)) {
                            launchSingleTop = true
                        }
                    },
                    onOpenWeightDetails = { navController.navigateInternal(AppRoutes.WEIGHT_DETAILS) },
                    onOpenSchedulePicker = viewModel::openSchedulePicker,
                    onDismissSchedulePicker = viewModel::dismissSchedulePicker,
                    onScheduleTemplate = viewModel::scheduleTemplate,
                    onOpenReschedule = viewModel::openReschedule,
                    onDismissReschedule = viewModel::dismissReschedule,
                    onConfirmReschedule = viewModel::confirmReschedule,
                    onOpenRemove = viewModel::openRemove,
                    onDismissRemove = viewModel::dismissRemove,
                    onConfirmRemove = viewModel::confirmRemove,
                    onStartScheduled = viewModel::startScheduled,
                    onContinueScheduled = viewModel::continueScheduled,
                    onOpenScheduledJournal = viewModel::openScheduledJournal,
                    onCreateTemplateFromSchedule = {
                        viewModel.dismissSchedulePicker()
                        navController.navigateInternal(templateEditorRoute(null))
                    },
                    onContinueOnboarding = {
                        when (state.onboarding.resumeTarget) {
                            OnboardingResumeTarget.CreatePlan -> {
                                navController.navigateInternal(templateEditorRoute(null))
                            }
                            OnboardingResumeTarget.StartWorkout -> {
                                onboardingGuideViewModel.requestStartWorkoutCoach()
                            }
                            OnboardingResumeTarget.WeightChart -> {
                                navController.navigateInternal(AppRoutes.WEIGHT_DETAILS)
                            }
                            OnboardingResumeTarget.None,
                            OnboardingResumeTarget.Heatmap,
                            OnboardingResumeTarget.WeightPrompt,
                            OnboardingResumeTarget.Calendar -> Unit
                        }
                    },
                    onDismissOnboardingReminder = viewModel::dismissOnboardingReminder,
                    onConfirmHeatmapCoach = viewModel::markHeatmapSeen,
                    onConfirmCalendarCoach = viewModel::markCalendarSeen,
                    onOnboardingWeightChange = viewModel::onOnboardingWeightChange,
                    onSaveOnboardingWeight = viewModel::saveOnboardingWeight,
                    onSkipOnboardingWeight = viewModel::skipOnboardingWeight
                )
                LaunchedEffect(state.startedSessionId) {
                    val sessionId = state.startedSessionId ?: return@LaunchedEffect
                    viewModel.consumeStartedSession()
                    viewModel.dismissDaySheet()
                    navController.openActiveWorkout(sessionId)
                }
                LaunchedEffect(state.journalSessionId) {
                    val sessionId = state.journalSessionId ?: return@LaunchedEffect
                    viewModel.consumeJournalSession()
                    viewModel.dismissDaySheet()
                    navController.navigate(workoutDetailRoute(sessionId)) {
                        launchSingleTop = true
                    }
                }
                LaunchedEffect(state.openWeightDetailsForOnboarding) {
                    if (!state.openWeightDetailsForOnboarding) return@LaunchedEffect
                    viewModel.consumeOpenWeightDetailsForOnboarding()
                    navController.navigateInternal(AppRoutes.WEIGHT_DETAILS)
                }
            }
            composable(AppRoutes.JOURNAL) { entry ->
                val viewModel: HistoryViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val deleted by entry.savedStateHandle
                    .getStateFlow(KEY_WORKOUT_DELETED, false)
                    .collectAsStateWithLifecycle()
                LaunchedEffect(deleted) {
                    if (deleted) {
                        viewModel.showWorkoutDeleted()
                        entry.savedStateHandle[KEY_WORKOUT_DELETED] = false
                    }
                }
                HistoryScreen(
                    state = state,
                    today = dateProvider.today(),
                    onAdd = viewModel::openNew,
                    onEdit = viewModel::openEditor,
                    onDelete = viewModel::openDelete,
                    onEditorDateChange = viewModel::onEditorDateChange,
                    onEditorWeightChange = viewModel::onEditorWeightChange,
                    onSave = viewModel::saveEditor,
                    onDismissEditor = viewModel::dismissEditor,
                    onDeleteRequest = viewModel::requestDelete,
                    onDeleteDismiss = viewModel::dismissDelete,
                    onDeleteConfirm = viewModel::confirmDelete,
                    onMessageConsumed = viewModel::consumeMessage,
                    onOpenImport = {
                        val navigation = AppNavigation.openWorkoutImport(currentRoute)
                        if (navigation.shouldPush) {
                            navController.navigateInternal(AppRoutes.WORKOUT_IMPORT)
                        }
                    },
                    onFilterSelected = viewModel::onFilterSelected,
                    onIncludeAbandoned = viewModel::onIncludeAbandoned,
                    onOpenWorkout = { id ->
                        navController.navigate(workoutDetailRoute(id)) {
                            launchSingleTop = true
                        }
                    },
                    onRequestDeleteWorkout = viewModel::requestDeleteWorkout,
                    onDismissDeleteWorkout = viewModel::dismissDeleteWorkout,
                    onConfirmDeleteWorkout = viewModel::confirmDeleteWorkout
                )
            }
            composable(AppRoutes.WORKOUT_IMPORT) {
                val viewModel: WorkoutImportViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                WorkoutImportScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onPickFile = viewModel::onPickFileRequested,
                    onFilePicked = { uri ->
                        if (uri == null) {
                            viewModel.onFileSelectionCancelled()
                        } else {
                            viewModel.onFileSelected(uri.toString())
                        }
                    },
                    onOpenMappingPicker = viewModel::onOpenMappingPicker,
                    onDismissMappingPicker = viewModel::onDismissMappingPicker,
                    onMappingQueryChange = viewModel::onMappingQueryChange,
                    onMapExercise = viewModel::onMapExercise,
                    onToggleWorkout = viewModel::onToggleWorkoutExpanded,
                    onRequestConfirm = viewModel::onRequestConfirm,
                    onDismissConfirm = viewModel::onDismissConfirm,
                    onConfirmImport = viewModel::onConfirmImport,
                    onViewJournal = {
                        navController.popBackStack(AppRoutes.JOURNAL, false)
                    }
                )
            }
            composable(AppRoutes.SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsRoute(
                    viewModel = viewModel,
                    state = state,
                    today = dateProvider.today(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.WEIGHT_DETAILS) {
                val viewModel: WeightDetailsViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                WeightDetailsScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAddToday = { viewModel.openEditor() },
                    onChartRangeSelected = viewModel::onChartRangeSelected,
                    onEditorDateChange = viewModel::onEditorDateChange,
                    onEditorWeightChange = viewModel::onEditorWeightChange,
                    onSave = viewModel::saveEditor,
                    onDismissEditor = viewModel::dismissEditor,
                    onDeleteRequest = viewModel::requestDelete,
                    onDeleteDismiss = viewModel::dismissDelete,
                    onDeleteConfirm = viewModel::confirmDelete,
                    onMessageConsumed = viewModel::consumeMessage,
                    onConfirmWeightChartCoach = {
                        viewModel.markWeightChartSeen()
                        navController.popBackStack()
                    }
                )
            }
            composable(AppRoutes.EXERCISES) { entry ->
                val viewModel: ExerciseListViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val saved by entry.savedStateHandle
                    .getStateFlow(KEY_CATALOG_SAVED, "")
                    .collectAsStateWithLifecycle()
                LaunchedEffect(saved) {
                    when (saved) {
                        "created" -> viewModel.showSaved(true)
                        "updated" -> viewModel.showSaved(false)
                    }
                    if (saved.isNotEmpty()) {
                        entry.savedStateHandle[KEY_CATALOG_SAVED] = ""
                    }
                }
                ExerciseListScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAdd = { navController.navigate(editorRoute(null)) },
                    onEdit = { id -> navController.navigate(editorRoute(id)) },
                    onQueryChange = viewModel::onQueryChange,
                    onCategoryFilter = viewModel::onCategoryFilter,
                    onMuscleFilter = viewModel::onMuscleFilter,
                    onArchiveFilter = viewModel::onArchiveFilter,
                    onClearFilters = viewModel::clearFilters,
                    onArchive = viewModel::archive,
                    onRestore = viewModel::restore,
                    onRequestDelete = viewModel::requestDelete,
                    onDismissDelete = viewModel::dismissDelete,
                    onConfirmDelete = viewModel::confirmDelete,
                    onMessageConsumed = viewModel::consumeMessage
                )
            }
            composable(
                route = AppRoutes.EXERCISE_EDITOR_PATTERN,
                arguments = listOf(
                    navArgument(ARG_EXERCISE_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                val viewModel: ExerciseEditorViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ExerciseEditorScreen(
                    state = state,
                    onBack = viewModel::requestLeave,
                    onNameChange = viewModel::onNameChange,
                    onCategoryChange = viewModel::onCategoryChange,
                    onMovementChange = viewModel::onMovementChange,
                    onMeasurementChange = viewModel::onMeasurementChange,
                    onResistanceChange = viewModel::onResistanceChange,
                    onWeightInterpretationChange = viewModel::onWeightInterpretationChange,
                    onPrimaryMuscleChange = viewModel::onPrimaryMuscleChange,
                    onToggleSecondary = viewModel::onToggleSecondary,
                    onNotesChange = viewModel::onNotesChange,
                    onSave = viewModel::save,
                    onDismissDiscard = viewModel::dismissDiscard,
                    onConfirmDiscard = viewModel::confirmDiscard,
                    onFinished = { saved, created ->
                        if (saved) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(KEY_CATALOG_SAVED, if (created) "created" else "updated")
                        }
                        navController.popBackStack()
                    }
                )
            }
            composable(AppRoutes.TEMPLATES) { entry ->
                val viewModel: TemplateListViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val saved by entry.savedStateHandle
                    .getStateFlow(KEY_TEMPLATE_SAVED, "")
                    .collectAsStateWithLifecycle()
                LaunchedEffect(saved) {
                    when (saved) {
                        "created" -> viewModel.showSaved(true)
                        "updated" -> viewModel.showSaved(false)
                    }
                    if (saved.isNotEmpty()) {
                        entry.savedStateHandle[KEY_TEMPLATE_SAVED] = ""
                    }
                }
                TemplateListScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onAdd = {
                        navController.navigate(templateEditorRoute(null)) {
                            launchSingleTop = true
                        }
                    },
                    onEdit = { id ->
                        navController.navigate(templateEditorRoute(id)) {
                            launchSingleTop = true
                        }
                    },
                    onQueryChange = viewModel::onQueryChange,
                    onArchiveFilter = viewModel::onArchiveFilter,
                    onArchive = viewModel::archive,
                    onRestore = viewModel::restore,
                    onRequestDelete = viewModel::requestDelete,
                    onDismissDelete = viewModel::dismissDelete,
                    onConfirmDelete = viewModel::confirmDelete,
                    onMessageConsumed = viewModel::consumeMessage
                )
            }
            composable(
                route = AppRoutes.TEMPLATE_EDITOR_PATTERN,
                arguments = listOf(
                    navArgument(ARG_TEMPLATE_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                val viewModel: TemplateEditorViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                TemplateEditorScreen(
                    state = state,
                    onBack = viewModel::requestLeave,
                    onNameChange = viewModel::onNameChange,
                    onNotesChange = viewModel::onNotesChange,
                    onOpenPicker = viewModel::openPicker,
                    onClosePicker = viewModel::closePicker,
                    onPickerQuery = viewModel::onPickerQuery,
                    onPickerCategory = viewModel::onPickerCategory,
                    onPickerMuscle = viewModel::onPickerMuscle,
                    onSelectExercise = viewModel::selectExercise,
                    onConfirmDuplicate = viewModel::confirmDuplicate,
                    onDismissDuplicate = viewModel::dismissDuplicate,
                    onRemoveExercise = viewModel::removeExercise,
                    onMoveExercise = viewModel::moveExercise,
                    onToggleExpanded = viewModel::toggleExpanded,
                    onSetCount = viewModel::setSetCount,
                    onAddSet = viewModel::addSet,
                    onRemoveSet = viewModel::removeSet,
                    onMoveSet = viewModel::moveSet,
                    onApplyRemaining = viewModel::applyToRemaining,
                    onApplyAll = viewModel::applyToAll,
                    onMinReps = viewModel::onMinReps,
                    onMaxReps = viewModel::onMaxReps,
                    onLoadKind = viewModel::onLoadKind,
                    onWeight = viewModel::onWeight,
                    onMinutes = viewModel::onMinutes,
                    onSeconds = viewModel::onSeconds,
                    onDistance = viewModel::onDistance,
                    onDistanceUnit = viewModel::onDistanceUnit,
                    onSave = viewModel::save,
                    onDismissDiscard = viewModel::dismissDiscard,
                    onConfirmDiscard = viewModel::confirmDiscard,
                    onScrollConsumed = viewModel::consumeScrollEvent,
                    onFinished = { saved, created ->
                        if (saved) {
                            navController.previousBackStackEntry
                                ?.savedStateHandle
                                ?.set(KEY_TEMPLATE_SAVED, if (created) "created" else "updated")
                        }
                        navController.popBackStack()
                    }
                )
            }
            composable(
                route = AppRoutes.ACTIVE_WORKOUT_PATTERN,
                arguments = listOf(
                    navArgument(ARG_SESSION_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                val viewModel: ActiveWorkoutViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ActiveWorkoutScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onReps = viewModel::onReps,
                    onStepReps = viewModel::stepReps,
                    onLoadKind = viewModel::onLoadKind,
                    onWeight = viewModel::onWeight,
                    onMinutes = viewModel::onMinutes,
                    onSeconds = viewModel::onSeconds,
                    onDistance = viewModel::onDistance,
                    onDistanceUnit = viewModel::onDistanceUnit,
                    onComplete = viewModel::completeSet,
                    onSkip = viewModel::skipSet,
                    onUndoSkip = viewModel::undoSkip,
                    onAddExtra = viewModel::addExtraSet,
                    onFocusConsumed = viewModel::consumeFocusEvent,
                    onToggleExercise = viewModel::toggleExercise,
                    onRemoveExtra = viewModel::removeExtraSet,
                    onRequestFinish = viewModel::requestFinish,
                    onDismissFinish = viewModel::dismissFinish,
                    onConfirmFinish = viewModel::confirmFinish,
                    onRequestAbandon = viewModel::requestAbandon,
                    onDismissAbandon = viewModel::dismissAbandon,
                    onConfirmAbandon = viewModel::confirmAbandon,
                    onFinished = { summary ->
                        navController.openWorkoutComplete(summary)
                    },
                    onAbandoned = {
                        workoutHubViewModel.showAbandoned()
                        navController.popBackStack()
                    },
                    onMessageConsumed = viewModel::consumeMessage
                )
            }
            composable(
                route = AppRoutes.WORKOUT_COMPLETE_PATTERN,
                arguments = listOf(
                    navArgument(ARG_EXERCISES) {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument(ARG_COMPLETED_SETS) {
                        type = NavType.IntType
                        defaultValue = 0
                    },
                    navArgument(ARG_DURATION_MILLIS) {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { entry ->
                val summary = WorkoutCompletionSummary(
                    exerciseCount = entry.arguments?.getInt(ARG_EXERCISES) ?: 0,
                    completedSetCount = entry.arguments?.getInt(ARG_COMPLETED_SETS) ?: 0,
                    durationMillis = entry.arguments?.getLong(ARG_DURATION_MILLIS) ?: 0L
                )
                val onboardingViewModel: OnboardingGuideViewModel = viewModel(factory = factory)
                val onboarding by onboardingViewModel.guide.collectAsStateWithLifecycle()
                WorkoutCompletionScreen(
                    summary = summary,
                    onBackToOverview = { navController.leaveWorkoutComplete() },
                    showHeatmapCta = onboarding.showHeatmapCompletionCta,
                    onSeeWhatYouTrained = { navController.leaveWorkoutComplete() }
                )
            }
            composable(
                route = AppRoutes.WORKOUT_DETAIL_PATTERN,
                arguments = listOf(
                    navArgument(ARG_SESSION_ID) {
                        type = NavType.LongType
                        defaultValue = -1L
                    }
                )
            ) {
                val viewModel: WorkoutDetailViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                WorkoutDetailScreen(
                    state = state,
                    onBack = { navController.popBackStack() },
                    onOpenActive = { id ->
                        navController.popBackStack()
                        navController.navigate(activeWorkoutRoute(id)) {
                            launchSingleTop = true
                        }
                    },
                    onRequestDeleteWorkout = viewModel::requestDeleteWorkout,
                    onDismissDeleteWorkout = viewModel::dismissDeleteWorkout,
                    onConfirmDeleteWorkout = viewModel::confirmDeleteWorkout,
                    onDeleted = {
                        runCatching {
                            navController.getBackStackEntry(AppRoutes.JOURNAL)
                                .savedStateHandle[KEY_WORKOUT_DELETED] = true
                        }
                        if (!navController.popBackStack(AppRoutes.JOURNAL, false)) {
                            navController.popBackStack()
                        }
                    },
                    onMessageConsumed = viewModel::consumeMessage
                )
            }
        }
    }
    if (onboardingGuide.showWorkoutActionCoach && showBottomBar) {
        OnboardingWorkoutSpotlight(
            targetInRoot = workoutActionBounds,
            onDismiss = onboardingGuideViewModel::dismissStartWorkoutCoach,
            onTargetClick = onPrimaryWorkoutClick
        )
    }
    }
    if (hubState.pickerVisible) {
        WorkoutStartPickerSheet(
            templates = hubState.templates,
            starting = hubState.isStarting,
            onDismiss = workoutHubViewModel::dismissPicker,
            onSelectTemplate = workoutHubViewModel::chooseTemplate,
            onManageTemplates = {
                workoutHubViewModel.dismissPicker()
                navController.navigateInternal(AppRoutes.TEMPLATES)
            },
            onCreateTemplate = {
                workoutHubViewModel.dismissPicker()
                navController.navigateInternal(templateEditorRoute(null))
            },
            todayPlanned = hubState.todayPlanned,
            todayInProgress = hubState.todayInProgress,
            onStartScheduled = workoutHubViewModel::startScheduled,
            onContinueScheduled = workoutHubViewModel::continueScheduled
        )
    }
}

@Composable
private fun SettingsRoute(
    viewModel: SettingsViewModel,
    state: app.mymusclemap.ui.settings.SettingsUiState,
    today: LocalDate,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = viewModel.buildExportCsv()
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(csv.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("missing stream")
                }.isSuccess
            }
            viewModel.onExportFinished(success)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(StandardCharsets.UTF_8)
                    }
                }.getOrNull()
            }
            if (content == null) {
                viewModel.onImportReadFailed()
            } else {
                viewModel.importCsv(content)
            }
        }
    }
    val appBackupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val json = viewModel.buildAppBackupJson(
                AppBackupSource(
                    applicationId = context.packageName,
                    versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
                )
            )
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(StandardCharsets.UTF_8))
                    } ?: error("missing stream")
                }.isSuccess
            }
            viewModel.onAppBackupExportFinished(success)
        }
    }
    val appBackupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes()
                    }
                }.getOrNull()
            }
            if (bytes == null) {
                viewModel.onAppBackupReadFailed()
            } else {
                viewModel.restoreAppBackup(bytes)
            }
        }
    }
    SettingsScreen(
        state = state,
        onThemeSelected = viewModel::setThemeMode,
        onSelectDefaultPalette = viewModel::selectDefaultPalette,
        onSelectCustomPalette = viewModel::selectCustomPalette,
        onEditingDarkChange = viewModel::setEditingDark,
        onDraftFieldChange = viewModel::onDraftFieldChange,
        onDraftColorPicked = viewModel::onDraftColorPicked,
        onGenerateDark = viewModel::generateDarkFromLight,
        onSaveDraft = viewModel::saveDraft,
        onCancelDraft = viewModel::cancelDraft,
        onResetCustomDraft = viewModel::resetCustomDraft,
        onExportClick = {
            exportLauncher.launch("my_muscle_map_weight_${today}.csv")
        },
        onImportClick = viewModel::onImportClicked,
        onAppBackupExportClick = {
            appBackupExportLauncher.launch("my_muscle_map_backup_${today}.json")
        },
        onRestoreClick = viewModel::onRestoreClicked,
        onConfirmImportExplanation = {
            viewModel.confirmImportExplanation()
            importLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values"))
        },
        onDismissImportExplanation = viewModel::dismissImportExplanation,
        onDismissImportErrors = viewModel::dismissImportErrors,
        onConfirmRestoreExplanation = {
            viewModel.confirmRestoreExplanation()
            appBackupImportLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
        },
        onDismissRestoreExplanation = viewModel::dismissRestoreExplanation,
        onDismissRestoreErrors = viewModel::dismissRestoreErrors,
        onSendFeedback = {
            val subject = resources.getString(R.string.feedback_email_subject, state.appVersionName)
            val body = resources.getString(
                R.string.feedback_email_body,
                state.appVersionName,
                Build.VERSION.RELEASE,
                "${Build.MANUFACTURER}/${Build.MODEL}"
            )
            val intent = FeedbackComposer.createIntent(subject = subject, body = body)
            if (!FeedbackComposer.launch(context, intent)) {
                viewModel.onFeedbackEmailUnavailable()
            }
        },
        onOpenPrivacyPolicy = {
            val url = state.privacyPolicyUrl
            if (!url.isNullOrBlank()) {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: ActivityNotFoundException) {
                }
            }
        },
        onMessageConsumed = viewModel::consumeMessage,
        onBack = onBack
    )
}

internal fun NavHostController.navigateRoot(route: String) {
    if (route == AppRoutes.OVERVIEW) {
        if (AppNavigation.canonicalRoute(currentDestination?.route) != AppRoutes.OVERVIEW) {
            if (!popBackStack(AppRoutes.OVERVIEW, false)) {
                navigate(AppRoutes.OVERVIEW) {
                    launchSingleTop = true
                }
            }
        }
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun NavHostController.navigateInternal(route: String) {
    if (!AppNavigation.shouldNavigate(currentDestination?.route, route)) {
        return
    }
    navigate(route) {
        launchSingleTop = true
    }
}

internal fun NavHostController.openWorkoutComplete(summary: WorkoutCompletionSummary) {
    if (!AppNavigation.shouldNavigate(currentDestination?.route, AppRoutes.WORKOUT_COMPLETE)) {
        return
    }
    navigate(workoutCompleteRoute(summary)) {
        popUpTo(AppRoutes.OVERVIEW) { inclusive = false }
        launchSingleTop = true
    }
}

internal fun NavHostController.leaveWorkoutComplete() {
    navigateRoot(AppRoutes.OVERVIEW)
}

internal fun NavHostController.openActiveWorkout(sessionId: Long) {
    if (!AppNavigation.shouldNavigate(currentDestination?.route, AppRoutes.ACTIVE_WORKOUT)) {
        return
    }
    navigate(activeWorkoutRoute(sessionId)) {
        launchSingleTop = true
    }
}
