package hu.laca.weighttracker.ui.history

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.model.MeasurementListItem
import hu.laca.weighttracker.domain.model.WeightMeasurement
import hu.laca.weighttracker.ui.components.MeasurementEditorSheet
import hu.laca.weighttracker.ui.components.MeasurementRow
import hu.laca.weighttracker.ui.components.SettingsAction
import hu.laca.weighttracker.ui.components.UserMessageEffect
import hu.laca.weighttracker.ui.theme.WeightTrackerTheme
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    today: LocalDate,
    onAdd: () -> Unit,
    onEdit: (LocalDate) -> Unit,
    onDelete: (LocalDate) -> Unit,
    onEditorDateChange: (LocalDate) -> Unit,
    onEditorWeightChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismissEditor: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDeleteDismiss: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onMessageConsumed: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    UserMessageEffect(state.userMessage, snackbarHostState, onMessageConsumed)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                actions = { SettingsAction(onOpenSettings) }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.action_add_measurement)
                )
            }
        }
    ) { innerPadding ->
        if (state.isEmpty) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = stringResource(R.string.history_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                items(state.items, key = { it.measurement.id }) { item ->
                    MeasurementRow(
                        item = item,
                        onEdit = { onEdit(item.measurement.date) },
                        onDelete = { onDelete(item.measurement.date) }
                    )
                }
            }
        }
    }
    state.editor?.let { editor ->
        MeasurementEditorSheet(
            state = editor,
            today = today,
            onDateChange = onEditorDateChange,
            onWeightChange = onEditorWeightChange,
            onSave = onSave,
            onDismiss = onDismissEditor,
            onDeleteRequest = onDeleteRequest,
            onDeleteDismiss = onDeleteDismiss,
            onDeleteConfirm = onDeleteConfirm
        )
    }
}

@Preview(showBackground = true, name = "History light")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "History dark")
@Composable
private fun HistoryPreview() {
    val date = LocalDate.of(2026, 3, 11)
    WeightTrackerTheme {
        HistoryScreen(
            state = HistoryUiState(
                items = listOf(
                    MeasurementListItem(
                        measurement = WeightMeasurement(1, date, 82.4, 0, 0),
                        differenceFromPreviousKg = 0.3
                    )
                ),
                isEmpty = false
            ),
            today = date,
            onAdd = {},
            onEdit = {},
            onDelete = {},
            onEditorDateChange = {},
            onEditorWeightChange = {},
            onSave = {},
            onDismissEditor = {},
            onDeleteRequest = {},
            onDeleteDismiss = {},
            onDeleteConfirm = {},
            onMessageConsumed = {},
            onOpenSettings = {}
        )
    }
}
