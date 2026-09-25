package app.mymusclemap.domain.workoutimport

import java.math.BigDecimal

object WorkoutImportLimits {
    const val FORMAT_VERSION = 1
    const val COLUMN_COUNT = 18

    const val MAX_UTF8_BYTES = 1_048_576
    const val MAX_ROWS = 5_000
    const val MAX_WORKOUTS = 200
    const val MAX_EXERCISES_PER_WORKOUT = 40
    const val MAX_SETS_PER_EXERCISE = 40
    const val MAX_FIELD_CHARS = 8_192

    const val MAX_WORKOUT_ID_LENGTH = 80
    const val MAX_WORKOUT_NAME_LENGTH = 80
    const val MAX_EXERCISE_NAME_LENGTH = 100
    const val MAX_NOTES_LENGTH = 500

    const val MAX_REPS = 1_000
    const val MAX_DURATION_SECONDS = 86_400
    val MAX_DISTANCE_METERS: BigDecimal = BigDecimal("100000")
    val MIN_BODY_WEIGHT_KG: BigDecimal = BigDecimal("30.0")
    val MAX_BODY_WEIGHT_KG: BigDecimal = BigDecimal("300.0")
    val MAX_LOAD_WEIGHT_KG: BigDecimal = BigDecimal("500")
    const val BODY_WEIGHT_DECIMALS = 1
    const val LOAD_WEIGHT_DECIMALS = 2
    const val DISTANCE_DECIMALS = 3
}
