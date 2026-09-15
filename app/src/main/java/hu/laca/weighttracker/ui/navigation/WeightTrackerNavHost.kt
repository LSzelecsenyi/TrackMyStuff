package hu.laca.weighttracker.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import hu.laca.weighttracker.R
import hu.laca.weighttracker.WeightViewModelFactory
import hu.laca.weighttracker.domain.DateProvider
import hu.laca.weighttracker.ui.dashboard.DashboardScreen
import hu.laca.weighttracker.ui.dashboard.DashboardViewModel
import hu.laca.weighttracker.ui.history.HistoryScreen
import hu.laca.weighttracker.ui.history.HistoryViewModel
import hu.laca.weighttracker.ui.settings.SettingsScreen
import hu.laca.weighttracker.ui.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.LocalDate

private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_HISTORY = "history"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun WeightTrackerNavHost(
    factory: WeightViewModelFactory,
    dateProvider: DateProvider
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = currentRoute == ROUTE_DASHBOARD,
                    onClick = { navController.navigateSingleTop(ROUTE_DASHBOARD) },
                    icon = {
                        Icon(
                            Icons.Outlined.Home,
                            contentDescription = stringResource(R.string.nav_dashboard)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_dashboard)) },
                    colors = colors
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_HISTORY,
                    onClick = { navController.navigateSingleTop(ROUTE_HISTORY) },
                    icon = {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = stringResource(R.string.nav_history)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_history)) },
                    colors = colors
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_SETTINGS,
                    onClick = { navController.navigateSingleTop(ROUTE_SETTINGS) },
                    icon = {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    },
                    label = { Text(stringResource(R.string.nav_settings)) },
                    colors = colors
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_DASHBOARD,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize()
        ) {
            composable(ROUTE_DASHBOARD) {
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
                    onMessageConsumed = viewModel::consumeMessage
                )
            }
            composable(ROUTE_HISTORY) {
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
                    onMessageConsumed = viewModel::consumeMessage
                )
            }
            composable(ROUTE_SETTINGS) {
                val viewModel: SettingsViewModel = viewModel(factory = factory)
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsRoute(viewModel = viewModel, state = state, today = dateProvider.today())
            }
        }
    }
}

@Composable
private fun SettingsRoute(
    viewModel: SettingsViewModel,
    state: hu.laca.weighttracker.ui.settings.SettingsUiState,
    today: LocalDate
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
        onMessageConsumed = viewModel::consumeMessage
    )
}

private fun androidx.navigation.NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
