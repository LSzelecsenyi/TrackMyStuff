/*
 * Adapted anatomical SVG path data from body-muscles.
 *
 * Original project: Body Muscles
 * Repository: https://github.com/vulovix/body-muscles
 * Homepage: https://vulovix.github.io/body-muscles/
 * npm package: body-muscles@1.0.0
 * Source commit (npm gitHead): 38216b99c7c67518579a2eb71895886621c864ca
 * Copyright 2024 Ivan Vulović
 * Licensed under the Apache License, Version 2.0
 *
 * This file was converted for native Android / Jetpack Compose rendering.
 * We did not create the original anatomical artwork.
 *
 * TODO: Revalidate artwork provenance or replace this data before any public distribution.
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
