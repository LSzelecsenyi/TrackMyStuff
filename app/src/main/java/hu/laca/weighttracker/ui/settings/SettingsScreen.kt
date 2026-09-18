package hu.laca.weighttracker.ui.settings

import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.csv.WeightCsv
import hu.laca.weighttracker.domain.theme.AppearanceSettings
import hu.laca.weighttracker.domain.theme.PaletteDraft
import hu.laca.weighttracker.domain.theme.PaletteDraftLogic
import hu.laca.weighttracker.domain.theme.PaletteSaveResult
import hu.laca.weighttracker.domain.theme.PaletteType
import hu.laca.weighttracker.domain.theme.PaletteValidationError
import hu.laca.weighttracker.domain.theme.SeedField
import hu.laca.weighttracker.domain.theme.ThemeMode
import hu.laca.weighttracker.ui.components.CompactEditorDivider
import hu.laca.weighttracker.ui.components.CompactEditorSection
import hu.laca.weighttracker.ui.components.PaletteSwatch
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppTypeTokens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.util.Locale

internal const val SETTINGS_ROOT = "settings-root"
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
internal const val SETTINGS_EXPORT = "settings-export"
internal const val SETTINGS_IMPORT = "settings-import"

internal fun settingsColorRowTag(field: SeedField): String = "settings-color-${field.name.lowercase(Locale.US)}"

internal fun settingsHexTag(field: SeedField): String = "settings-hex-${field.name.lowercase(Locale.US)}"

internal fun settingsSwatchTag(field: SeedField): String = "settings-swatch-${field.name.lowercase(Locale.US)}"

