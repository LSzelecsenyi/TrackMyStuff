package app.mymusclemap.ui.units

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.mymusclemap.R
import app.mymusclemap.domain.units.DistanceWord
import app.mymusclemap.domain.units.UnitSystem

val LocalUnitSystem = compositionLocalOf { UnitSystem.METRIC }

@Composable
fun weightUnitLabel(): String {
    return stringResource(
        if (LocalUnitSystem.current == UnitSystem.IMPERIAL) R.string.unit_lb else R.string.unit_kg
    )
}

@Composable
fun weightFieldLabel(): String {
    return stringResource(
        if (LocalUnitSystem.current == UnitSystem.IMPERIAL) {
            R.string.field_weight_lb
        } else {
            R.string.field_weight_kg
        }
    )
}

fun DistanceWord.labelRes(): Int {
    return when (this) {
        DistanceWord.METERS -> R.string.set_copy_distance_m
        DistanceWord.KILOMETERS -> R.string.set_copy_distance_km
        DistanceWord.FEET -> R.string.set_copy_distance_ft
        DistanceWord.MILES -> R.string.set_copy_distance_mi
    }
}
