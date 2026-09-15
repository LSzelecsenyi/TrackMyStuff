package hu.laca.weighttracker.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import hu.laca.weighttracker.R
import hu.laca.weighttracker.domain.theme.ColorPickerLogic
import hu.laca.weighttracker.domain.theme.ColorScience
import hu.laca.weighttracker.ui.theme.AppDimens

@Composable
fun ColorPickerDialog(
    title: String,
    color: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var state by remember { mutableStateOf(ColorPickerLogic.open(color)) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val confirmIfValid = {
        val selected = ColorPickerLogic.confirm(state)
        if (selected != null) {
            keyboardController?.hide()
            onConfirm(selected)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text(text = title, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ColorPreviewRow(rgb = state.previewRgb)
                        Spacer(Modifier.height(16.dp))
                        SaturationValueField(
                            hsv = state.hsv,
                            onChange = { saturation, value ->
                                state = ColorPickerLogic.applySaturationValue(state, saturation, value)
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.color_hue),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        HueSpectrumBar(
                            hue = state.hsv.hue,
                            onHueChange = { hue ->
                                state = ColorPickerLogic.applyHue(state, hue)
                            }
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = state.hexDraft,
                            onValueChange = { state = ColorPickerLogic.applyHex(state, it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusEvent { focusState ->
                                    if (focusState.isFocused) {
                                        coroutineScope.launch { bringIntoViewRequester.bringIntoView() }
                                    }
                                },
                            label = { Text(stringResource(R.string.color_hex_code)) },
                            isError = !state.hexValid,
                            supportingText = {
                                if (!state.hexValid) {
                                    Text(stringResource(R.string.error_color_hex))
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Characters,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { confirmIfValid() }
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        QuickColorRow(
                            selectedRgb = state.previewRgb,
                            onSelect = { rgb ->
                                state = ColorPickerLogic.applySwatch(state, rgb)
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        TextButton(
                            onClick = confirmIfValid,
                            enabled = state.canConfirm
                        ) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPreviewRow(rgb: Int) {
    val description = stringResource(R.string.color_preview_description)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(rgb))
                .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .semantics { contentDescription = description }
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.color_preview_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SaturationValueField(
    hsv: ColorScience.Hsv,
    onChange: (Float, Float) -> Unit
) {
    val description = stringResource(R.string.color_sv_field_description)
    val hueColor = Color(ColorScience.fromHsv(ColorScience.Hsv(hsv.hue, 1f, 1f)))
    val onChangeUpdated = rememberUpdatedState(onChange)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(16.dp))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectContinuousOffset { offset ->
                    val (saturation, value) = ColorPickerLogic.svFromPointer(
                        x = offset.x,
                        y = offset.y,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )
                    onChangeUpdated.value(saturation, value)
                }
            }
    ) {
        drawRect(color = hueColor)
        drawRect(brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val indicator = Offset(
            x = hsv.saturation.coerceIn(0f, 1f) * size.width,
            y = (1f - hsv.value.coerceIn(0f, 1f)) * size.height
        )
        val radius = 10.dp.toPx()
        drawCircle(color = Color.White, radius = radius, center = indicator, style = Stroke(width = 3.dp.toPx()))
        drawCircle(color = Color.Black, radius = radius, center = indicator, style = Stroke(width = 1.5.dp.toPx()))
        drawCircle(color = Color(0x66FFFFFF), radius = 3.dp.toPx(), center = indicator)
    }
}

@Composable
private fun HueSpectrumBar(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    val description = stringResource(R.string.color_hue_bar_description)
    val onHueChangeUpdated = rememberUpdatedState(onHueChange)
    val spectrum = listOf(
        Color(0xFFFF0000),
        Color(0xFFFFFF00),
        Color(0xFF00FF00),
        Color(0xFF00FFFF),
        Color(0xFF0000FF),
        Color(0xFFFF00FF),
        Color(0xFFFF0000)
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectContinuousOffset { offset ->
                    onHueChangeUpdated.value(
                        ColorPickerLogic.hueFromPointer(
                            x = offset.x,
                            width = size.width.toFloat()
                        )
                    )
                }
            }
    ) {
        drawRect(brush = Brush.horizontalGradient(spectrum))
        val thumbX = ColorPickerLogic.hueThumbFraction(hue) * size.width
        val thumbRadius = 12.dp.toPx()
        val center = Offset(thumbX.coerceIn(thumbRadius, size.width - thumbRadius), size.height / 2f)
        drawCircle(color = Color.White, radius = thumbRadius, center = center)
        drawCircle(color = Color.Black, radius = thumbRadius, center = center, style = Stroke(width = 2.dp.toPx()))
        val inner = Color(ColorScience.fromHsv(ColorScience.Hsv(hue, 1f, 1f)))
        drawCircle(color = inner, radius = 6.dp.toPx(), center = center)
    }
}

@Composable
private fun QuickColorRow(
    selectedRgb: Int,
    onSelect: (Int) -> Unit
) {
    val labels = listOf(
        stringResource(R.string.color_swatch_blue),
        stringResource(R.string.color_swatch_cyan),
        stringResource(R.string.color_swatch_green),
        stringResource(R.string.color_swatch_orange),
        stringResource(R.string.color_swatch_coral),
        stringResource(R.string.color_swatch_purple),
        stringResource(R.string.color_swatch_grey)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ColorPickerLogic.quickSwatches.forEachIndexed { index, rgb ->
            val selected = rgb == selectedRgb
            Box(
                modifier = Modifier
                    .size(AppDimens.minTouch)
                    .clip(CircleShape)
                    .clickable { onSelect(rgb) }
                    .semantics {
                        contentDescription = labels[index]
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(rgb))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                )
            }
        }
    }
}

private suspend fun PointerInputScope.detectContinuousOffset(
    onOffset: (Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onOffset(down.position)
        drag(down.id) { change ->
            change.consume()
            onOffset(change.position)
        }
    }
}
