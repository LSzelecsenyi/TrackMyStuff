package hu.laca.weighttracker.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import hu.laca.weighttracker.R
import hu.laca.weighttracker.data.preferences.ThemePreference
import hu.laca.weighttracker.domain.csv.WeightCsv
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onConfirmImportExplanation: () -> Unit,
    onDismissImportExplanation: () -> Unit,
    onDismissImportErrors: () -> Unit,
    onMessageConsumed: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.theme_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Column(modifier = Modifier.selectableGroup()) {
                ThemeOption(
                    label = stringResource(R.string.theme_system),
                    selected = state.theme == ThemePreference.System,
                    onClick = { onThemeSelected(ThemePreference.System) }
                )
                ThemeOption(
                    label = stringResource(R.string.theme_light),
                    selected = state.theme == ThemePreference.Light,
                    onClick = { onThemeSelected(ThemePreference.Light) }
                )
                ThemeOption(
                    label = stringResource(R.string.theme_dark),
                    selected = state.theme == ThemePreference.Dark,
                    onClick = { onThemeSelected(ThemePreference.Dark) }
                )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
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
            Text(
                text = stringResource(R.string.privacy_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

@Preview(showBackground = true, name = "Settings light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Settings dark")
@Composable
private fun SettingsPreview() {
    WeightTrackerTheme {
        SettingsScreen(
            state = SettingsUiState(),
            onThemeSelected = {},
            onExportClick = {},
            onImportClick = {},
            onConfirmImportExplanation = {},
            onDismissImportExplanation = {},
            onDismissImportErrors = {},
            onMessageConsumed = {}
        )
    }
}
