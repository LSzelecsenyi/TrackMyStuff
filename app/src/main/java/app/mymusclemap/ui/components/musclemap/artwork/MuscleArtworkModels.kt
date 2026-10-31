/*
 * Logical muscle regions are separate from the SVG path ids that draw them.
 * Male and female artwork can use different path ids for the same MuscleGroup.
 */
package app.mymusclemap.ui.components.musclemap.artwork

import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel

enum class MuscleMapView {
    FRONT,
    BACK
}

enum class AnatomicalSide {
    LEFT,
    RIGHT,
    CENTRAL
}

internal data class MuscleArtworkPath(
    val id: String,
    val inkscapeLabel: String,
    val view: MuscleMapView,
    val pathData: String
)

data class MuscleArtworkRegion(
    val id: String,
    val view: MuscleMapView,
    val side: AnatomicalSide,
    val muscleGroup: MuscleGroup?,
    val pathData: String
)

data class MuscleMapFigureArtwork(
    val bodyModel: MuscleMapBodyModel,
    val viewWidth: Float,
    val viewHeight: Float,
    val splitX: Float,
    val anatomyAsset: String,
    val regions: List<MuscleArtworkRegion>
)
