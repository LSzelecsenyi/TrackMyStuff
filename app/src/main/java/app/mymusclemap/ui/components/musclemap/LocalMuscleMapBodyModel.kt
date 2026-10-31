package app.mymusclemap.ui.components.musclemap

import androidx.compose.runtime.staticCompositionLocalOf
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel

val LocalMuscleMapBodyModel = staticCompositionLocalOf { MuscleMapBodyModel.MALE }
