package app.mymusclemap.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.mymusclemap.R
import app.mymusclemap.data.appbackup.AppBackupError
import app.mymusclemap.data.appbackup.AppBackupErrorCode
import app.mymusclemap.domain.theme.PaletteDraft
import app.mymusclemap.domain.theme.PaletteDraftLogic
import app.mymusclemap.domain.theme.PaletteSaveResult
import app.mymusclemap.domain.theme.PaletteType
import app.mymusclemap.domain.theme.PaletteValidationError
import app.mymusclemap.domain.theme.SeedField
import app.mymusclemap.ui.components.CompactEditorDivider
import app.mymusclemap.ui.components.ExclusiveChoice
import app.mymusclemap.ui.components.ReadableContent
import app.mymusclemap.ui.components.StrictBrandArtwork
import app.mymusclemap.ui.components.StrictBrandLockup
import app.mymusclemap.domain.units.UnitSystem
import app.mymusclemap.domain.units.WeightAdjustmentConfiguration
import app.mymusclemap.domain.units.WeightAdjustmentStep
import app.mymusclemap.domain.workout.PendingWeeklyGoal
import app.mymusclemap.ui.components.CompactEditorSection
import app.mymusclemap.ui.components.PaletteSwatch
import app.mymusclemap.ui.components.UserMessage
import app.mymusclemap.ui.components.UserMessageEffect
import app.mymusclemap.domain.locale.AppLocale
import app.mymusclemap.domain.workout.TimerAudioMode
import app.mymusclemap.ui.components.SegmentedControl
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.AppShapeTokens
import app.mymusclemap.ui.theme.AppTypeTokens
import app.mymusclemap.ui.theme.StrictBrand
import app.mymusclemap.ui.theme.WeightTrackerTheme
import java.math.BigDecimal
import java.util.Locale

internal const val SETTINGS_ROOT = "settings-root"
internal const val SETTINGS_CATEGORY_APPEARANCE = "settings-category-appearance"
internal const val SETTINGS_CATEGORY_TRAINING = "settings-category-training"
internal const val SETTINGS_CATEGORY_HEALTH = "settings-category-health"
internal const val SETTINGS_CATEGORY_BACKUP = "settings-category-backup"
internal const val SETTINGS_CATEGORY_ACCOUNT = "settings-category-account"
internal const val SETTINGS_CATEGORY_ABOUT = "settings-category-about"
internal const val SETTINGS_THEME_SYSTEM = "settings-theme-system"
internal const val SETTINGS_THEME_LIGHT = "settings-theme-light"
internal const val SETTINGS_THEME_DARK = "settings-theme-dark"
internal const val SETTINGS_PALETTE_DEFAULT = "settings-palette-default"
internal const val SETTINGS_PALETTE_CUSTOM = "settings-palette-custom"
internal const val SETTINGS_PREVIEW = "settings-preview"
internal const val SETTINGS_EDITOR = "settings-editor"
internal const val SETTINGS_EDITOR_MODE = "settings-editor-mode"
internal const val SETTINGS_EDITOR_LIGHT = "settings-editor-light"
internal const val SETTINGS_EDITOR_DARK = "settings-editor-dark"
internal const val SETTINGS_GENERATE = "settings-generate"
internal const val SETTINGS_RESET = "settings-reset"
internal const val SETTINGS_SAVE_BAR = "settings-save-bar"
internal const val SETTINGS_SAVE = "settings-save"
internal const val SETTINGS_CANCEL = "settings-cancel"
internal const val SETTINGS_APP_BACKUP_EXPORT = "settings-app-backup-export"
internal const val SETTINGS_APP_BACKUP_RESTORE = "settings-app-backup-restore"
internal const val SETTINGS_HELP_TIPS = "settings-help-tips"
internal const val SETTINGS_SEND_FEEDBACK = "settings-send-feedback"
internal const val SETTINGS_PRIVACY_POLICY = "settings-privacy-policy"
internal const val SETTINGS_APP_VERSION = "settings-app-version"
internal const val SETTINGS_PRO_PLAN = "settings-pro-plan"
internal const val SETTINGS_ACCOUNT = "settings-account"
internal const val SETTINGS_LANGUAGE = "settings-language"
internal const val SETTINGS_LANGUAGE_HU = "settings-language-hu"
internal const val SETTINGS_LANGUAGE_EN = "settings-language-en"
internal const val SETTINGS_SIGN_OUT = "settings-sign-out"
internal const val SETTINGS_DELETE_ACCOUNT = "settings-delete-account"
internal const val SETTINGS_WEEKLY_GOAL = "settings-weekly-goal"
internal const val SETTINGS_LOCK_SCREEN_SETS = "settings-lock-screen-sets"
internal const val SETTINGS_LOCK_SCREEN_ENABLE = "settings-lock-screen-enable"
internal const val SETTINGS_LOCK_SCREEN_NOT_NOW = "settings-lock-screen-not-now"
internal const val SETTINGS_WEIGHT_STEP = "settings-weight-step"
internal const val SETTINGS_WEIGHT_STEP_CUSTOM = "settings-weight-step-custom"
internal const val SETTINGS_WEIGHT_STEP_CUSTOM_INPUT = "settings-weight-step-custom-input"
internal const val SETTINGS_WEIGHT_STEP_SAVE = "settings-weight-step-save"
internal const val SETTINGS_WEIGHT_STEP_ERROR = "settings-weight-step-error"

