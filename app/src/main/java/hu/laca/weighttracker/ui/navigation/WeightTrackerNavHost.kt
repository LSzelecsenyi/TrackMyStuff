package hu.laca.weighttracker.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import hu.laca.weighttracker.R
import hu.laca.weighttracker.WeightViewModelFactory
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.ui.dashboard.DashboardScreen
import hu.laca.weighttracker.ui.dashboard.DashboardViewModel
import hu.laca.weighttracker.ui.exercises.ExerciseEditorScreen
import hu.laca.weighttracker.ui.exercises.ExerciseEditorViewModel
import hu.laca.weighttracker.ui.exercises.ExerciseListScreen
import hu.laca.weighttracker.ui.exercises.ExerciseListViewModel
import hu.laca.weighttracker.ui.history.HistoryScreen
import hu.laca.weighttracker.ui.history.HistoryViewModel
import hu.laca.weighttracker.ui.history.WorkoutDetailScreen
import hu.laca.weighttracker.ui.history.WorkoutDetailViewModel
import hu.laca.weighttracker.ui.settings.SettingsScreen
import hu.laca.weighttracker.ui.settings.SettingsViewModel
import hu.laca.weighttracker.ui.templates.TemplateEditorScreen
import hu.laca.weighttracker.ui.templates.TemplateEditorViewModel
import hu.laca.weighttracker.ui.templates.TemplateListScreen
import hu.laca.weighttracker.ui.templates.TemplateListViewModel
import hu.laca.weighttracker.ui.workout.ActiveWorkoutScreen
import hu.laca.weighttracker.ui.workout.ActiveWorkoutViewModel
import hu.laca.weighttracker.ui.workout.WorkoutHubScreen
import hu.laca.weighttracker.ui.workout.WorkoutHubViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.LocalDate

private const val ARG_EXERCISE_ID = "exerciseId"
private const val ARG_TEMPLATE_ID = "templateId"
private const val ARG_SESSION_ID = "sessionId"
private const val KEY_CATALOG_SAVED = "catalog_saved"
private const val KEY_TEMPLATE_SAVED = "template_saved"
private const val KEY_WORKOUT_RESULT = "workout_result"

private fun editorRoute(exerciseId: Long?): String {
    return "${AppRoutes.EXERCISE_EDITOR}?$ARG_EXERCISE_ID=${exerciseId ?: -1L}"
}

private fun templateEditorRoute(templateId: Long?): String {
    return "${AppRoutes.TEMPLATE_EDITOR}?$ARG_TEMPLATE_ID=${templateId ?: -1L}"
}

private fun activeWorkoutRoute(sessionId: Long): String {
    return "${AppRoutes.ACTIVE_WORKOUT}?$ARG_SESSION_ID=$sessionId"
}

private fun workoutDetailRoute(sessionId: Long): String {
    return "${AppRoutes.WORKOUT_DETAIL}?$ARG_SESSION_ID=$sessionId"
}

private fun RootTab.icon(): ImageVector {
    return when (route) {
        AppRoutes.OVERVIEW -> Icons.Outlined.Home
        AppRoutes.WORKOUT -> Icons.Outlined.FitnessCenter
        else -> Icons.AutoMirrored.Outlined.MenuBook
    }
}

