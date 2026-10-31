/*
 * Logical muscle groups for the anatomical maps.
 * Several SVG paths may draw one group. Path ids are not stored in the database.
 *
 * Male and female files do not share every path id. The maps below keep one
 * MuscleGroup for both drawings. Activation math does not read these ids.
 *
 * obliques_back is the posterior oblique: the lateral waist on the back,
 * flanking the erectors and sitting above the glutes. Older artwork left that
 * region unnamed.
 * sbd is the male rectus abdominis; its Inkscape label is "abs". The female
 * file uses the id abs for the same muscle.
 * traps_front is the anterior trapezius and belongs to UPPER_BACK.
 * tigh_extensors_back is the quadriceps visible from behind.
 *
 * Female id differences, matched by where the path sits on the drawing:
 * - side_delts_front is the anterior deltoid (male id front_delts). Its
 *   Inkscape label repeats "side_delts_front" and does not match that place.
 * - path3, also labeled side_delts_front, is the lateral deltoid cap
 *   (male id side_delts_front).
 * - triceps_front is the male triceps_left_front region.
 * - tigh_extensors_2 and path4 are both quadriceps. The male file draws that
 *   muscle as one path, tigh_extensors_front. path4 is the outer strip.
 * - biceps is one path covering both arms. The male file splits biceps and
 *   biceps_right.
 */
package app.mymusclemap.ui.components.musclemap.artwork

import app.mymusclemap.data.preferences.DisplayedMuscleMapBodyModel
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel

object MuscleMapCatalog {
    val maleGroupByPathId: Map<String, MuscleGroup> = mapOf(
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
        "obliques_back" to MuscleGroup.OBLIQUES,
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

    val femaleGroupByPathId: Map<String, MuscleGroup> = mapOf(
        "pecs" to MuscleGroup.CHEST,
        "side_delts_front" to MuscleGroup.FRONT_DELTOID,
        "path3" to MuscleGroup.SIDE_DELTOID,
        "side_delts_back" to MuscleGroup.SIDE_DELTOID,
        "back_delts_back" to MuscleGroup.REAR_DELTOID,
        "traps_front" to MuscleGroup.UPPER_BACK,
        "upper_back" to MuscleGroup.UPPER_BACK,
        "neck" to MuscleGroup.NECK,
        "neck_back" to MuscleGroup.NECK,
        "biceps" to MuscleGroup.BICEPS,
        "triceps_front" to MuscleGroup.TRICEPS,
        "triceps" to MuscleGroup.TRICEPS,
        "forearms_front" to MuscleGroup.FOREARMS,
        "forearms_back" to MuscleGroup.FOREARMS,
        "lats_front" to MuscleGroup.LATS,
        "lats" to MuscleGroup.LATS,
        "abs" to MuscleGroup.ABS,
        "serratus" to MuscleGroup.SERRATUS,
        "obliques_front" to MuscleGroup.OBLIQUES,
        "obliques_back" to MuscleGroup.OBLIQUES,
        "lower_back" to MuscleGroup.LOWER_BACK,
        "glutes" to MuscleGroup.GLUTES,
        "tigh_extensors_2" to MuscleGroup.QUADRICEPS,
        "path4" to MuscleGroup.QUADRICEPS,
        "tigh_extensors_back" to MuscleGroup.QUADRICEPS,
        "tigh_flexors" to MuscleGroup.HAMSTRINGS,
        "tigh_adductors_front" to MuscleGroup.ADDUCTORS,
        "tigh_adductors_back" to MuscleGroup.ADDUCTORS,
        "calves_front" to MuscleGroup.CALVES,
        "calves_back" to MuscleGroup.CALVES
    )

    internal fun regionsFor(
        artworkPaths: List<MuscleArtworkPath>,
        groups: Map<String, MuscleGroup>
    ): List<MuscleArtworkRegion> {
        return artworkPaths.map { path ->
            val group = groups[path.id]
                ?: error("Muscle map path ${path.id} has no MuscleGroup")
            MuscleArtworkRegion(
                id = path.id,
                view = path.view,
                side = sideFor(path.id),
                muscleGroup = group,
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
    fun select(preferred: MuscleMapBodyModel = MuscleMapBodyModel.MALE): MuscleMapFigureArtwork {
        return when (preferred) {
            MuscleMapBodyModel.MALE -> male()
            MuscleMapBodyModel.FEMALE -> female()
        }
    }

    fun current(): MuscleMapFigureArtwork = select(DisplayedMuscleMapBodyModel.current)

    private fun male(): MuscleMapFigureArtwork {
        return MuscleMapFigureArtwork(
            bodyModel = MuscleMapBodyModel.MALE,
            viewWidth = MaleMuscleMapGeometry.FIGURE_WIDTH,
            viewHeight = MaleMuscleMapGeometry.FIGURE_HEIGHT,
            splitX = MaleMuscleMapGeometry.SPLIT_X,
            anatomyAsset = MaleMuscleMapGeometry.ANATOMY_ASSET,
            regions = MuscleMapCatalog.regionsFor(
                MaleMuscleMapGeometry.paths,
                MuscleMapCatalog.maleGroupByPathId
            )
        )
    }

    private fun female(): MuscleMapFigureArtwork {
        return MuscleMapFigureArtwork(
            bodyModel = MuscleMapBodyModel.FEMALE,
            viewWidth = FemaleMuscleMapGeometry.FIGURE_WIDTH,
            viewHeight = FemaleMuscleMapGeometry.FIGURE_HEIGHT,
            splitX = FemaleMuscleMapGeometry.SPLIT_X,
            anatomyAsset = FemaleMuscleMapGeometry.ANATOMY_ASSET,
            regions = MuscleMapCatalog.regionsFor(
                FemaleMuscleMapGeometry.paths,
                MuscleMapCatalog.femaleGroupByPathId
            )
        )
    }
}