internal fun settingsWeightStepPresetTag(value: String): String = "settings-weight-step-$value"

enum class LockScreenEnablePrompt {
    RequestPermission,
    OpenSettings
}

enum class LockScreenAccessNote {
    Permission,
    Channel
}

internal fun settingsColorRowTag(field: SeedField): String = "settings-color-${field.name.lowercase(Locale.US)}"

internal fun settingsHexTag(field: SeedField): String = "settings-hex-${field.name.lowercase(Locale.US)}"

internal fun settingsSwatchTag(field: SeedField): String = "settings-swatch-${field.name.lowercase(Locale.US)}"

internal fun settingsPreviewPrimaryTag(hex: String): String = "settings-preview-primary-$hex"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenHealth: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenAbout: () -> Unit
) {
    val categories = listOf(
        SettingsCategory(
            title = stringResource(R.string.settings_category_appearance),
            subtitle = stringResource(R.string.settings_category_appearance_subtitle),
            icon = Icons.Outlined.Palette,
            testTag = SETTINGS_CATEGORY_APPEARANCE,
            onClick = onOpenAppearance
        ),
        SettingsCategory(
            title = stringResource(R.string.training_title),
            subtitle = stringResource(R.string.settings_category_training_subtitle),
            icon = Icons.Outlined.FitnessCenter,
            testTag = SETTINGS_CATEGORY_TRAINING,
            onClick = onOpenTraining
        ),
        SettingsCategory(
            title = stringResource(R.string.health_connect_title),
            subtitle = stringResource(R.string.settings_category_health_subtitle),
            icon = Icons.Outlined.Favorite,
            testTag = SETTINGS_CATEGORY_HEALTH,
            onClick = onOpenHealth
        ),
        SettingsCategory(
            title = stringResource(R.string.settings_category_backup),
            subtitle = stringResource(R.string.settings_category_backup_subtitle),
            icon = Icons.Outlined.CloudSync,
            testTag = SETTINGS_CATEGORY_BACKUP,
            onClick = onOpenBackup
        ),
        SettingsCategory(
            title = stringResource(R.string.settings_category_account),
            subtitle = stringResource(R.string.settings_category_account_subtitle),
            icon = Icons.Outlined.Person,
            testTag = SETTINGS_CATEGORY_ACCOUNT,
            onClick = onOpenAccount
        ),
        SettingsCategory(
            title = stringResource(R.string.about_title),
            subtitle = stringResource(R.string.settings_category_about_subtitle),
            icon = Icons.Outlined.Info,
            testTag = SETTINGS_CATEGORY_ABOUT,
            onClick = onOpenAbout
        )
    )
    SettingsPage(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
        modifier = Modifier.testTag(SETTINGS_ROOT)
    ) {
        categories.forEachIndexed { index, category ->
            SettingsCategoryRow(category)
            if (index < categories.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = AppDimens.strokeThin
                )
            }
        }
    }
}