@Composable
fun WeightTrackerNavHost(
    factory: WeightViewModelFactory,
    dateProvider: DateProvider
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = AppNavigation.showsBottomBar(currentRoute)
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    AppNavigation.rootTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = AppNavigation.canonicalRoute(currentRoute) == tab.route,
                            onClick = { navController.navigateRoot(tab.route) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon(),
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) },
                            colors = colors
                        )
                    }
                }
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
                    onAddToday = { viewModel.openEditor() },
                    onChartRangeSelected = viewModel::onChartRangeSelected,
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
                    onOpenWorkout = { id ->
                        viewModel.dismissDaySheet()
                        navController.navigate(workoutDetailRoute(id)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AppRoutes.WORKOUT) { entry ->
                val viewModel: WorkoutHubViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val result by entry.savedStateHandle
                    .getStateFlow(KEY_WORKOUT_RESULT, "")
                    .collectAsStateWithLifecycle()
                LaunchedEffect(result) {
                    when (result) {
                        "finished" -> viewModel.showFinished()
                        "abandoned" -> viewModel.showAbandoned()
                    }
                    if (result.isNotEmpty()) {
                        entry.savedStateHandle[KEY_WORKOUT_RESULT] = ""
                    }
                }
                WorkoutHubScreen(
                    state = state,
                    onOpenSettings = { navController.navigateInternal(AppRoutes.SETTINGS) },
                    onOpenTemplates = { navController.navigateInternal(AppRoutes.TEMPLATES) },
                    onOpenCatalog = { navController.navigateInternal(AppRoutes.EXERCISES) },
                    onStartTemplate = viewModel::requestStart,
                    onResume = { id -> navController.navigate(activeWorkoutRoute(id)) },
                    onDismissStart = viewModel::dismissStart,
                    onStartWeightChange = viewModel::onStartWeightChange,
                    onConfirmStart = viewModel::confirmStart,
                    onStartedConsumed = viewModel::consumeStartedSession,
                    onOpenStarted = { id -> navController.navigate(activeWorkoutRoute(id)) },
                    onMessageConsumed = viewModel::consumeMessage,
                    onOpenRecent = { id ->
                        navController.navigate(workoutDetailRoute(id)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AppRoutes.JOURNAL) {
                val viewModel: HistoryViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
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
                    onOpenSettings = { navController.navigateInternal(AppRoutes.SETTINGS) },
                    onFilterSelected = viewModel::onFilterSelected,
                    onIncludeAbandoned = viewModel::onIncludeAbandoned,
                    onOpenWorkout = { id ->
                        navController.navigate(workoutDetailRoute(id)) {
                            launchSingleTop = true
                        }
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
                    onAdd = { navController.navigate(templateEditorRoute(null)) },
                    onEdit = { id -> navController.navigate(templateEditorRoute(id)) },
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
                    onPrevious = viewModel::previousExercise,
                    onNext = viewModel::nextExercise,
                    onReps = viewModel::onReps,
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
                    onRemoveExtra = viewModel::removeExtraSet,
                    onRequestFinish = viewModel::requestFinish,
                    onDismissFinish = viewModel::dismissFinish,
                    onConfirmFinish = viewModel::confirmFinish,
                    onRequestAbandon = viewModel::requestAbandon,
                    onDismissAbandon = viewModel::dismissAbandon,
                    onConfirmAbandon = viewModel::confirmAbandon,
                    onFinished = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(KEY_WORKOUT_RESULT, "finished")
                        navController.popBackStack()
                    },
                    onAbandoned = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(KEY_WORKOUT_RESULT, "abandoned")
                        navController.popBackStack()
                    },
                    onMessageConsumed = viewModel::consumeMessage
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
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsRoute(
    viewModel: SettingsViewModel,
    state: hu.laca.weighttracker.ui.settings.SettingsUiState,
    today: LocalDate,
    onBack: () -> Unit
) {
    val context = LocalContext.current
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
            exportLauncher.launch("testsuly_mentes_${today}.csv")
        },
        onImportClick = viewModel::onImportClicked,
        onConfirmImportExplanation = {
            viewModel.confirmImportExplanation()
            importLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values"))
        },
        onDismissImportExplanation = viewModel::dismissImportExplanation,
        onDismissImportErrors = viewModel::dismissImportErrors,
        onMessageConsumed = viewModel::consumeMessage,
        onBack = onBack
    )
}

private fun NavHostController.navigateRoot(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.navigateInternal(route: String) {
    if (!AppNavigation.shouldNavigate(currentDestination?.route, route)) {
        return
    }
    navigate(route) {
        launchSingleTop = true
    }
}
