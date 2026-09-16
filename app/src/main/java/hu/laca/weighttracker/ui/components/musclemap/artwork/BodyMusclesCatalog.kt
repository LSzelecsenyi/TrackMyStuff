/*
 * Mapping from body-muscles region IDs to application MuscleGroup values.
 *
 * Original project: Body Muscles
 * Repository: https://github.com/vulovix/body-muscles
 * npm package: body-muscles@1.0.0
 * Source commit (npm gitHead): 38216b99c7c67518579a2eb71895886621c864ca
 * Copyright 2024 Ivan Vulović
 * Licensed under the Apache License, Version 2.0
 *
 * This mapping was adapted for native Android. We did not create the artwork.
 * Abdominal Has / Ferde hasizom IDs are extracted subpaths of the upstream
 * serratus-anterior-* and obliques-* compound paths.
 *
 * TODO: Revalidate artwork provenance or replace this data before any public distribution.
 */
package hu.laca.weighttracker.ui.components.musclemap.artwork

import hu.laca.weighttracker.domain.exercise.MuscleGroup

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
