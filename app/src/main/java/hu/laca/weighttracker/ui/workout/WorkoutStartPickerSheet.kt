package hu.laca.weighttracker.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.workout.TemplateListItem
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppTypeTokens

internal const val START_PICKER_SHEET = "workout-start-picker-sheet"
internal const val START_PICKER_MANAGE = "workout-start-picker-manage"
internal const val START_PICKER_CREATE = "workout-start-picker-create"
internal const val START_PICKER_EMPTY = "workout-start-picker-empty"

internal fun startPickerRowTag(id: Long): String = "workout-start-picker-row-$id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutStartPickerSheet(
    templates: List<TemplateListItem>,
    starting: Boolean,
    onDismiss: () -> Unit,
    onSelectTemplate: (TemplateListItem) -> Unit,
    onManageTemplates: () -> Unit,
    onCreateTemplate: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    var selected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        keyboard?.hide()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(START_PICKER_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimens.screenPadding)
                .padding(bottom = AppDimens.itemGap)
        ) {
            Text(
                text = stringResource(R.string.start_workout_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(AppDimens.itemGap))
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_start_picker_empty),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(START_PICKER_EMPTY)
                )
                Spacer(Modifier.height(AppDimens.headerStackGap))
                TextButton(
                    onClick = {
                        if (selected) {
                            return@TextButton
                        }
                        selected = true
                        onCreateTemplate()
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(START_PICKER_CREATE)
                ) {
                    Text(stringResource(R.string.action_create_template))
                }
            } else {
                templates.forEachIndexed { index, item ->
                    PickerTemplateRow(
                        item = item,
                        enabled = !starting,
                        onClick = {
                            if (starting || selected) {
                                return@PickerTemplateRow
                            }
                            selected = true
                            onSelectTemplate(item)
                        }
                    )
                    if (index != templates.lastIndex) {
                        HorizontalDivider(
                            thickness = AppDimens.strokeThin,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
                Spacer(Modifier.height(AppDimens.itemGap))
                TextButton(
                    onClick = {
                        if (selected) {
                            return@TextButton
                        }
                        selected = true
                        onManageTemplates()
                    },
                    modifier = Modifier
                        .align(Alignment.Start)
                        .defaultMinSize(minHeight = AppDimens.minTouch)
                        .testTag(START_PICKER_MANAGE)
                ) {
                    Text(
                        text = stringResource(R.string.action_manage_templates),
                        style = AppTypeTokens.statCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerTemplateRow(
    item: TemplateListItem,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(startPickerRowTag(item.template.id))
            .semantics { role = Role.Button }
            .padding(vertical = AppDimens.headerStackGap)
    ) {
        Text(
            text = item.template.name,
            style = AppTypeTokens.sectionTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Text(
            text = stringResource(
                R.string.template_row_meta,
                item.exerciseCount,
                item.setCount
            ),
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
