/*
 * Logical muscle groups for the custom anatomical map.
 * Several SVG paths may draw one group. Path ids are not stored in the database.
 *
 * path27 is the posterior oblique: the lateral waist on the back, flanking the
 * erectors and sitting above the glutes. The Inkscape file left it unnamed.
 * sbd is the rectus abdominis; its Inkscape label is "abs".
 * traps_front is the anterior trapezius and belongs to UPPER_BACK.
 * tigh_extensors_back is the quadriceps visible from behind.
 */
package app.mymusclemap.ui.components.musclemap.artwork

import app.mymusclemap.domain.exercise.MuscleGroup

object MuscleMapCatalog {
    val groupByPathId: Map<String, MuscleGroup> = mapOf(
        "pecs" to MuscleGroup.CHEST,
        "front_delts" to MuscleGroup.FRONT_DELTOID,
        "side_delts_front" to MuscleGroup.SIDE_DELTOID,
        "side_delts_back" to MuscleGroup.SIDE_DELTOID,
        "back_delts_back" to MuscleGroup.REAR_DELTOID,
        "traps_front" to MuscleGroup.UPPER_BACK,
        "upper_back" to MuscleGroup.UPPER_BACK,
        "neck" to MuscleGroup.NECK,
        "neck_back" to MuscleGroup.NECK,
        "biceps" to MuscleGroup.BICEPS,
        "biceps_right" to MuscleGroup.BICEPS,
        "triceps_left_front" to MuscleGroup.TRICEPS,
        "triceps" to MuscleGroup.TRICEPS,
        "forearms_front" to MuscleGroup.FOREARMS,
        "forearms_back" to MuscleGroup.FOREARMS,
        "lats_front" to MuscleGroup.LATS,
        "lats" to MuscleGroup.LATS,
        "sbd" to MuscleGroup.ABS,
        "serratus" to MuscleGroup.SERRATUS,
        "obliques_front" to MuscleGroup.OBLIQUES,
        "path27" to MuscleGroup.OBLIQUES,
        "lower_back" to MuscleGroup.LOWER_BACK,
        "glutes" to MuscleGroup.GLUTES,
        "tigh_extensors_front" to MuscleGroup.QUADRICEPS,
        "tigh_extensors_back" to MuscleGroup.QUADRICEPS,
        "tigh_flexors" to MuscleGroup.HAMSTRINGS,
        "tigh_adductors_front" to MuscleGroup.ADDUCTORS,
        "tigh_adductors_back" to MuscleGroup.ADDUCTORS,
        "calves_front" to MuscleGroup.CALVES,
        "calves_back" to MuscleGroup.CALVES
    )

    internal fun regionsFor(artworkPaths: List<MaleMusclePath>): List<MuscleArtworkRegion> {
        return artworkPaths.map { path ->
            MuscleArtworkRegion(
                id = path.id,
                view = path.view,
                side = sideFor(path.id),
                muscleGroup = groupByPathId[path.id],
                pathData = path.pathData
            )
        }
    }

    fun sideFor(pathId: String): AnatomicalSide {
        return when (pathId) {
            "biceps" -> AnatomicalSide.LEFT
            "biceps_right" -> AnatomicalSide.RIGHT
            else -> AnatomicalSide.CENTRAL
        }
    }
}

object MuscleMapArtworkSelector {
    /**
     * Female artwork is not available yet. Preferred sex is accepted so a later
     * asset can be selected without changing muscle-group logic. Until then the
     * male map is the only visualization. There is no gender control in the UI.
     */
    fun select(preferred: MuscleMapSex = MuscleMapSex.MALE): MuscleMapFigureArtwork {
        return when (preferred) {
            MuscleMapSex.MALE -> male()
            MuscleMapSex.FEMALE -> femaleOrNull() ?: male()
        }
    }

    fun current(): MuscleMapFigureArtwork = select(MuscleMapSex.MALE)

    fun femaleOrNull(): MuscleMapFigureArtwork? = null

    private fun male(): MuscleMapFigureArtwork {
        return MuscleMapFigureArtwork(
            sex = MuscleMapSex.MALE,
            viewWidth = MaleMuscleMapGeometry.FIGURE_WIDTH,
            viewHeight = MaleMuscleMapGeometry.FIGURE_HEIGHT,
            splitX = MaleMuscleMapGeometry.SPLIT_X,
            anatomyAsset = MaleMuscleMapGeometry.ANATOMY_ASSET,
            regions = MuscleMapCatalog.regionsFor(MaleMuscleMapGeometry.paths)
        )
    }
}
