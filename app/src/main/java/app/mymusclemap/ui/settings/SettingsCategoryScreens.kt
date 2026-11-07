package app.mymusclemap.ui.settings

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.mymusclemap.ui.pro.PRO_DISCOVERY_SYNC_SETTING
import app.mymusclemap.R
import app.mymusclemap.domain.health.HealthSettingsState
import app.mymusclemap.domain.locale.AppLanguage
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel
import app.mymusclemap.domain.theme.PaletteDraftLogic
import app.mymusclemap.domain.theme.PaletteType
import app.mymusclemap.domain.theme.SeedField
import app.mymusclemap.domain.units.UnitSystem
import app.mymusclemap.domain.units.WeightAdjustmentConfiguration
import app.mymusclemap.domain.workout.TimerAudioMode
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.UserMessage
import app.mymusclemap.ui.components.WeeklyGoalEditorSheet
import app.mymusclemap.ui.health.HealthConnectSettingsSection

@Composable
fun AppearancePreferencesScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSelectDefaultPalette: () -> Unit = {},
    onSelectCustomPalette: () -> Unit = {},
    onEditingDarkChange: (Boolean) -> Unit = {},
    onDraftFieldChange: (SeedField, String) -> Unit = { _, _ -> },
    onDraftColorPicked: (SeedField, Int) -> Unit = { _, _ -> },
    onGenerateDark: () -> Unit = {},
    onSaveDraft: () -> Unit = {},
    onCancelDraft: () -> Unit = {},
    onResetCustomDraft: () -> Unit = {},
    onMessageConsumed: () -> Unit = {},
    showLanguageSetting: Boolean = false,
    selectedLanguage: AppLanguage = AppLanguage.EN,
    onLanguageSelected: (AppLanguage) -> Unit = {},
    selectedUnitSystem: UnitSystem = UnitSystem.METRIC,
    onUnitSystemSelected: (UnitSystem) -> Unit = {},
    selectedBodyModel: MuscleMapBodyModel = MuscleMapBodyModel.MALE,
    onBodyModelSelected: (MuscleMapBodyModel) -> Unit = {}
) {
    var lastSaveAt by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    val saveOnce: () -> Unit = {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSaveAt >= 700L) {
            lastSaveAt = now
            onSaveDraft()
        }
    }
    val customEditorVisible = state.draft != null && state.appearance.paletteType == PaletteType.Custom
    val saveEnabled = state.draft != null &&
        SeedField.entries.all { PaletteDraftLogic.fieldError(state.draft.valueFor(it)) == null }
    SettingsPage(
        title = stringResource(R.string.settings_category_appearance),
        onBack = onBack,
        userMessage = state.userMessage,
        onMessageConsumed = onMessageConsumed,
        padForNavigationBars = !customEditorVisible,
        bottomBar = {
            if (customEditorVisible) {
                SettingsSaveBar(
                    saveEnabled = saveEnabled,
                    onSave = saveOnce,
                    onCancel = onCancelDraft
                )
            }
        }
    ) {
        if (showLanguageSetting) {
            LanguageSection(selected = selectedLanguage, onSelected = onLanguageSelected)
            CompactEditorDivider()
        }
        MeasurementUnitsSection(selected = selectedUnitSystem, onSelected = onUnitSystemSelected)
        CompactEditorDivider()
        MuscleMapBodyModelSection(selected = selectedBodyModel, onSelected = onBodyModelSelected)
        CompactEditorDivider()
        AppearanceSection(
            state = state,
            customEditorVisible = customEditorVisible,
            onSelectDefaultPalette = onSelectDefaultPalette,
            onSelectCustomPalette = onSelectCustomPalette,
            onEditingDarkChange = onEditingDarkChange,
            onDraftFieldChange = onDraftFieldChange,
            onDraftColorPicked = onDraftColorPicked,
            onGenerateDark = onGenerateDark,
            onResetCustomDraft = onResetCustomDraft
        )
    }
}