private data class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun SettingsCategoryRow(category: SettingsCategory) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 72.dp)
            .clickable(onClick = category.onClick, role = Role.Button)
            .testTag(category.testTag)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    StrictBrand.blue.copy(alpha = 0.22f),
                    RoundedCornerShape(AppDimens.cornerCompact)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = StrictBrand.lime,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = category.subtitle,
                style = AppTypeTokens.sectionSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    userMessage: UserMessage? = null,
    onMessageConsumed: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    padForNavigationBars: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = bottomBar
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            SettingsHeader(title = title, onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (padForNavigationBars) {
                            Modifier.navigationBarsPadding()
                        } else {
                            Modifier
                        }
                    )
                    .verticalScroll(rememberScrollState())
            ) {
                ReadableContent(
                    modifier = Modifier.padding(
                        top = AppDimens.headerStackGap,
                        bottom = AppDimens.sectionGap
                    )
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.screenPadding)
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(AppDimens.minTouch)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Text(
            text = title,
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun SettingsSaveBar(
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .testTag(SETTINGS_SAVE_BAR),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = AppDimens.strokeThin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(
            modifier = Modifier
                .widthIn(max = AppDimens.readableMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = AppDimens.screenPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier
                    .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
                    .testTag(SETTINGS_CANCEL)
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
            TextButton(
                onClick = onSave,
                enabled = saveEnabled,
                modifier = Modifier
                    .defaultMinSize(minWidth = AppDimens.minTouch, minHeight = AppDimens.minTouch)
                    .testTag(SETTINGS_SAVE)
            ) {
                Text(
                    text = stringResource(R.string.action_save),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun LanguageSection(
    selected: app.mymusclemap.domain.locale.AppLanguage,
    onSelected: (app.mymusclemap.domain.locale.AppLanguage) -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.settings_language))) {
        Text(
            text = stringResource(R.string.settings_language_body),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SETTINGS_LANGUAGE)
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        Column(modifier = Modifier.selectableGroup()) {
            ChoiceRow(
                label = stringResource(R.string.language_hungarian),
                selected = selected == app.mymusclemap.domain.locale.AppLanguage.HU,
                testTag = SETTINGS_LANGUAGE_HU,
                onClick = { onSelected(app.mymusclemap.domain.locale.AppLanguage.HU) }
            )
            ChoiceRow(
                label = stringResource(R.string.language_english),
                selected = selected == app.mymusclemap.domain.locale.AppLanguage.EN,
                testTag = SETTINGS_LANGUAGE_EN,
                onClick = { onSelected(app.mymusclemap.domain.locale.AppLanguage.EN) }
            )
        }
    }
}

@Composable
internal fun MeasurementUnitsSection(
    selected: app.mymusclemap.domain.units.UnitSystem,
    onSelected: (app.mymusclemap.domain.units.UnitSystem) -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.settings_units))) {
        Text(
            text = stringResource(R.string.settings_units_body),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("settings-units")
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
        ) {
            ExclusiveChoice(
                label = stringResource(R.string.units_metric),
                supporting = stringResource(R.string.units_metric_detail),
                selected = selected == app.mymusclemap.domain.units.UnitSystem.METRIC,
                testTag = "settings-units-metric",
                onClick = { onSelected(app.mymusclemap.domain.units.UnitSystem.METRIC) }
            )
            ExclusiveChoice(
                label = stringResource(R.string.units_imperial),
                supporting = stringResource(R.string.units_imperial_detail),
                selected = selected == app.mymusclemap.domain.units.UnitSystem.IMPERIAL,
                testTag = "settings-units-imperial",
                onClick = { onSelected(app.mymusclemap.domain.units.UnitSystem.IMPERIAL) }
            )
        }
    }
}

@Composable
internal fun MuscleMapBodyModelSection(
    selected: app.mymusclemap.domain.musclemap.MuscleMapBodyModel,
    onSelected: (app.mymusclemap.domain.musclemap.MuscleMapBodyModel) -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.settings_body_model))) {
        Text(
            text = stringResource(R.string.settings_body_model_body),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("settings-body-model")
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(AppDimens.itemGap)
        ) {
            ExclusiveChoice(
                label = stringResource(R.string.body_model_male),
                selected = selected == app.mymusclemap.domain.musclemap.MuscleMapBodyModel.MALE,
                testTag = "settings-body-model-male",
                onClick = { onSelected(app.mymusclemap.domain.musclemap.MuscleMapBodyModel.MALE) }
            )
            ExclusiveChoice(
                label = stringResource(R.string.body_model_female),
                selected = selected == app.mymusclemap.domain.musclemap.MuscleMapBodyModel.FEMALE,
                testTag = "settings-body-model-female",
                onClick = { onSelected(app.mymusclemap.domain.musclemap.MuscleMapBodyModel.FEMALE) }
            )
        }
    }
}

@Composable
internal fun AppearanceSection(
    state: SettingsUiState,
    customEditorVisible: Boolean,
    onSelectDefaultPalette: () -> Unit,
    onSelectCustomPalette: () -> Unit,
    onEditingDarkChange: (Boolean) -> Unit,
    onDraftFieldChange: (SeedField, String) -> Unit,
    onDraftColorPicked: (SeedField, Int) -> Unit,
    onGenerateDark: () -> Unit,
    onResetCustomDraft: () -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.palette_title))) {
        val customSelected = state.appearance.paletteType == PaletteType.Custom
        Column(modifier = Modifier.selectableGroup()) {
            ChoiceRow(
                label = stringResource(R.string.palette_default),
                supporting = stringResource(R.string.palette_default_body),
                selected = !customSelected,
                testTag = SETTINGS_PALETTE_DEFAULT,
                onClick = onSelectDefaultPalette
            )
            ChoiceRow(
                label = stringResource(R.string.palette_custom),
                supporting = stringResource(R.string.palette_custom_body),
                selected = customSelected,
                testTag = SETTINGS_PALETTE_CUSTOM,
                onClick = onSelectCustomPalette
            )
        }
    }
    if (customEditorVisible && state.draft != null) {
        CompactEditorDivider()
        CompactEditorSection(title = settingsKicker(stringResource(R.string.theme_preview_title))) {
            Text(
                text = stringResource(R.string.theme_preview_body),
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.headerStackGap))
            ThemePreviewCard(
                seeds = state.draft.activePreview,
                darkTheme = state.draft.previewIsDark
            )
        }
        CompactEditorDivider()
        CustomPaletteEditor(
            draft = state.draft,
            saveError = state.saveError,
            onEditingDarkChange = onEditingDarkChange,
            onDraftFieldChange = onDraftFieldChange,
            onDraftColorPicked = onDraftColorPicked,
            onGenerateDark = onGenerateDark,
            onResetCustomDraft = onResetCustomDraft
        )
    }
}