internal fun settingsPreviewPrimaryTag(hex: String): String = "settings-preview-primary-$hex"

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeSelected: (ThemeMode) -> Unit,
    onSelectDefaultPalette: () -> Unit,
    onSelectCustomPalette: () -> Unit,
    onEditingDarkChange: (Boolean) -> Unit,
    onDraftFieldChange: (SeedField, String) -> Unit,
    onDraftColorPicked: (SeedField, Int) -> Unit,
    onGenerateDark: () -> Unit,
    onSaveDraft: () -> Unit,
    onCancelDraft: () -> Unit,
    onResetCustomDraft: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onConfirmImportExplanation: () -> Unit,
    onDismissImportExplanation: () -> Unit,
    onDismissImportErrors: () -> Unit,
    onMessageConsumed: () -> Unit,
    onBack: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    var lastSaveAt by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    val saveOnce: () -> Unit = {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSaveAt >= 700L) {
            lastSaveAt = now
            onSaveDraft()
        }
    }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    val customEditorVisible = state.draft != null && state.appearance.paletteType == PaletteType.Custom
    val saveEnabled = state.draft != null &&
        SeedField.entries.all { PaletteDraftLogic.fieldError(state.draft.valueFor(it)) == null }
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SETTINGS_ROOT),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (customEditorVisible) {
                SettingsSaveBar(
                    saveEnabled = saveEnabled,
                    onSave = saveOnce,
                    onCancel = onCancelDraft
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            SettingsHeader(onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .then(
                        if (customEditorVisible) {
                            Modifier
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    )
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(
                        top = AppDimens.headerStackGap,
                        bottom = if (customEditorVisible) AppDimens.itemGap else AppDimens.scrollEndPadding
                    )
            ) {
                AppearanceSection(
                    state = state,
                    customEditorVisible = customEditorVisible,
                    onThemeSelected = onThemeSelected,
                    onSelectDefaultPalette = onSelectDefaultPalette,
                    onSelectCustomPalette = onSelectCustomPalette,
                    onEditingDarkChange = onEditingDarkChange,
                    onDraftFieldChange = onDraftFieldChange,
                    onDraftColorPicked = onDraftColorPicked,
                    onGenerateDark = onGenerateDark,
                    onResetCustomDraft = onResetCustomDraft
                )
                CompactEditorDivider()
                DataSection(
                    onExportClick = onExportClick,
                    onImportClick = onImportClick
                )
                CompactEditorDivider()
                CompactEditorSection(title = settingsKicker(stringResource(R.string.privacy_title))) {
                    Text(
                        text = stringResource(R.string.privacy_body),
                        style = AppTypeTokens.statSecondary,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    if (state.showImportExplanation) {
        AlertDialog(
            onDismissRequest = onDismissImportExplanation,
            title = { Text(stringResource(R.string.import_explain_title)) },
            text = { Text(stringResource(R.string.import_explain_body)) },
            confirmButton = {
                TextButton(onClick = onConfirmImportExplanation) {
                    Text(stringResource(R.string.action_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissImportExplanation) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (state.importErrors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissImportErrors,
            title = { Text(stringResource(R.string.import_error_title)) },
            text = {
                Column {
                    state.importErrors.take(8).forEach { error ->
                        Text(
                            text = stringResource(
                                R.string.import_error_line,
                                error.lineNumber,
                                csvErrorText(error)
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissImportErrors) {
                    Text(stringResource(R.string.action_ok))
                }
            }
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
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
            text = stringResource(R.string.settings_title),
            style = AppTypeTokens.sectionTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SettingsSaveBar(
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
            .testTag(SETTINGS_SAVE_BAR)
    ) {
        HorizontalDivider(
            thickness = AppDimens.strokeThin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Row(
            modifier = Modifier
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
private fun AppearanceSection(
    state: SettingsUiState,
    customEditorVisible: Boolean,
    onThemeSelected: (ThemeMode) -> Unit,
    onSelectDefaultPalette: () -> Unit,
    onSelectCustomPalette: () -> Unit,
    onEditingDarkChange: (Boolean) -> Unit,
    onDraftFieldChange: (SeedField, String) -> Unit,
    onDraftColorPicked: (SeedField, Int) -> Unit,
    onGenerateDark: () -> Unit,
    onResetCustomDraft: () -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.theme_title))) {
        Column(modifier = Modifier.selectableGroup()) {
            ChoiceRow(
                label = stringResource(R.string.theme_system),
                selected = state.appearance.mode == ThemeMode.System,
                testTag = SETTINGS_THEME_SYSTEM,
                onClick = { onThemeSelected(ThemeMode.System) }
            )
            ChoiceRow(
                label = stringResource(R.string.theme_light),
                selected = state.appearance.mode == ThemeMode.Light,
                testTag = SETTINGS_THEME_LIGHT,
                onClick = { onThemeSelected(ThemeMode.Light) }
            )
            ChoiceRow(
                label = stringResource(R.string.theme_dark),
                selected = state.appearance.mode == ThemeMode.Dark,
                testTag = SETTINGS_THEME_DARK,
                onClick = { onThemeSelected(ThemeMode.Dark) }
            )
        }
    }
    CompactEditorDivider()
    CompactEditorSection(title = settingsKicker(stringResource(R.string.palette_title))) {
        val customSelected = state.appearance.paletteType == PaletteType.Custom
        Column(modifier = Modifier.selectableGroup()) {
            ChoiceRow(
                label = stringResource(R.string.palette_default),
                selected = !customSelected,
                testTag = SETTINGS_PALETTE_DEFAULT,
                onClick = onSelectDefaultPalette
            )
            ChoiceRow(
                label = stringResource(R.string.palette_custom),
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
        SeedField.entries.forEach { field ->
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
    onClick: () -> Unit
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
        Text(
            text = label,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
private fun DataSection(
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    CompactEditorSection(title = settingsKicker(stringResource(R.string.data_title))) {
        DataActionRow(
            icon = Icons.Outlined.FileUpload,
            title = stringResource(R.string.action_export_weight),
            subtitle = stringResource(R.string.action_export_weight_subtitle),
            testTag = SETTINGS_EXPORT,
            onClick = onExportClick
        )
        DataActionRow(
            icon = Icons.Outlined.FileDownload,
            title = stringResource(R.string.action_import_weight),
            subtitle = stringResource(R.string.action_import_weight_subtitle),
            testTag = SETTINGS_IMPORT,
            onClick = onImportClick
        )
    }
}

@Composable
private fun DataActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(onClick = onClick)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(AppDimens.itemGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = AppTypeTokens.statCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
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
private fun csvErrorText(error: WeightCsv.RowError): String {
    val reason = when (error.reason) {
        WeightCsv.CsvErrorReason.MissingHeader -> stringResource(R.string.csv_error_missing_header)
        WeightCsv.CsvErrorReason.InvalidHeader -> stringResource(R.string.csv_error_invalid_header)
        WeightCsv.CsvErrorReason.WrongColumnCount -> stringResource(R.string.csv_error_columns)
        WeightCsv.CsvErrorReason.InvalidDate -> stringResource(R.string.csv_error_date)
        WeightCsv.CsvErrorReason.FutureDate -> stringResource(R.string.csv_error_future)
        WeightCsv.CsvErrorReason.InvalidWeight -> stringResource(R.string.csv_error_weight)
        WeightCsv.CsvErrorReason.DuplicateDateInFile -> stringResource(R.string.csv_error_duplicate)
    }
    return if (error.detail.isNullOrBlank()) reason else "$reason (${error.detail})"
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
    return text.uppercase(Locale.forLanguageTag("hu-HU"))
}

private fun PaletteDraft.valueFor(field: SeedField): String {
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
            state = SettingsUiState(appearance = AppearanceSettings.Default),
            onThemeSelected = {},
            onSelectDefaultPalette = {},
            onSelectCustomPalette = {},
            onEditingDarkChange = {},
            onDraftFieldChange = { _, _ -> },
            onDraftColorPicked = { _, _ -> },
            onGenerateDark = {},
            onSaveDraft = {},
            onCancelDraft = {},
            onResetCustomDraft = {},
            onExportClick = {},
            onImportClick = {},
            onConfirmImportExplanation = {},
            onDismissImportExplanation = {},
            onDismissImportErrors = {},
            onMessageConsumed = {},
            onBack = {}
        )
    }
}
