package hu.laca.weighttracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import hu.laca.weighttracker.domain.exercise.ExerciseNaming
import hu.laca.weighttracker.ui.theme.AppDimens
import hu.laca.weighttracker.ui.theme.AppShapeTokens
import hu.laca.weighttracker.ui.theme.AppTypeTokens

@Composable
fun CompactEditorSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = AppTypeTokens.sectionKicker,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppDimens.headerStackGap))
        content()
    }
}

@Composable
fun CompactEditorDivider(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
        HorizontalDivider(
            thickness = AppDimens.strokeThin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Spacer(Modifier.height(AppDimens.sectionDividerSpace))
    }
}

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    sentenceCapitalize: Boolean = false,
    testTag: String? = null
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    LaunchedEffect(value) {
        if (value != field.text) {
            val cursor = field.selection.start.coerceIn(0, value.length)
            field = TextFieldValue(text = value, selection = TextRange(cursor))
        }
    }
    val lineColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val captionColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    BasicTextField(
        value = field,
        onValueChange = { incoming ->
            val nextText = if (sentenceCapitalize) {
                ExerciseNaming.capitalizeFirstLetter(incoming.text)
            } else {
                incoming.text
            }
            val selection = TextRange(
                ExerciseNaming.mapCursor(incoming.text, nextText, incoming.selection.start),
                ExerciseNaming.mapCursor(incoming.text, nextText, incoming.selection.end)
            )
            field = incoming.copy(
                text = nextText,
                selection = selection,
                composition = incoming.composition.takeIf { nextText.length == incoming.text.length }
            )
            onValueChange(nextText)
        },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .semantics { contentDescription = label }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        textStyle = AppTypeTokens.statValue.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = singleLine,
        minLines = minLines,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        visualTransformation = VisualTransformation.None,
        decorationBox = { inner ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = AppTypeTokens.statCaption,
                    color = captionColor
                )
                Spacer(Modifier.height(AppDimens.statSecondaryGap))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = if (singleLine) 28.dp else 64.dp),
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
                if (!supportingText.isNullOrBlank()) {
                    Spacer(Modifier.height(AppDimens.statSecondaryGap))
                    Text(
                        text = supportingText,
                        style = AppTypeTokens.statSecondary,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    )
}

@Composable
fun <T> CompactDropdown(
    label: String,
    selected: T?,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    testTag: String,
    menuTestTag: String = "$testTag-menu",
    anchorTestTag: String = "$testTag-anchor"
) {
    var expanded by remember { mutableStateOf(false) }
    val value = selected?.let(optionLabel) ?: placeholder
    val captionColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = captionColor
        )
        Spacer(Modifier.height(AppDimens.statSecondaryGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AppDimens.minTouch)
                .clickable(enabled = enabled, role = Role.DropdownList) {
                    expanded = true
                }
                .semantics { contentDescription = "$label: $value" }
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                style = AppTypeTokens.statValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopEnd)
                    .semantics(mergeDescendants = false) {}
                    .testTag(anchorTestTag)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.testTag(menuTestTag)
                ) {
                    options.forEach { option ->
                        val optionText = optionLabel(option)
                        val isSelected = option == selected
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = optionText,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                onSelected(option)
                                expanded = false
                            },
                            modifier = Modifier.semantics {
                                this.selected = isSelected
                                contentDescription = optionText
                            }
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.strokeThin)
                .background(
                    if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                )
        )
        if (!supportingText.isNullOrBlank()) {
            Spacer(Modifier.height(AppDimens.statSecondaryGap))
            Text(
                text = supportingText,
                style = AppTypeTokens.statSecondary,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun CompactChoiceChip(
    label: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    removeDescription: String = label
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = AppDimens.minTouch)
            .clip(AppShapeTokens.chip)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onRemove)
            .padding(horizontal = 10.dp)
            .semantics {
                role = Role.Button
                contentDescription = removeDescription
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = AppTypeTokens.statCaption,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}