@Composable
private fun CustomPaletteEditor(
    draft: PaletteDraft,
    saveError: PaletteSaveResult?,
    onEditingDarkChange: (Boolean) -> Unit,
    onDraftFieldChange: (SeedField, String) -> Unit,
    onDraftColorPicked: (SeedField, Int) -> Unit,
    onGenerateDark: () -> Unit,
    onResetCustomDraft: () -> Unit
) {
    var pickerField by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    CompactEditorSection(
        title = settingsKicker(stringResource(R.string.palette_custom)),
        modifier = Modifier.testTag(SETTINGS_EDITOR)
    ) {
        CompactModeSelector(
            editingDark = draft.editingDark,
            onEditingDarkChange = onEditingDarkChange
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        SeedField.editorFields.forEach { field ->
            key(field, draft.editingDark) {
                ColorFieldRow(
                    field = field,
                    value = draft.valueFor(field),
                    previewRgb = draft.previewRgb(field),
                    onValueChange = { onDraftFieldChange(field, it) },
                    onSwatchClick = { pickerField = field.name }
                )
            }
        }
        saveError?.let { error ->
            Spacer(Modifier.height(AppDimens.headerStackGap))
            Text(
                text = saveErrorText(error),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        TextButton(
            onClick = onGenerateDark,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag(SETTINGS_GENERATE)
        ) {
            Text(text = stringResource(R.string.action_generate_dark))
        }
        TextButton(
            onClick = { confirmReset = true },
            modifier = Modifier
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .testTag(SETTINGS_RESET)
        ) {
            Text(
                text = stringResource(R.string.action_reset_custom_colors),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.action_reset_custom_colors)) },
            text = { Text(stringResource(R.string.reset_custom_colors_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onResetCustomDraft()
                    }
                ) {
                    Text(stringResource(R.string.action_reset_custom_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    pickerField?.let { name ->
        val field = SeedField.valueOf(name)
        ColorPickerDialog(
            title = stringResource(field.labelRes()),
            color = draft.previewRgb(field),
            onConfirm = { rgb ->
                onDraftColorPicked(field, rgb)
                pickerField = null
            },
            onDismiss = { pickerField = null }
        )
    }
}

@Composable
private fun CompactModeSelector(
    editingDark: Boolean,
    onEditingDarkChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SETTINGS_EDITOR_MODE)
    ) {
        ModeTab(
            label = stringResource(R.string.theme_light),
            selected = !editingDark,
            testTag = SETTINGS_EDITOR_LIGHT,
            onClick = { onEditingDarkChange(false) }
        )
        ModeTab(
            label = stringResource(R.string.theme_dark),
            selected = editingDark,
            testTag = SETTINGS_EDITOR_DARK,
            onClick = { onEditingDarkChange(true) }
        )
    }
}

@Composable
private fun RowScope.ModeTab(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = AppTypeTokens.sectionTitle,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
        )
    }
}

@Composable
private fun ColorFieldRow(
    field: SeedField,
    value: String,
    previewRgb: Int,
    onValueChange: (String) -> Unit,
    onSwatchClick: () -> Unit
) {
    val error = PaletteDraftLogic.fieldError(value)
    val label = stringResource(field.labelRes())
    val lineColor = if (error != null) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.outline
    }
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            val cursor = fieldValue.selection.start.coerceIn(0, value.length)
            fieldValue = TextFieldValue(text = value, selection = TextRange(cursor))
        }
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .testTag(settingsColorRowTag(field)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PaletteSwatch(
            color = Color(previewRgb),
            contentDescription = stringResource(R.string.color_swatch_description, label),
            onClick = onSwatchClick,
            modifier = Modifier.testTag(settingsSwatchTag(field))
        )
        Spacer(Modifier.width(AppDimens.itemGap))
        BasicTextField(
            value = fieldValue,
            onValueChange = { incoming ->
                fieldValue = incoming
                onValueChange(incoming.text)
            },
            modifier = Modifier
                .weight(1f)
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .semantics { contentDescription = label }
                .testTag(settingsHexTag(field)),
            textStyle = AppTypeTokens.statValue.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus(force = true)
                    keyboard?.hide()
                }
            ),
            decorationBox = { inner ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = label,
                        style = AppTypeTokens.statCaption,
                        color = if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(Modifier.height(AppDimens.statSecondaryGap))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        inner()
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppDimens.strokeThin)
                            .background(lineColor)
                    )
                    if (error != null) {
                        Spacer(Modifier.height(AppDimens.statSecondaryGap))
                        Text(
                            text = stringResource(R.string.error_color_hex),
                            style = AppTypeTokens.statCaption,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
    supporting: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .semantics { this.selected = selected }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (supporting != null) {
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Text(
                    text = supporting,
                    style = AppTypeTokens.sectionSubtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun TrainingSection(
    state: SettingsUiState,
    onOpenGoal: () -> Unit,
    lockScreenSetCompletion: Boolean,
    lockScreenAccessNote: LockScreenAccessNote?,
    onLockScreenSetCompletionChange: (Boolean) -> Unit,
    onOpenLockScreenAccessSettings: () -> Unit,
    timerAudio: TimerAudioMode,
    onTimerAudioChange: (TimerAudioMode) -> Unit,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    weightAdjustment: WeightAdjustmentConfiguration = WeightAdjustmentConfiguration(),
    onWeightAdjustmentStep: (String) -> Unit = {}
) {
    val subtitle = when (val pending = state.weeklyGoal.pending) {
        is PendingWeeklyGoal.Update -> stringResource(
            R.string.weekly_goal_pending_summary,
            pending.workoutsPerWeek
        )
        is PendingWeeklyGoal.Disable -> stringResource(R.string.weekly_goal_turns_off_next_monday)
        null -> state.weeklyGoal.current.goal?.let { goal ->
            stringResource(R.string.weekly_goal_summary, goal)
        } ?: stringResource(R.string.weekly_goal_not_set)
    }
    CompactEditorSection(title = settingsKicker(stringResource(R.string.training_title))) {
        DataActionRow(
            icon = Icons.Filled.EmojiEvents,
            title = stringResource(R.string.weekly_goal_title),
            subtitle = subtitle,
            testTag = SETTINGS_WEEKLY_GOAL,
            onClick = onOpenGoal
        )
        LockScreenSetCompletionRow(
            enabled = lockScreenSetCompletion,
            note = lockScreenAccessNote,
            onCheckedChange = onLockScreenSetCompletionChange,
            onOpenSettings = onOpenLockScreenAccessSettings
        )
        TimerAudioRow(
            mode = timerAudio,
            onModeChange = onTimerAudioChange
        )
        Spacer(Modifier.height(AppDimens.sectionGap))
        WeightAdjustmentSection(
            unitSystem = unitSystem,
            configuration = weightAdjustment,
            onStepSelected = onWeightAdjustmentStep
        )
    }
}

@Composable
private fun WeightAdjustmentSection(
    unitSystem: UnitSystem,
    configuration: WeightAdjustmentConfiguration,
    onStepSelected: (String) -> Unit
) {
    val step = configuration.stepFor(unitSystem)
    val unit = stringResource(
        if (unitSystem == UnitSystem.IMPERIAL) R.string.unit_lb else R.string.unit_kg
    )
    val storedIsPreset = WeightAdjustmentStep.isPreset(step, unitSystem)
    var customOpen by remember(unitSystem) { mutableStateOf(!storedIsPreset) }
    var customText by remember(unitSystem, step) {
        mutableStateOf(if (storedIsPreset) "" else WeightAdjustmentStep.storedText(step))
    }
    var customError by remember(unitSystem, step) { mutableStateOf(false) }
    LaunchedEffect(unitSystem, step) {
        if (WeightAdjustmentStep.isPreset(step, unitSystem)) {
            customOpen = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SETTINGS_WEIGHT_STEP)
    ) {
        Text(
            text = stringResource(R.string.settings_weight_step_title),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        Text(
            text = stringResource(R.string.settings_weight_step_body),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        WeightStepGrid(
            unitSystem = unitSystem,
            step = step,
            unit = unit,
            storedIsPreset = storedIsPreset,
            onPresetSelected = { labelText ->
                customError = false
                onStepSelected(labelText)
            },
            onCustomSelected = { customOpen = true }
        )
        if (customOpen || !storedIsPreset) {
            Spacer(Modifier.height(AppDimens.itemGap))
            OutlinedTextField(
                value = customText,
                onValueChange = {
                    customText = it
                    customError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SETTINGS_WEIGHT_STEP_CUSTOM_INPUT),
                label = { Text(stringResource(R.string.settings_weight_step_custom_label)) },
                suffix = { Text(unit) },
                isError = customError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        submitCustomWeightStep(customText, unitSystem, onStepSelected) { rejected ->
                            customError = rejected
                        }
                    }
                )
            )
            TextButton(
                onClick = {
                    submitCustomWeightStep(customText, unitSystem, onStepSelected) { rejected ->
                        customError = rejected
                    }
                },
                modifier = Modifier.testTag(SETTINGS_WEIGHT_STEP_SAVE)
            ) {
                Text(stringResource(R.string.action_save))
            }
            if (customError) {
                Text(
                    text = stringResource(R.string.settings_weight_step_invalid),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(SETTINGS_WEIGHT_STEP_ERROR)
                )
            }
        }
    }
}

internal val WeightStepFillColorKey = SemanticsPropertyKey<Color>("WeightStepFillColor")
internal val WeightStepBorderColorKey = SemanticsPropertyKey<Color>("WeightStepBorderColor")
internal val WeightStepContentColorKey = SemanticsPropertyKey<Color>("WeightStepContentColor")

private val WeightStepPillGap = 8.dp

@Composable
private fun WeightStepGrid(
    unitSystem: UnitSystem,
    step: BigDecimal,
    unit: String,
    storedIsPreset: Boolean,
    onPresetSelected: (String) -> Unit,
    onCustomSelected: () -> Unit
) {
    val choices = WeightAdjustmentStep.presets(unitSystem).map { preset ->
        val labelText = WeightAdjustmentStep.storedText(preset)
        WeightStepChoice(
            label = stringResource(R.string.settings_weight_step_value, labelText, unit),
            selected = preset.compareTo(step) == 0,
            testTag = settingsWeightStepPresetTag(labelText),
            onClick = { onPresetSelected(labelText) }
        )
    } + WeightStepChoice(
        label = stringResource(R.string.settings_weight_step_custom),
        selected = !storedIsPreset,
        testTag = SETTINGS_WEIGHT_STEP_CUSTOM,
        onClick = onCustomSelected
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(WeightStepPillGap)
    ) {
        choices.chunked(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(WeightStepPillGap)
            ) {
                row.forEach { choice ->
                    WeightStepPill(
                        choice = choice,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private data class WeightStepChoice(
    val label: String,
    val selected: Boolean,
    val testTag: String,
    val onClick: () -> Unit
)

@Composable
private fun WeightStepPill(
    choice: WeightStepChoice,
    modifier: Modifier = Modifier
) {
    val shape = AppShapeTokens.chip
    val accent = StrictBrand.actionContainer()
    val fill = if (choice.selected) accent else Color.Transparent
    val content = if (choice.selected) {
        StrictBrand.onAction()
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (choice.selected) accent else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clip(shape)
            .border(AppDimens.strokeThin, borderColor, shape)
            .background(fill, shape)
            .selectable(
                selected = choice.selected,
                onClick = choice.onClick,
                role = Role.RadioButton
            )
            .semantics {
                selected = choice.selected
                set(WeightStepFillColorKey, fill)
                set(WeightStepBorderColorKey, borderColor)
                set(WeightStepContentColorKey, content)
            }
            .testTag(choice.testTag)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = choice.label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun submitCustomWeightStep(
    raw: String,
    unitSystem: UnitSystem,
    onStepSelected: (String) -> Unit,
    onRejected: (Boolean) -> Unit
) {
    val parsed = WeightAdjustmentStep.parse(raw, unitSystem)
    if (parsed == null) {
        onRejected(true)
        return
    }
    onRejected(false)
    onStepSelected(WeightAdjustmentStep.storedText(parsed))
}

@Composable
private fun TimerAudioRow(
    mode: TimerAudioMode,
    onModeChange: (TimerAudioMode) -> Unit
) {
    val options = TimerAudioMode.entries
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_timer_audio")
    ) {
        Text(
            text = stringResource(R.string.settings_timer_audio_title),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        SegmentedControl(
            options = options.map { stringResource(it.labelRes()) },
            selectedIndex = options.indexOf(mode).coerceAtLeast(0),
            onSelected = { index -> onModeChange(options[index]) },
            optionTestTags = options.map { it.testTag() }
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Text(
            text = stringResource(mode.explanationRes()),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun TimerAudioMode.labelRes(): Int {
    return when (this) {
        TimerAudioMode.VOICE -> R.string.settings_timer_audio_voice
        TimerAudioMode.BEEPS -> R.string.settings_timer_audio_beeps
        TimerAudioMode.SILENT -> R.string.settings_timer_audio_silent
    }
}

private fun TimerAudioMode.explanationRes(): Int {
    return when (this) {
        TimerAudioMode.VOICE -> R.string.settings_timer_audio_voice_body
        TimerAudioMode.BEEPS -> R.string.settings_timer_audio_beeps_body
        TimerAudioMode.SILENT -> R.string.settings_timer_audio_silent_body
    }
}

private fun TimerAudioMode.testTag(): String {
    return when (this) {
        TimerAudioMode.VOICE -> "settings_timer_audio_voice"
        TimerAudioMode.BEEPS -> "settings_timer_audio_beeps"
        TimerAudioMode.SILENT -> "settings_timer_audio_silent"
    }
}

@Composable
private fun LockScreenSetCompletionRow(
    enabled: Boolean,
    note: LockScreenAccessNote?,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = AppDimens.itemGap)
        ) {
            Text(
                text = stringResource(R.string.settings_lock_screen_sets_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.settings_lock_screen_sets_body),
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(SETTINGS_LOCK_SCREEN_SETS)
        )
    }
    if (note != null) {
        Text(
            text = stringResource(
                if (note == LockScreenAccessNote.Channel) {
                    R.string.settings_lock_screen_sets_channel
                } else {
                    R.string.settings_lock_screen_sets_permission
                }
            ),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AppDimens.statSecondaryGap)
        )
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.action_open_settings))
        }
    }
}

@Composable
internal fun AppBackupSection(
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.app_backup_title))) {
        Text(
            text = stringResource(R.string.app_backup_body),
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        DataActionRow(
            icon = Icons.Outlined.Backup,
            title = stringResource(R.string.action_export_app_backup),
            subtitle = stringResource(R.string.action_export_app_backup_subtitle),
            testTag = SETTINGS_APP_BACKUP_EXPORT,
            onClick = onExportClick
        )
        DataActionRow(
            icon = Icons.Outlined.SettingsBackupRestore,
            title = stringResource(R.string.action_restore_app_backup),
            subtitle = stringResource(R.string.action_restore_app_backup_subtitle),
            testTag = SETTINGS_APP_BACKUP_RESTORE,
            onClick = onRestoreClick
        )
    }
}

@Composable
internal fun AccountSection(
    name: String?,
    email: String?,
    onSignOut: () -> Unit,
    onOpenDeleteAccount: () -> Unit
) {
    var confirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    CompactEditorSection(title = settingsKicker(stringResource(R.string.settings_account))) {
        Text(
            text = name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_account_signed_in),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.testTag(SETTINGS_ACCOUNT)
        )
        if (!email.isNullOrBlank()) {
            Text(
                text = email,
                style = AppTypeTokens.statSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(AppDimens.itemGap))
        DataActionRow(
            icon = Icons.Outlined.Email,
            title = stringResource(R.string.settings_sign_out),
            subtitle = stringResource(R.string.settings_sign_out_body),
            testTag = SETTINGS_SIGN_OUT,
            onClick = { confirm = true }
        )
        Spacer(Modifier.height(AppDimens.sectionGap))
        HorizontalDivider(color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
        DataActionRow(
            icon = Icons.Outlined.Delete,
            title = stringResource(R.string.settings_delete_account),
            subtitle = stringResource(R.string.settings_delete_account_subtitle),
            testTag = SETTINGS_DELETE_ACCOUNT,
            onClick = onOpenDeleteAccount,
            destructive = true
        )
    }
    if (confirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_body)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = false
                    onSignOut()
                }) {
                    Text(stringResource(R.string.settings_sign_out))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
internal fun ProPlanSection(onOpenProPlan: () -> Unit) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.settings_pro_plan))) {
        DataActionRow(
            icon = Icons.Filled.Star,
            title = stringResource(R.string.settings_pro_plan),
            subtitle = stringResource(R.string.settings_pro_plan_subtitle),
            testTag = SETTINGS_PRO_PLAN,
            onClick = onOpenProPlan
        )
    }
}

@Composable
internal fun FounderSection(
    foundingMember: Boolean,
    onOpenFounderProgram: () -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.founder_settings_title))) {
        DataActionRow(
            icon = Icons.Filled.EmojiEvents,
            title = stringResource(
                if (foundingMember) R.string.founder_badge else R.string.founder_open
            ),
            subtitle = stringResource(
                if (foundingMember) R.string.founder_member_subtitle else R.string.founder_open_subtitle
            ),
            testTag = "settings-founder-program",
            onClick = onOpenFounderProgram
        )
    }
}

@Composable
internal fun AboutSection(
    state: SettingsUiState,
    showFounderBadge: Boolean,
    onSendFeedback: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenHelp: () -> Unit
) {
    val usesExternalPrivacyPolicy = !state.privacyPolicyUrl.isNullOrBlank()
    val version = if (showFounderBadge) {
        stringResource(
            R.string.about_version_status,
            state.appVersionName,
            stringResource(R.string.founder_badge)
        )
    } else {
        stringResource(R.string.about_version, state.appVersionName)
    }
    CompactEditorSection(title = settingsKicker(stringResource(R.string.about_title))) {
        StrictBrandLockup(
            artwork = StrictBrandArtwork.Wordmark,
            showTagline = true,
            markWidth = 200.dp
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        Text(
            text = stringResource(R.string.about_product),
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = version,
            style = AppTypeTokens.statSecondary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(SETTINGS_APP_VERSION)
        )
        Spacer(Modifier.height(AppDimens.itemGap))
        DataActionRow(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            title = stringResource(R.string.action_help_tips),
            subtitle = stringResource(R.string.action_help_tips_subtitle),
            testTag = SETTINGS_HELP_TIPS,
            onClick = onOpenHelp
        )
        DataActionRow(
            icon = Icons.Outlined.Email,
            title = stringResource(R.string.action_send_feedback),
            subtitle = stringResource(R.string.action_send_feedback_subtitle),
            testTag = SETTINGS_SEND_FEEDBACK,
            onClick = onSendFeedback
        )
        DataActionRow(
            icon = Icons.Outlined.Policy,
            title = stringResource(R.string.action_privacy_policy),
            subtitle = stringResource(
                if (usesExternalPrivacyPolicy) {
                    R.string.action_privacy_policy_subtitle
                } else {
                    R.string.action_privacy_policy_in_app_subtitle
                }
            ),
            testTag = SETTINGS_PRIVACY_POLICY,
            onClick = onOpenPrivacyPolicy
        )
    }
}

@Composable
private fun DataActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false
) {
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onBackground
    }
    val secondaryColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    val iconTint = if (destructive && enabled) titleColor else secondaryColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppDimens.itemGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypeTokens.sectionTitle,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = AppTypeTokens.statCaption,
                color = secondaryColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = secondaryColor,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun restoreErrorText(error: AppBackupError): String {
    val reason = when (error.code) {
        AppBackupErrorCode.UnsupportedFormatVersion,
        AppBackupErrorCode.UnsupportedSchemaVersion,
        AppBackupErrorCode.UnsupportedContainerVersion ->
            stringResource(R.string.restore_app_backup_error_unsupported)
        AppBackupErrorCode.CorruptBackup,
        AppBackupErrorCode.InvalidPhoto,
        AppBackupErrorCode.InvalidJson ->
            stringResource(R.string.restore_app_backup_error_corrupt)
        AppBackupErrorCode.IncompleteBackup,
        AppBackupErrorCode.MissingField ->
            stringResource(R.string.restore_app_backup_error_incomplete)
        AppBackupErrorCode.RestoreFailed ->
            stringResource(R.string.restore_app_backup_error_failed)
        else -> stringResource(R.string.restore_app_backup_error_invalid)
    }
    val safeDetail = error.code == AppBackupErrorCode.InvalidValue ||
        error.code == AppBackupErrorCode.InvalidType ||
        error.code == AppBackupErrorCode.DuplicateKey ||
        error.code == AppBackupErrorCode.MissingRelation ||
        error.code == AppBackupErrorCode.FileTooLarge
    return if (!safeDetail || error.detail.isNullOrBlank()) reason else "$reason (${error.detail})"
}

@Composable
private fun saveErrorText(error: PaletteSaveResult): String {
    return when (error) {
        PaletteSaveResult.InvalidHex -> stringResource(R.string.error_color_hex)
        is PaletteSaveResult.Contrast -> {
            val prefix = if (error.isDark) {
                stringResource(R.string.contrast_error_dark)
            } else {
                stringResource(R.string.contrast_error_light)
            }
            "$prefix ${stringResource(error.error.messageRes())}"
        }
        is PaletteSaveResult.Success -> ""
    }
}

private fun settingsKicker(text: String): String {
    return text.uppercase(AppLocale.UI)
}

internal fun PaletteDraft.valueFor(field: SeedField): String {
    return if (editingDark) {
        when (field) {
            SeedField.Background -> darkBackground
            SeedField.Primary -> darkPrimary
            SeedField.Secondary -> darkSecondary
            SeedField.Tertiary -> darkTertiary
        }
    } else {
        when (field) {
            SeedField.Background -> lightBackground
            SeedField.Primary -> lightPrimary
            SeedField.Secondary -> lightSecondary
            SeedField.Tertiary -> lightTertiary
        }
    }
}

private fun PaletteDraft.previewRgb(field: SeedField): Int {
    val seeds = activePreview
    return when (field) {
        SeedField.Background -> seeds.background
        SeedField.Primary -> seeds.primary
        SeedField.Secondary -> seeds.secondary
        SeedField.Tertiary -> seeds.tertiary
    }
}

private fun SeedField.labelRes(): Int {
    return when (this) {
        SeedField.Background -> R.string.color_background
        SeedField.Primary -> R.string.color_primary
        SeedField.Secondary -> R.string.color_secondary
        SeedField.Tertiary -> R.string.color_tertiary
    }
}

private fun PaletteValidationError.messageRes(): Int {
    return when (this) {
        PaletteValidationError.InvalidHex -> R.string.error_color_hex
        PaletteValidationError.BackgroundTextContrast -> R.string.contrast_background_text
        PaletteValidationError.SurfaceTextContrast -> R.string.contrast_surface_text
        PaletteValidationError.PrimaryTextContrast -> R.string.contrast_primary_text
        PaletteValidationError.SecondaryTextContrast -> R.string.contrast_secondary_text
        PaletteValidationError.TertiaryTextContrast -> R.string.contrast_tertiary_text
        PaletteValidationError.PrimaryAgainstBackground -> R.string.contrast_primary_background
    }
}

@Preview(showBackground = true, name = "Settings light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Settings dark")
@Composable
private fun SettingsPreview() {
    WeightTrackerTheme {
        SettingsScreen(
            onBack = {},
            onOpenAppearance = {},
            onOpenTraining = {},
            onOpenHealth = {},
            onOpenBackup = {},
            onOpenAccount = {},
            onOpenAbout = {}
        )
    }
}
