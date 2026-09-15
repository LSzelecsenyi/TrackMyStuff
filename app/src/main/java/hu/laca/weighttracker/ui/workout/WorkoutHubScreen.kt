package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.ui.components.SettingsAction
import hu.laca.weighttracker.ui.theme.AppDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHubScreen(
    state: WorkoutHubUiState,
    onOpenSettings: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_workout)) },
                actions = { SettingsAction(onOpenSettings) }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(top = 8.dp, bottom = AppDimens.scrollEndPadding)
        ) {
            Text(
                text = stringResource(R.string.workout_hub_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppDimens.sectionGap))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Column(modifier = Modifier.padding(AppDimens.heroPadding)) {
                    Text(
                        text = stringResource(R.string.exercises_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.workout_catalog_subtitle),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.workout_catalog_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(12.dp))
                    if (state.isEmpty) {
                        Text(
                            text = stringResource(R.string.workout_catalog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else if (!state.loading) {
                        Text(
                            text = if (state.archivedCount > 0) {
                                stringResource(
                                    R.string.workout_catalog_counts,
                                    state.activeCount,
                                    state.archivedCount
                                )
                            } else {
                                stringResource(R.string.workout_catalog_active_count, state.activeCount)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onOpenCatalog,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.action_manage_exercises))
                    }
                }
            }
        }
    }
}
