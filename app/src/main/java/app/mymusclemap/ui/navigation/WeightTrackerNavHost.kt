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
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mymusclemap.R
import app.mymusclemap.WeightViewModelFactory
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.appbackup.AppBackupWriteResult
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.reports.ReportPeriod
import app.mymusclemap.domain.onboarding.OnboardingResumeTarget
import app.mymusclemap.domain.workout.WorkoutCompletionSummary
import app.mymusclemap.ui.dashboard.DashboardScreen
import app.mymusclemap.ui.dashboard.DashboardViewModel
import app.mymusclemap.ui.dashboard.BodyMeasurementDetailScreen
import app.mymusclemap.ui.dashboard.WeightDetailsScreen
import app.mymusclemap.ui.dashboard.WeightDetailsViewModel
import app.mymusclemap.ui.progress.ProgressPhotoCompareScreen
import app.mymusclemap.ui.progress.ProgressPhotoViewerScreen
import app.mymusclemap.ui.progress.ProgressPhotosScreen
import app.mymusclemap.ui.exercises.ExerciseEditorScreen
import app.mymusclemap.ui.exercises.ExerciseEditorViewModel
import app.mymusclemap.ui.exercises.ExerciseListScreen
import app.mymusclemap.ui.exercises.ExerciseListViewModel
import app.mymusclemap.ui.history.HistoryScreen
import app.mymusclemap.ui.history.HistoryViewModel
import app.mymusclemap.ui.history.WorkoutDetailScreen
import app.mymusclemap.ui.history.WorkoutDetailViewModel
import app.mymusclemap.ui.settings.FeedbackComposer
import app.mymusclemap.ui.onboarding.OnboardingCalendarHistorySpotlight
import app.mymusclemap.ui.onboarding.OnboardingCalendarWeightSpotlight
import app.mymusclemap.ui.onboarding.OnboardingChartSpotlight
import app.mymusclemap.ui.onboarding.OnboardingGuideViewModel
import app.mymusclemap.ui.onboarding.OnboardingHeatmapSpotlight
import app.mymusclemap.ui.onboarding.OnboardingWorkoutSpotlight
import app.mymusclemap.ui.settings.HelpTipsScreen
import app.mymusclemap.ui.settings.PrivacyPolicyScreen
import app.mymusclemap.ui.pro.ProInfoScreen
import app.mymusclemap.ui.pro.ProInfoSheet
import app.mymusclemap.domain.health.HealthSettingsAction
import app.mymusclemap.ui.health.HealthConnectDetailScreen
import app.mymusclemap.ui.health.HealthConnectViewModel
import app.mymusclemap.ui.health.RefreshHealthConnectOnResume
import app.mymusclemap.ui.health.openHealthConnectManageAccess
import app.mymusclemap.ui.health.openHealthConnectProviderInstall
import app.mymusclemap.ui.health.rememberHealthConnectPermissionLaunch
import app.mymusclemap.ui.settings.SettingsScreen
import app.mymusclemap.ui.settings.SettingsViewModel
import app.mymusclemap.ui.reports.ReportDetailScreen
import app.mymusclemap.ui.reports.ReportsScreen
import app.mymusclemap.ui.reports.ReportsViewModel
import app.mymusclemap.ui.statistics.StatisticsExerciseDetailScreen
import app.mymusclemap.ui.statistics.StatisticsExerciseListScreen
import app.mymusclemap.ui.statistics.StatisticsMuscleDistributionScreen
import app.mymusclemap.ui.statistics.StatisticsRestScreen
import app.mymusclemap.ui.statistics.StatisticsScreen
import app.mymusclemap.ui.statistics.StatisticsViewModel
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
private const val ARG_STATISTICS_EXERCISE_ID = "exerciseId"
private const val ARG_REPORT_KIND = "kind"
private const val ARG_REPORT_START = "start"
private const val ARG_BODY_TYPE = "type"
private const val ARG_PHOTO_ID = "photoId"
private const val ARG_PHOTO_FIRST = "first"
private const val ARG_PHOTO_SECOND = "second"
private const val KEY_CATALOG_SAVED = "catalog_saved"
private const val KEY_TEMPLATE_SAVED = "template_saved"
private const val KEY_WORKOUT_DELETED = "workout_deleted"

