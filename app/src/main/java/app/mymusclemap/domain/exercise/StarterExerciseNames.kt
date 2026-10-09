package app.mymusclemap.domain.exercise

import android.content.res.Resources
import app.mymusclemap.R

/**
 * Display names for the seeded catalog. The stored name stays the English
 * catalog identity, so a language change cannot rewrite user data.
 */
object StarterExerciseNames {
    private val resources: Map<String, Int> = mapOf(
        "squat" to R.string.starter_exercise_squat,
        "leg press" to R.string.starter_exercise_leg_press,
        "lunge" to R.string.starter_exercise_lunge,
        "deadlift" to R.string.starter_exercise_deadlift,
        "romanian deadlift" to R.string.starter_exercise_romanian_deadlift,
        "hip thrust" to R.string.starter_exercise_hip_thrust,
        "leg curl" to R.string.starter_exercise_leg_curl,
        "calf raise" to R.string.starter_exercise_calf_raise,
        "bench press" to R.string.starter_exercise_bench_press,
        "push-up" to R.string.starter_exercise_push_up,
        "overhead press" to R.string.starter_exercise_overhead_press,
        "dip" to R.string.starter_exercise_dip,
        "pull-up" to R.string.starter_exercise_pull_up,
        "chin-up" to R.string.starter_exercise_chin_up,
        "lat pulldown" to R.string.starter_exercise_lat_pulldown,
        "barbell row" to R.string.starter_exercise_barbell_row,
        "dumbbell row" to R.string.starter_exercise_dumbbell_row,
        "face pull" to R.string.starter_exercise_face_pull,
        "lateral raise" to R.string.starter_exercise_lateral_raise,
        "biceps curl" to R.string.starter_exercise_biceps_curl,
        "triceps extension" to R.string.starter_exercise_triceps_extension,
        "plank" to R.string.starter_exercise_plank
    )

    fun resId(storedName: String): Int? {
        return resources[ExerciseNaming.normalize(storedName)]
    }

    fun display(resources: Resources, storedName: String): String {
        val resId = resId(storedName) ?: return storedName
        return resources.getString(resId)
    }
}
