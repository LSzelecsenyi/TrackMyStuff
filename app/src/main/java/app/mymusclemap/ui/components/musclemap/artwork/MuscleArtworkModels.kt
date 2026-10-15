/*
 * Logical muscle regions are separate from the SVG path ids that draw them.
 * Male and female artwork can use different path ids for the same MuscleGroup.
 */
package app.mymusclemap.ui.components.musclemap.artwork

import app.mymusclemap.domain.exercise.MuscleGroup

enum class MuscleMapSex {
    MALE,
    FEMALE
}

enum class MuscleMapView {
    FRONT,
    BACK
}

enum class AnatomicalSide {
    LEFT,
    RIGHT,
    CENTRAL
}

data class MuscleArtworkRegion(
    val id: String,
    val view: MuscleMapView,
    val side: AnatomicalSide,
    val muscleGroup: MuscleGroup?,
    val pathData: String
)

data class MuscleMapFigureArtwork(
    val sex: MuscleMapSex,
    val viewWidth: Float,
    val viewHeight: Float,
    val splitX: Float,
    val anatomyAsset: String,
    val regions: List<MuscleArtworkRegion>
)