private fun editorRoute(exerciseId: Long?): String {
    return "${AppRoutes.EXERCISE_EDITOR}?$ARG_EXERCISE_ID=${exerciseId ?: -1L}"
}

private fun templateEditorRoute(templateId: Long?): String {
    return "${AppRoutes.TEMPLATE_EDITOR}?$ARG_TEMPLATE_ID=${templateId ?: -1L}"
}

private fun statisticsExerciseRoute(exerciseId: Long): String {
    return "${AppRoutes.STATISTICS_EXERCISE}?$ARG_STATISTICS_EXERCISE_ID=$exerciseId"
}

private fun reportDetailRoute(period: ReportPeriod): String {
    return "${AppRoutes.REPORT_DETAIL}?$ARG_REPORT_KIND=${period.kind.name}&$ARG_REPORT_START=${period.startInclusive}"
}

@Composable
private fun statisticsViewModel(
    navController: NavHostController,
    factory: WeightViewModelFactory,
    entry: NavBackStackEntry
): StatisticsViewModel {
    val parent = remember(entry) {
        navController.getBackStackEntry(AppRoutes.STATISTICS_GRAPH)
    }
    return viewModel(parent, factory = factory)
}

@Composable
private fun progressPhotosViewModel(
    navController: NavHostController,
    factory: WeightViewModelFactory,
    entry: NavBackStackEntry
): app.mymusclemap.ui.progress.ProgressPhotosViewModel {
    val parent = remember(entry) {
        navController.getBackStackEntry(AppRoutes.BODY_PROGRESS_GRAPH)
    }
    return viewModel(parent, factory = factory)
}

@Composable
private fun bodyProgressViewModel(
    navController: NavHostController,
    factory: WeightViewModelFactory,
    entry: NavBackStackEntry
): WeightDetailsViewModel {
    val parent = remember(entry) {
        navController.getBackStackEntry(AppRoutes.BODY_PROGRESS_GRAPH)
    }
    return viewModel(parent, factory = factory)
}