@Composable
fun TrainingSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSetWeeklyGoal: (Int) -> Unit = {},
    onDisableWeeklyGoal: () -> Unit = {},
    lockScreenSetCompletion: Boolean = false,
    lockScreenAccessNote: LockScreenAccessNote? = null,
    onLockScreenSetCompletionChange: (Boolean) -> Unit = {},
    onOpenLockScreenAccessSettings: () -> Unit = {},
    onTimerAudioChange: (TimerAudioMode) -> Unit = {},
    unitSystem: UnitSystem = UnitSystem.METRIC,
    weightAdjustment: WeightAdjustmentConfiguration = WeightAdjustmentConfiguration(),
    onWeightAdjustmentStep: (String) -> Unit = {},
    lockScreenEnablePrompt: LockScreenEnablePrompt? = null,
    onConfirmLockScreenEnable: () -> Unit = {},
    onDismissLockScreenEnable: () -> Unit = {},
    onMessageConsumed: () -> Unit = {}
) {
    var weeklyGoalEditorOpen by remember { mutableStateOf(false) }
    SettingsPage(
        title = stringResource(R.string.training_title),
        onBack = onBack,
        userMessage = state.userMessage,
        onMessageConsumed = onMessageConsumed
    ) {
        TrainingSection(
            state = state,
            onOpenGoal = { weeklyGoalEditorOpen = true },
            lockScreenSetCompletion = lockScreenSetCompletion,
            lockScreenAccessNote = lockScreenAccessNote,
            onLockScreenSetCompletionChange = onLockScreenSetCompletionChange,
            onOpenLockScreenAccessSettings = onOpenLockScreenAccessSettings,
            timerAudio = state.timerAudio,
            onTimerAudioChange = onTimerAudioChange,
            unitSystem = unitSystem,
            weightAdjustment = weightAdjustment,
            onWeightAdjustmentStep = onWeightAdjustmentStep
        )
    }
    if (weeklyGoalEditorOpen) {
        WeeklyGoalEditorSheet(
            status = state.weeklyGoal,
            onSave = onSetWeeklyGoal,
            onDisable = onDisableWeeklyGoal,
            onDismiss = { weeklyGoalEditorOpen = false }
        )
    }
    val enablePrompt = lockScreenEnablePrompt
    if (enablePrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissLockScreenEnable,
            title = { Text(stringResource(R.string.settings_lock_screen_sets_title)) },
            text = {
                Text(
                    stringResource(R.string.settings_lock_screen_sets_explain) + "\n\n" +
                        stringResource(
                            if (enablePrompt == LockScreenEnablePrompt.OpenSettings) {
                                R.string.settings_lock_screen_sets_open_settings_body
                            } else {
                                R.string.settings_lock_screen_sets_permission_reason
                            }
                        )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmLockScreenEnable,
                    modifier = Modifier.testTag(SETTINGS_LOCK_SCREEN_ENABLE)
                ) {
                    Text(
                        stringResource(
                            if (enablePrompt == LockScreenEnablePrompt.OpenSettings) {
                                R.string.action_open_settings
                            } else {
                                R.string.settings_lock_screen_enable
                            }
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissLockScreenEnable,
                    modifier = Modifier.testTag(SETTINGS_LOCK_SCREEN_NOT_NOW)
                ) {
                    Text(stringResource(R.string.settings_lock_screen_not_now))
                }
            }
        )
    }
}

@Composable
fun HealthConnectSettingsPage(
    health: HealthSettingsState,
    onBack: () -> Unit,
    onHealthAction: () -> Unit = {},
    onOpenHealthDetails: () -> Unit = {},
    onRequestExerciseHistory: () -> Unit = {},
    userMessage: UserMessage? = null,
    onMessageConsumed: () -> Unit = {}
) {
    SettingsPage(
        title = stringResource(R.string.health_connect_title),
        onBack = onBack,
        userMessage = userMessage,
        onMessageConsumed = onMessageConsumed
    ) {
        HealthConnectSettingsSection(
            state = health,
            onAction = onHealthAction,
            onOpenDetails = onOpenHealthDetails,
            onRequestHistory = onRequestExerciseHistory
        )
    }
}

@Composable
fun DataBackupScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onExportClick: () -> Unit = {},
    onRestoreClick: () -> Unit = {},
    onConfirmRestoreExplanation: () -> Unit = {},
    onDismissRestoreExplanation: () -> Unit = {},
    onDismissRestoreErrors: () -> Unit = {},
    onMessageConsumed: () -> Unit = {}
) {
    SettingsPage(
        title = stringResource(R.string.settings_category_backup),
        onBack = onBack,
        userMessage = state.userMessage,
        onMessageConsumed = onMessageConsumed
    ) {
        AppBackupSection(onExportClick = onExportClick, onRestoreClick = onRestoreClick)
    }
    if (state.showRestoreExplanation) {
        AlertDialog(
            onDismissRequest = onDismissRestoreExplanation,
            title = { Text(stringResource(R.string.restore_app_backup_title)) },
            text = { Text(stringResource(R.string.restore_app_backup_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmRestoreExplanation) {
                    Text(stringResource(R.string.restore_app_backup_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRestoreExplanation) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (state.restoreErrors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissRestoreErrors,
            title = { Text(stringResource(R.string.restore_app_backup_error_title)) },
            text = {
                Column {
                    state.restoreErrors.take(8).forEach { error ->
                        Text(text = restoreErrorText(error))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissRestoreErrors) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun ProDiscoverySyncSection(
    enabled: Boolean,
    deletionPending: Boolean,
    deletionFailed: Boolean,
    onChange: (Boolean) -> Unit,
    onOpenPrivacy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag(PRO_DISCOVERY_SYNC_SETTING)
    ) {
        Text(stringResource(R.string.pro_discovery_sync_setting_title))
        Text(stringResource(R.string.pro_discovery_sync_setting_body))
        RowSwitch(
            checked = enabled,
            label = stringResource(
                if (enabled) R.string.pro_discovery_sync_enabled else R.string.pro_discovery_sync_disabled
            ),
            onChange = onChange
        )
        if (deletionPending) {
            Text(stringResource(R.string.pro_discovery_sync_deletion_pending))
        }
        if (deletionFailed) {
            Text(stringResource(R.string.pro_discovery_sync_deletion_failed))
        }
        TextButton(onClick = onOpenPrivacy) {
            Text(stringResource(R.string.action_privacy_policy))
        }
    }
}

@Composable
private fun RowSwitch(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun AccountMembershipScreen(
    onBack: () -> Unit,
    onOpenProPlan: () -> Unit = {},
    accountName: String? = null,
    accountEmail: String? = null,
    onSignOut: () -> Unit = {},
    onOpenDeleteAccount: () -> Unit = {},
    showFounderProgram: Boolean = true,
    showFounderBadge: Boolean = false,
    onOpenFounderProgram: () -> Unit = {},
    activitySyncEnabled: Boolean = false,
    activitySyncDeletionPending: Boolean = false,
    activitySyncDeletionFailed: Boolean = false,
    onActivitySyncChange: (Boolean) -> Unit = {},
    onOpenPrivacy: () -> Unit = {}
) {
    SettingsPage(
        title = stringResource(R.string.settings_category_account),
        onBack = onBack
    ) {
        ProPlanSection(onOpenProPlan = onOpenProPlan)
        CompactEditorDivider()
        AccountSection(
            name = accountName,
            email = accountEmail,
            onSignOut = onSignOut,
            onOpenDeleteAccount = onOpenDeleteAccount
        )
        CompactEditorDivider()
        ProDiscoverySyncSection(
            enabled = activitySyncEnabled,
            deletionPending = activitySyncDeletionPending,
            deletionFailed = activitySyncDeletionFailed,
            onChange = onActivitySyncChange,
            onOpenPrivacy = onOpenPrivacy
        )
        if (showFounderProgram) {
            CompactEditorDivider()
            FounderSection(
                foundingMember = showFounderBadge,
                onOpenFounderProgram = onOpenFounderProgram
            )
        }
        DeveloperToolsSection()
    }
}

@Composable
fun AboutStrictScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    showFounderBadge: Boolean = false,
    onSendFeedback: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onMessageConsumed: () -> Unit = {}
) {
    SettingsPage(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        userMessage = state.userMessage,
        onMessageConsumed = onMessageConsumed
    ) {
        AboutSection(
            state = state,
            showFounderBadge = showFounderBadge,
            onSendFeedback = onSendFeedback,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenHelp = onOpenHelp
        )
    }
}
