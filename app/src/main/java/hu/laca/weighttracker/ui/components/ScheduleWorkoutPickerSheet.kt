package hu.laca.weighttracker.ui.components

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
import androidx.compose.ui.Modifier
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
import hu.laca.weighttracker.ui.workout.CompactSheetHandle
import java.time.LocalDate

internal const val SCHEDULE_PICKER_SHEET = "schedule-picker-sheet"
internal const val SCHEDULE_PICKER_EMPTY = "schedule-picker-empty"
internal const val SCHEDULE_PICKER_CREATE = "schedule-picker-create"
internal const val SCHEDULE_PICKER_ERROR = "schedule-picker-error"

internal fun schedulePickerRowTag(id: Long): String = "schedule-picker-row-$id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleWorkoutPickerSheet(
    date: LocalDate,
    templates: List<TemplateListItem>,
    busy: Boolean,
    errorText: String?,
    onDismiss: () -> Unit,
    onSelectTemplate: (TemplateListItem) -> Unit,
    onCreateTemplate: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (!busy) {
            selected = false
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        dragHandle = { CompactSheetHandle() },
        modifier = Modifier.testTag(SCHEDULE_PICKER_SHEET)
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
                text = stringResource(R.string.schedule_picker_title),
                style = AppTypeTokens.sectionTitle,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = UiFormatters.longDateWithWeekday(date),
                style = AppTypeTokens.sectionSubtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (errorText != null) {
                Spacer(Modifier.height(AppDimens.headerStackGap))
                Text(
                    text = errorText,
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(SCHEDULE_PICKER_ERROR)
                )
            }
            Spacer(Modifier.height(AppDimens.itemGap))
            if (templates.isEmpty()) {
                Text(
                    text = stringResource(R.string.schedule_picker_empty),
                    style = AppTypeTokens.statSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(SCHEDULE_PICKER_EMPTY)
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
                        .testTag(SCHEDULE_PICKER_CREATE)
                ) {
                    Text(stringResource(R.string.action_create_template))
                }
            } else {
                templates.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = AppDimens.minTouch)
                            .clickable(enabled = !busy && !selected) {
                                selected = true
                                onSelectTemplate(item)
                            }
                            .testTag(schedulePickerRowTag(item.template.id))
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
                    if (index != templates.lastIndex) {
                        HorizontalDivider(
                            thickness = AppDimens.strokeThin,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