@Composable
private fun reportsViewModel(
    navController: NavHostController,
    factory: WeightViewModelFactory,
    entry: NavBackStackEntry
): ReportsViewModel {
    val parent = remember(entry) {
        navController.getBackStackEntry(AppRoutes.REPORTS_GRAPH)
    }
    return viewModel(parent, factory = factory)
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
    onOpenedNewTemplate: () -> Unit = {},
    openPrivacyPolicy: Boolean = false,
    onOpenedPrivacyPolicy: () -> Unit = {},
    openOverviewRequest: Int = 0
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
    var heatmapBounds by remember { mutableStateOf(Rect.Zero) }
    var calendarBounds by remember { mutableStateOf(Rect.Zero) }
    var todayBounds by remember { mutableStateOf(Rect.Zero) }
    var chartBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(openOverviewRequest) {
        if (openOverviewRequest > 0) {
            navController.navigateRoot(AppRoutes.OVERVIEW)
        }
    }
    LaunchedEffect(openPrivacyPolicy) {
        if (openPrivacyPolicy) {
            navController.navigate(AppRoutes.PRIVACY) {
                launchSingleTop = true
            }
            onOpenedPrivacyPolicy()
        }
    }
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
    LaunchedEffect(onboardingGuide.resumeTarget) {
        if (onboardingGuide.resumeTarget == OnboardingResumeTarget.WeightChart) {
            onboardingGuideViewModel.requestChartCoach()
        }
    }
    LaunchedEffect(openNewTemplate) {
        if (!openNewTemplate) return@LaunchedEffect
        workoutHubViewModel.requestCreatePlan {
            navController.navigateInternal(templateEditorRoute(null))
        }
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
                    selectedRoute = AppNavigation.bottomTabRoute(currentRoute),
                    hasActiveSession = hubState.activeSession != null,
                    onOverview = { navController.navigateRoot(AppRoutes.OVERVIEW) },
                    onStatistics = { navController.navigateRoot(AppRoutes.STATISTICS_GRAPH) },
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
                val healthViewModel: HealthConnectViewModel = viewModel(factory = factory)
                val health by healthViewModel.card.collectAsStateWithLifecycle()
                RefreshHealthConnectOnResume(healthViewModel::refresh)
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
                    onDisplayedMonthChange = viewModel::showMonth,
                    onSetWeeklyGoal = viewModel::setWeeklyGoal,
                    onDisableWeeklyGoal = viewModel::disableWeeklyGoal,
                    health = health,
                    onOpenHealthSettings = { navController.navigateInternal(AppRoutes.SETTINGS) },
                    onOpenHealthDetails = { navController.navigateInternal(AppRoutes.HEALTH_CONNECT) },
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
                        viewModel.requestCreatePlan {
                            viewModel.dismissSchedulePicker()
                            navController.navigateInternal(templateEditorRoute(null))
                        }
                    },
                    onDismissLocked = viewModel::consumeLockedFeature,
                    onContinueOnboarding = {
                        when (state.onboarding.resumeTarget) {
                            OnboardingResumeTarget.CreatePlan -> {
                                viewModel.requestCreatePlan {
                                    navController.navigateInternal(templateEditorRoute(null))
                                }
                            }
                            OnboardingResumeTarget.StartWorkout -> {
                                onboardingGuideViewModel.requestStartWorkoutCoach()
                            }
                            OnboardingResumeTarget.Heatmap -> {
                                onboardingGuideViewModel.requestHeatmapCoach()
                            }
                            OnboardingResumeTarget.WeightPrompt -> {
                                onboardingGuideViewModel.requestCalendarWeightCoach()
                            }
                            OnboardingResumeTarget.WeightChart -> {
                                onboardingGuideViewModel.requestChartCoach()
                            }
                            OnboardingResumeTarget.Calendar -> {
                                onboardingGuideViewModel.requestCalendarHistoryCoach()
                            }
                            OnboardingResumeTarget.None -> Unit
                        }
                    },
                    onDismissOnboardingReminder = viewModel::dismissOnboardingReminder,
                    heatmapRevealRequested = onboardingGuide.heatmapRevealRequested,
                    calendarRevealRequested = onboardingGuide.calendarRevealRequested,
                    chartRevealRequested = onboardingGuide.chartRevealRequested,
                    onHeatmapBounds = { bounds ->
                        if (bounds != heatmapBounds) {
                            heatmapBounds = bounds
                        }
                    },
                    onCalendarBounds = { bounds ->
                        if (bounds != calendarBounds) {
                            calendarBounds = bounds
                        }
                    },
                    onTodayBounds = { bounds ->
                        if (bounds != todayBounds) {
                            todayBounds = bounds
                        }
                    },
                    onChartBounds = { bounds ->
                        if (bounds != chartBounds) {
                            chartBounds = bounds
                        }
                    },
                    onDashboardTargetRevealed = onboardingGuideViewModel::markDashboardTargetReady
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
                val healthViewModel: HealthConnectViewModel = viewModel(factory = factory)
                val health by healthViewModel.settings.collectAsStateWithLifecycle()
                SettingsRoute(
                    viewModel = viewModel,
                    state = state,
                    today = dateProvider.today(),
                    health = health,
                    onHealthRefresh = healthViewModel::refresh,
                    onBack = { navController.popBackStack() },
                    onOpenHelp = { navController.navigateInternal(AppRoutes.HELP) },
                    onOpenPrivacy = { navController.navigateInternal(AppRoutes.PRIVACY) },
                    onOpenHealthDetails = { navController.navigateInternal(AppRoutes.HEALTH_CONNECT) }
                )
            }
            composable(AppRoutes.HEALTH_CONNECT) {
                val healthViewModel: HealthConnectViewModel = viewModel(factory = factory)
                val detail by healthViewModel.detail.collectAsStateWithLifecycle()
                RefreshHealthConnectOnResume(healthViewModel::refresh)
                HealthConnectDetailScreen(
                    state = detail,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(AppRoutes.HELP) {
                HelpTipsScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.PRIVACY) {
                PrivacyPolicyScreen(onBack = { navController.popBackStack() })
            }
            composable(AppRoutes.PRO_INFO) {
                ProInfoScreen(onBack = { navController.popBackStack() })
            }
            navigation(
                route = AppRoutes.STATISTICS_GRAPH,
                startDestination = AppRoutes.STATISTICS
            ) {
                composable(AppRoutes.STATISTICS) { entry ->
                    val viewModel = statisticsViewModel(navController, factory, entry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    StatisticsScreen(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onRangeSelected = viewModel::onRangeSelected,
                        onDismissLocked = viewModel::consumeLockedFeature,
                        onOpenMuscleDistribution = {
                            navController.navigateInternal(AppRoutes.STATISTICS_MUSCLES)
                        },
                        onOpenRest = { navController.navigateInternal(AppRoutes.STATISTICS_REST) },
                        onOpenExercises = {
                            navController.navigateInternal(AppRoutes.STATISTICS_EXERCISES)
                        },
                        onOpenExercise = { id ->
                            navController.navigateInternal(statisticsExerciseRoute(id))
                        },
                        onOpenReports = { navController.navigateInternal(AppRoutes.REPORTS_GRAPH) }
                    )
                }
                composable(AppRoutes.STATISTICS_MUSCLES) { entry ->
                    val viewModel = statisticsViewModel(navController, factory, entry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    StatisticsMuscleDistributionScreen(
                        muscles = state.dashboard.muscleDistribution,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoutes.STATISTICS_REST) { entry ->
                    val viewModel = statisticsViewModel(navController, factory, entry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    StatisticsRestScreen(
                        rest = state.dashboard.restBetweenSessions,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(AppRoutes.STATISTICS_EXERCISES) { entry ->
                    val viewModel = statisticsViewModel(navController, factory, entry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    StatisticsExerciseListScreen(
                        exercises = state.dashboard.exercises,
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { id ->
                            navController.navigateInternal(statisticsExerciseRoute(id))
                        }
                    )
                }
                composable(
                    route = AppRoutes.STATISTICS_EXERCISE_PATTERN,
                    arguments = listOf(
                        navArgument(ARG_STATISTICS_EXERCISE_ID) {
                            type = NavType.LongType
                            defaultValue = -1L
                        }
                    )
                ) { entry ->
                    val viewModel = statisticsViewModel(navController, factory, entry)
                    val exerciseId = entry.arguments?.getLong(ARG_STATISTICS_EXERCISE_ID) ?: -1L
                    StatisticsExerciseDetailScreen(
                        summary = viewModel.exercise(exerciseId),
                        onBack = { navController.popBackStack() }
                    )
                }
                navigation(
                    route = AppRoutes.REPORTS_GRAPH,
                    startDestination = AppRoutes.REPORTS
                ) {
                    composable(AppRoutes.REPORTS) { entry ->
                        val viewModel = reportsViewModel(navController, factory, entry)
                        val state by viewModel.uiState.collectAsStateWithLifecycle()
                        ReportsScreen(
                            state = state,
                            onBack = { navController.popBackStack() },
                            onKindSelected = viewModel::onKindSelected,
                            onOpenReport = { period ->
                                viewModel.openReport(period) {
                                    navController.navigateInternal(reportDetailRoute(period))
                                }
                            },
                            onLockedReport = viewModel::showLockedReports,
                            onDismissLocked = viewModel::consumeLockedFeature
                        )
                    }
                    composable(
                        route = AppRoutes.REPORT_DETAIL_PATTERN,
                        arguments = listOf(
                            navArgument(ARG_REPORT_KIND) {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument(ARG_REPORT_START) {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { entry ->
                        val viewModel = reportsViewModel(navController, factory, entry)
                        val state by viewModel.uiState.collectAsStateWithLifecycle()
                        val kindName = entry.arguments?.getString(ARG_REPORT_KIND)
                        val denial = viewModel.denial(kindName)
                        ReportDetailScreen(
                            loading = state.loading,
                            summary = if (denial != null) {
                                null
                            } else {
                                viewModel.summary(kindName, entry.arguments?.getString(ARG_REPORT_START))
                            },
                            lockedFeature = denial,
                            onDismissLocked = { navController.popBackStack() },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
            navigation(
                route = AppRoutes.BODY_PROGRESS_GRAPH,
                startDestination = AppRoutes.WEIGHT_DETAILS
            ) {
                composable(AppRoutes.WEIGHT_DETAILS) { entry ->
                    val viewModel = bodyProgressViewModel(navController, factory, entry)
                    val photosViewModel = progressPhotosViewModel(navController, factory, entry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val photos by photosViewModel.uiState.collectAsStateWithLifecycle()
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
                        onOpenMeasurement = { typeCode ->
                            navController.navigateInternal(AppNavigation.bodyMeasurementRoute(typeCode))
                        },
                        onLockedMeasurement = viewModel::showBodyMeasurementLocked,
                        onDismissLocked = viewModel::dismissLockedFeature,
                        progressPhotoCount = photos.photos.size,
                        progressPhotoLatestDate = photos.latest?.date,
                        progressPhotoProBadge = photos.showProBadge,
                        progressPhotoThumbnails = photos.recentThumbnails,
                        progressPhotoMissing = photos.latest?.missing == true,
                        onOpenProgressPhotos = {
                            navController.navigateInternal(AppRoutes.PROGRESS_PHOTOS)
                        }
                    )
                }
                composable(AppRoutes.PROGRESS_PHOTOS) { entry ->
                    val photosViewModel = progressPhotosViewModel(navController, factory, entry)
                    val photos by photosViewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    ProgressPhotosScreen(
                        state = photos,
                        onBack = { navController.popBackStack() },
                        onAdd = photosViewModel::requestAdd,
                        onConsumeLaunchPicker = photosViewModel::consumeLaunchPicker,
                        onPicked = { uri ->
                            if (uri == null) {
                                photosViewModel.onPickerCancelled()
                            } else {
                                val stream = context.contentResolver.openInputStream(uri)
                                if (stream == null) {
                                    photosViewModel.import(java.io.ByteArrayInputStream(byteArrayOf()))
                                } else {
                                    photosViewModel.import(stream)
                                }
                            }
                        },
                        onOpenPhoto = { id ->
                            navController.navigateInternal(AppNavigation.progressPhotoRoute(id))
                        },
                        onBeginCompare = photosViewModel::beginCompare,
                        onToggleCompare = photosViewModel::toggleCompareSelection,
                        onCancelCompare = photosViewModel::cancelCompare,
                        onShowCompare = { first, second ->
                            photosViewModel.cancelCompare()
                            navController.navigateInternal(
                                AppNavigation.progressPhotoCompareRoute(first, second)
                            )
                        },
                        onDismissLocked = photosViewModel::dismissLockedFeature,
                        decode = photosViewModel::decode
                    )
                }
                composable(
                    route = AppRoutes.PROGRESS_PHOTO_PATTERN,
                    arguments = listOf(navArgument(ARG_PHOTO_ID) { type = NavType.LongType })
                ) { entry ->
                    val photosViewModel = progressPhotosViewModel(navController, factory, entry)
                    val photos by photosViewModel.uiState.collectAsStateWithLifecycle()
                    val photoId = entry.arguments?.getLong(ARG_PHOTO_ID) ?: -1L
                    val photo = photos.photos.find { it.id == photoId }
                    if (photos.loaded && photo == null) {
                        LaunchedEffect(photoId) { navController.popBackStack() }
                    } else if (photo != null) {
                        var bitmap by remember(photo.fileName) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(photo.fileName, photo.missing) {
                            bitmap = if (photo.missing) {
                                null
                            } else {
                                photosViewModel.decode(photo.fileName, 1600)
                            }
                        }
                        ProgressPhotoViewerScreen(
                            date = photo.date,
                            bitmap = bitmap,
                            missing = photo.missing,
                            onBack = { navController.popBackStack() },
                            onDelete = { photosViewModel.delete(photo.id) }
                        )
                    }
                }
                composable(
                    route = AppRoutes.PROGRESS_PHOTO_COMPARE_PATTERN,
                    arguments = listOf(
                        navArgument(ARG_PHOTO_FIRST) { type = NavType.LongType },
                        navArgument(ARG_PHOTO_SECOND) { type = NavType.LongType }
                    )
                ) { entry ->
                    val photosViewModel = progressPhotosViewModel(navController, factory, entry)
                    val photos by photosViewModel.uiState.collectAsStateWithLifecycle()
                    val firstId = entry.arguments?.getLong(ARG_PHOTO_FIRST) ?: -1L
                    val secondId = entry.arguments?.getLong(ARG_PHOTO_SECOND) ?: -1L
                    val first = photos.photos.find { it.id == firstId }
                    val second = photos.photos.find { it.id == secondId }
                    if (photos.loaded && (first == null || second == null)) {
                        LaunchedEffect(firstId, secondId) { navController.popBackStack() }
                    } else if (first != null && second != null) {
                        var before by remember(first.fileName) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        var after by remember(second.fileName) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(first.fileName, first.missing, second.fileName, second.missing) {
                            before = if (first.missing) null else photosViewModel.decode(first.fileName, 1600)
                            after = if (second.missing) null else photosViewModel.decode(second.fileName, 1600)
                        }
                        ProgressPhotoCompareScreen(
                            beforeDate = first.date,
                            before = before,
                            beforeMissing = first.missing,
                            afterDate = second.date,
                            after = after,
                            afterMissing = second.missing,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    route = AppRoutes.BODY_MEASUREMENT_PATTERN,
                    arguments = listOf(navArgument(ARG_BODY_TYPE) { type = NavType.StringType })
                ) { entry ->
                    val viewModel = bodyProgressViewModel(navController, factory, entry)
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val typeCode = entry.arguments?.getString(ARG_BODY_TYPE).orEmpty()
                    val detail = state.details[typeCode]
                    if (detail == null) {
                        LaunchedEffect(typeCode) { navController.popBackStack() }
                    } else {
                        BodyMeasurementDetailScreen(
                            detail = detail,
                            today = state.today,
                            editor = state.bodyEditor,
                            lockedFeature = state.lockedFeature,
                            userMessage = state.userMessage,
                            onBack = { navController.popBackStack() },
                            onRangeSelected = viewModel::onBodyChartRangeSelected,
                            onAdd = {
                                val type = detail.type
                                if (type != null) viewModel.openBodyEditor(type)
                            },
                            onOpenHistory = { measurement ->
                                val type = detail.type
                                if (type != null) viewModel.openBodyEditor(type, measurement)
                            },
                            onBodyDateChange = viewModel::onBodyEditorDateChange,
                            onBodyValueChange = viewModel::onBodyEditorValueChange,
                            onSaveBody = viewModel::saveBodyEditor,
                            onDismissBodyEditor = viewModel::dismissBodyEditor,
                            onBodyDeleteRequest = viewModel::requestBodyDelete,
                            onBodyDeleteDismiss = viewModel::dismissBodyDelete,
                            onBodyDeleteConfirm = viewModel::confirmBodyDelete,
                            onDismissLocked = viewModel::dismissLockedFeature,
                            onMessageConsumed = viewModel::consumeMessage,
                            onOpened = { viewModel.setMeasurementDetailVisible(true) },
                            onClosed = viewModel::leaveMeasurementDetail
                        )
                    }
                }
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
                        viewModel.requestCreate {
                            navController.navigate(templateEditorRoute(null)) {
                                launchSingleTop = true
                            }
                        }
                    },
                    onDismissLocked = viewModel::consumeLockedFeature,
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
                    onDismissLocked = viewModel::consumeLockedFeature,
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
    if (onboardingGuide.showHeatmapSpotlight &&
        AppNavigation.canonicalRoute(currentRoute) == AppRoutes.OVERVIEW
    ) {
        OnboardingHeatmapSpotlight(
            targetInRoot = heatmapBounds,
            onDismiss = onboardingGuideViewModel::dismissHeatmapCoach,
            onConfirm = onboardingGuideViewModel::confirmHeatmapCoach
        )
    }
    if (onboardingGuide.showCalendarWeightSpotlight &&
        AppNavigation.canonicalRoute(currentRoute) == AppRoutes.OVERVIEW
    ) {
        OnboardingCalendarWeightSpotlight(
            dayInRoot = todayBounds,
            calendarInRoot = calendarBounds,
            onDismiss = onboardingGuideViewModel::dismissCalendarWeightCoach,
            onConfirm = onboardingGuideViewModel::confirmCalendarWeightCoach
        )
    }
    if (onboardingGuide.showChartSpotlight &&
        AppNavigation.canonicalRoute(currentRoute) == AppRoutes.OVERVIEW
    ) {
        OnboardingChartSpotlight(
            targetInRoot = chartBounds,
            onDismiss = onboardingGuideViewModel::dismissChartCoach,
            onConfirm = onboardingGuideViewModel::confirmChartCoach
        )
    }
    if (onboardingGuide.showCalendarHistorySpotlight &&
        AppNavigation.canonicalRoute(currentRoute) == AppRoutes.OVERVIEW
    ) {
        OnboardingCalendarHistorySpotlight(
            targetInRoot = calendarBounds,
            onDismiss = onboardingGuideViewModel::dismissCalendarHistoryCoach,
            onConfirm = onboardingGuideViewModel::confirmCalendarHistoryCoach
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
                workoutHubViewModel.requestCreatePlan {
                    workoutHubViewModel.dismissPicker()
                    navController.navigateInternal(templateEditorRoute(null))
                }
            },
            todayPlanned = hubState.todayPlanned,
            todayInProgress = hubState.todayInProgress,
            onStartScheduled = workoutHubViewModel::startScheduled,
            onContinueScheduled = workoutHubViewModel::continueScheduled
        )
    }
    hubState.lockedFeature?.let { feature ->
        ProInfoSheet(
            feature = feature,
            onDismiss = workoutHubViewModel::consumeLockedFeature
        )
    }
}

@Composable
private fun SettingsRoute(
    viewModel: SettingsViewModel,
    state: app.mymusclemap.ui.settings.SettingsUiState,
    today: LocalDate,
    health: app.mymusclemap.domain.health.HealthSettingsState,
    onHealthRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenHealthDetails: () -> Unit
) {
    val launchHealthPermissions = rememberHealthConnectPermissionLaunch(onHealthRefresh)
    RefreshHealthConnectOnResume(onHealthRefresh)
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
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val source = AppBackupSource(
                applicationId = context.packageName,
                versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            )
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        viewModel.writeAppBackup(source, stream)
                    } ?: AppBackupWriteResult.Failed
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    AppBackupWriteResult.Failed
                }
            }
            viewModel.onAppBackupExportFinished(result)
        }
    }
    val appBackupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.restoreAppBackup {
            context.contentResolver.openInputStream(uri) ?: error("missing stream")
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
            appBackupExportLauncher.launch("strict-backup-$today.zip")
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
            appBackupImportLauncher.launch(
                arrayOf(
                    "application/zip",
                    "application/json",
                    "application/octet-stream",
                    "text/*",
                    "*/*"
                )
            )
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
            } else {
                onOpenPrivacy()
            }
        },
        onOpenHelp = onOpenHelp,
        onMessageConsumed = viewModel::consumeMessage,
        onSetWeeklyGoal = viewModel::setWeeklyGoal,
        onDisableWeeklyGoal = viewModel::disableWeeklyGoal,
        onBack = onBack,
        health = health,
        onOpenHealthDetails = onOpenHealthDetails,
        onHealthAction = {
            val opened = when (health.action) {
                HealthSettingsAction.InstallOrUpdate -> openHealthConnectProviderInstall(context)
                HealthSettingsAction.RequestPermissions -> {
                    launchHealthPermissions()
                    true
                }
                HealthSettingsAction.ManageAccess -> openHealthConnectManageAccess(context)
                HealthSettingsAction.None -> true
            }
            if (!opened) {
                viewModel.onHealthConnectOpenFailed()
            }
        }
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
