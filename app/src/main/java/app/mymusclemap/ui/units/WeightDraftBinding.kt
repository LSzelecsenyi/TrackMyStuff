package app.mymusclemap.ui.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.mymusclemap.R
import app.mymusclemap.domain.units.DistanceDisplay
import app.mymusclemap.domain.units.MeasurementInput
import app.mymusclemap.domain.units.UnitSystem
import app.mymusclemap.domain.units.WorkoutDistanceInput
import app.mymusclemap.domain.units.WorkoutWeightInput
import app.mymusclemap.domain.workout.DistanceUnit
import app.mymusclemap.ui.theme.AppDimens
import app.mymusclemap.ui.theme.StrictBrand

data class WeightDraftBinding(
    val text: String,
    val onText: (String) -> Unit,
    val step: (direction: Int) -> Unit
)

/**
 * Shows canonical kilogram text in the active unit system. A preference change
 * reformats the field and does not call [onCanonicalText]. A keystroke writes
 * only when the canonical kilograms actually change.
 */
@Composable
fun rememberWeightDraftBinding(
    fieldKey: Any,
    canonicalText: String,
    onCanonicalText: (String) -> Unit,
    enabled: Boolean = true
): WeightDraftBinding {
    val system = LocalUnitSystem.current
    val increment = LocalWeightAdjustment.current.stepFor(system)
    var editing by remember(fieldKey) { mutableStateOf<String?>(null) }
    var editingSystem by remember(fieldKey) { mutableStateOf(system) }
    LaunchedEffect(system, fieldKey) {
        val typed = editing
        if (typed != null && editingSystem != system) {
            editing = WorkoutWeightInput.reexpress(typed, editingSystem, system)
            editingSystem = system
        } else {
            editingSystem = system
        }
    }
    val shown = editing ?: WorkoutWeightInput.displayOf(canonicalText, system)
    return WeightDraftBinding(
        text = shown,
        onText = { raw ->
            if (!enabled) return@WeightDraftBinding
            editing = raw
            editingSystem = system
            val next = WorkoutWeightInput.commit(canonicalText, raw, system)
            if (next != canonicalText) onCanonicalText(next)
        },
        step = { direction ->
            if (!enabled) return@WeightDraftBinding
            val stepped = WorkoutWeightInput.step(shown, system, direction, increment)
            val next = WorkoutWeightInput.commit(canonicalText, stepped, system)
            if (MeasurementInput.sameText(stepped, WorkoutWeightInput.displayOf(next, system))) {
                editing = null
            } else {
                editing = stepped
                editingSystem = system
            }
            if (next != canonicalText) onCanonicalText(next)
        }
    )
}

@Composable
fun WeightStepButtons(
    text: String,
    onStep: (direction: Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    minusTag: String = "weight-step-minus",
    plusTag: String = "weight-step-plus",
    minusCircleTag: String = "weight-step-minus-circle",
    plusCircleTag: String = "weight-step-plus-circle",
    minusTraversalIndex: Float? = null,
    plusTraversalIndex: Float? = null
) {
    val system = LocalUnitSystem.current
    val increment = LocalWeightAdjustment.current.stepFor(system)
    val decreaseEnabled = enabled &&
        WorkoutWeightInput.step(text, system, -1, increment) != text
    val increaseEnabled = enabled &&
        WorkoutWeightInput.step(text, system, 1, increment) != text
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.itemGap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedQuantityStepButton(
            symbol = "−",
            enabled = decreaseEnabled,
            accent = StrictBrand.lime,
            contentDescription = stringResource(R.string.action_decrease_weight_a11y),
            testTag = minusTag,
            circleTag = minusCircleTag,
            traversalIndex = minusTraversalIndex,
            onClick = { onStep(-1) }
        )
        OutlinedQuantityStepButton(
            symbol = "+",
            enabled = increaseEnabled,
            accent = StrictBrand.lime,
            contentDescription = stringResource(R.string.action_increase_weight_a11y),
            testTag = plusTag,
            circleTag = plusCircleTag,
            traversalIndex = plusTraversalIndex,
            onClick = { onStep(1) }
        )
    }
}

fun weightUnitSymbol(system: UnitSystem): String = if (system == UnitSystem.IMPERIAL) "lb" else "kg"

data class DistanceDraftBinding(
    val text: String,
    val imperial: Boolean,
    val selectedIndex: Int,
    val onText: (String) -> Unit,
    val onUnitSelected: (Int) -> Unit
)

/**
 * Metric distance keeps the draft text and the meters/kilometers choice.
 * Imperial shows feet or miles and writes meters only after the number changes.
 */
@Composable
fun rememberDistanceDraftBinding(
    fieldKey: Any,
    canonicalText: String,
    canonicalUnit: DistanceUnit,
    onCanonicalText: (String) -> Unit,
    onCanonicalUnit: (DistanceUnit) -> Unit
): DistanceDraftBinding {
    val system = LocalUnitSystem.current
    if (system == UnitSystem.METRIC) {
        return DistanceDraftBinding(
            text = canonicalText,
            imperial = false,
            selectedIndex = if (canonicalUnit == DistanceUnit.KILOMETERS) 1 else 0,
            onText = onCanonicalText,
            onUnitSelected = { index ->
                onCanonicalUnit(if (index == 1) DistanceUnit.KILOMETERS else DistanceUnit.METERS)
            }
        )
    }
    var display by remember(fieldKey) {
        mutableStateOf(
            if (canonicalUnit == DistanceUnit.KILOMETERS) DistanceDisplay.MILES else DistanceDisplay.FEET
        )
    }
    var editing by remember(fieldKey) { mutableStateOf<String?>(null) }
    val meters = WorkoutDistanceInput.meters(canonicalText, canonicalUnit)
    val shown = editing ?: meters?.let { WorkoutDistanceInput.displayOf(it, display) } ?: canonicalText
    return DistanceDraftBinding(
        text = shown,
        imperial = true,
        selectedIndex = if (display == DistanceDisplay.MILES) 1 else 0,
        onText = { raw ->
            editing = raw
            val committed = WorkoutDistanceInput.commitMeters(meters, raw, display)
            if (committed != null && committed != canonicalText) {
                onCanonicalText(committed)
                if (canonicalUnit != DistanceUnit.METERS) {
                    onCanonicalUnit(DistanceUnit.METERS)
                }
            }
        },
        onUnitSelected = { index ->
            display = if (index == 1) DistanceDisplay.MILES else DistanceDisplay.FEET
            editing = null
        }
    )
}
