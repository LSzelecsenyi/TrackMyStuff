/*
 * Maps body-muscles region ids onto this app's MuscleGroup values.
 * Path provenance is recorded on BodyMusclesArtwork.
 * The abs-block-* and obliques-block-* ids are the split subpaths of the
 * upstream serratus-anterior-* and obliques-* paths.
 */
package app.mymusclemap.ui.components.musclemap.artwork

import app.mymusclemap.domain.exercise.MuscleGroup

object BodyMusclesCatalog {
    val regions: List<MuscleArtworkRegion> = BodyMusclesArtwork.regions

    val mappedGroups: Set<MuscleGroup> = regions.mapNotNull { it.muscleGroup }.toSet()

    val unmappedIds: Set<String> = regions.filter { it.muscleGroup == null }.map { it.id }.toSet()

    fun groupFor(regionId: String): MuscleGroup? {
        return regions.firstOrNull { it.id == regionId }?.muscleGroup
    }

    fun regionsFor(group: MuscleGroup): List<MuscleArtworkRegion> {
        return regions.filter { it.muscleGroup == group }
    }

    fun regionsFor(view: MuscleMapView): List<MuscleArtworkRegion> {
        return regions.filter { it.view == view }
    }

    fun hasAnatomicalPaths(group: MuscleGroup): Boolean {
        return group in mappedGroups
    }
}
