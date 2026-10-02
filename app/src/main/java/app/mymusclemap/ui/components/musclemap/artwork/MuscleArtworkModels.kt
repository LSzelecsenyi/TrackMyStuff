/*
 * Region model for the adapted body-muscles artwork.
 * Provenance, revision, and modifications are recorded on BodyMusclesArtwork.
 */
package app.mymusclemap.ui.components.musclemap.artwork

import app.mymusclemap.domain.exercise.MuscleGroup

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
