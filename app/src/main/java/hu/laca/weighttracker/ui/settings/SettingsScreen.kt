package hu.laca.weighttracker.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import hu.laca.weighttracker.ui.components.PaletteSwatch
import hu.laca.weighttracker.ui.components.SectionHeader
import hu.laca.weighttracker.ui.components.SegmentedControl
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
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
    onMessageConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(horizontal = AppDimens.screenPadding)
                    .padding(top = 12.dp, bottom = AppDimens.scrollEndPadding)
            ) {
            AppearanceSection(
                state = state,
                onThemeSelected = onThemeSelected,
                onSelectDefaultPalette = onSelectDefaultPalette,
                onSelectCustomPalette = onSelectCustomPalette,
                onEditingDarkChange = onEditingDarkChange,
                onDraftFieldChange = onDraftFieldChange,
                onDraftColorPicked = onDraftColorPicked,
                onGenerateDark = onGenerateDark,
                onSaveDraft = onSaveDraft,
                onCancelDraft = onCancelDraft,
                onResetCustomDraft = onResetCustomDraft
            )
            Spacer(Modifier.height(28.dp))
            SectionHeader(title = stringResource(R.string.backup_title))
            Text(
                text = stringResource(R.string.backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onExportClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_export))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_import))
            }
            Spacer(Modifier.height(28.dp))
            SectionHeader(title = stringResource(R.string.privacy_title))
            Text(
                text = stringResource(R.string.privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
private fun AppearanceSection(
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
    onResetCustomDraft: () -> Unit
) {
    SectionHeader(title = stringResource(R.string.theme_title))
    Column(modifier = Modifier.selectableGroup()) {
        ThemeOption(
            label = stringResource(R.string.theme_system),
            selected = state.appearance.mode == ThemeMode.System,
            onClick = { onThemeSelected(ThemeMode.System) }
        )
        ThemeOption(
            label = stringResource(R.string.theme_light),
            selected = state.appearance.mode == ThemeMode.Light,
            onClick = { onThemeSelected(ThemeMode.Light) }
        )
        ThemeOption(
            label = stringResource(R.string.theme_dark),
            selected = state.appearance.mode == ThemeMode.Dark,
            onClick = { onThemeSelected(ThemeMode.Dark) }
        )
    }
    Spacer(Modifier.height(20.dp))
    Text(
        text = stringResource(R.string.palette_title),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(8.dp))
    val customSelected = state.appearance.paletteType == PaletteType.Custom
    Column(modifier = Modifier.selectableGroup()) {
        ThemeOption(
            label = stringResource(R.string.palette_default),
            selected = !customSelected,
            onClick = onSelectDefaultPalette
        )
        ThemeOption(
            label = stringResource(R.string.palette_custom),
            selected = customSelected,
            onClick = onSelectCustomPalette
        )
    }
    val previewDraft = state.draft
    val previewDark = previewDraft?.previewIsDark ?: when (state.appearance.mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val previewSeeds = previewDraft?.activePreview ?: state.appearance.activeSeeds(previewDark)
    Spacer(Modifier.height(16.dp))
    ThemePreviewCard(seeds = previewSeeds, darkTheme = previewDark)
    if (state.draft != null) {
        Spacer(Modifier.height(16.dp))
        CustomPaletteEditor(
            draft = state.draft,
            saveError = state.saveError,
            onEditingDarkChange = onEditingDarkChange,
            onDraftFieldChange = onDraftFieldChange,
            onDraftColorPicked = onDraftColorPicked,
            onGenerateDark = onGenerateDark,
            onSaveDraft = onSaveDraft,
            onCancelDraft = onCancelDraft,
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
    onSaveDraft: () -> Unit,
    onCancelDraft: () -> Unit,
    onResetCustomDraft: () -> Unit
) {
    var pickerField by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    SegmentedControl(
        options = listOf(
            stringResource(R.string.theme_light),
            stringResource(R.string.theme_dark)
        ),
        selectedIndex = if (draft.editingDark) 1 else 0,
        onSelected = { onEditingDarkChange(it == 1) }
    )
    Spacer(Modifier.height(12.dp))
    SeedField.entries.forEach { field ->
        ColorFieldRow(
            field = field,
            value = draft.valueFor(field),
            previewRgb = draft.previewRgb(field),
            onValueChange = { onDraftFieldChange(field, it) },
            onSwatchClick = { pickerField = field.name }
        )
        Spacer(Modifier.height(8.dp))
    }
    saveError?.let { error ->
        Text(
            text = saveErrorText(error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
    OutlinedButton(
        onClick = onGenerateDark,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.action_generate_dark))
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onSaveDraft,
        modifier = Modifier.fillMaxWidth(),
        enabled = SeedField.entries.all { PaletteDraftLogic.fieldError(draft.valueFor(it)) == null }
    ) {
        Text(stringResource(R.string.action_save))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = onCancelDraft,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.action_cancel))
    }
    TextButton(onClick = { confirmReset = true }) {
        Text(stringResource(R.string.action_reset_custom_colors))
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
private fun ColorFieldRow(
    field: SeedField,
    value: String,
    previewRgb: Int,
    onValueChange: (String) -> Unit,
    onSwatchClick: () -> Unit
) {
    val error = PaletteDraftLogic.fieldError(value)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(field.labelRes()),
            style = MaterialTheme.typography.labelLarge
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaletteSwatch(
                color = Color(previewRgb),
                contentDescription = stringResource(R.string.color_swatch_description, stringResource(field.labelRes())),
                onClick = onSwatchClick
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                singleLine = true,
                isError = error != null,
                supportingText = error?.let {
                    {
                        Text(stringResource(R.string.error_color_hex))
                    }
                }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp)
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
            onMessageConsumed = {}
        )
    }
}
