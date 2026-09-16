package hu.laca.weighttracker.domain.workoutimport

import hu.laca.weighttracker.domain.exercise.ExerciseNaming
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.workout.TemplateNaming
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object WorkoutImportFingerprint {
    const val VERSION = "workout-import-fingerprint-v1"

    fun hash(workout: WorkoutImportResolvedWorkout): String {
        return sha256Hex(canonical(workout))
    }

    fun canonical(workout: WorkoutImportResolvedWorkout): String {
        val builder = StringBuilder()
        builder.appendLine(VERSION)
        builder.appendLine("workout_date=${workout.workoutDate}")
        builder.appendLine("workout_name=${workout.normalizedName}")
        builder.appendLine("started_at=${workout.startedAt}")
        builder.appendLine("finished_at=${workout.finishedAt}")
        builder.appendLine("notes=${escape(TemplateNaming.normalize(workout.notes.orEmpty()))}")
        builder.appendLine("body_weight_kg=${canonicalKilograms(workout.bodyWeight.kilograms)}")
        builder.appendLine("body_weight_source=${workout.bodyWeight.source.name}")
        builder.appendLine("body_weight_source_date=${workout.bodyWeight.sourceDate?.toString().orEmpty()}")
        workout.exercises.forEach { exercise ->
            builder.append(canonicalExercise(exercise))
        }
        return builder.toString()
    }

    private fun canonicalExercise(exercise: WorkoutImportResolvedExercise): String {
        val snapshot = exercise.snapshot
        val builder = StringBuilder()
        builder.appendLine("exercise")
        builder.appendLine("index=${exercise.exerciseIndex}")
        builder.appendLine("catalog_id=${snapshot?.exerciseId ?: ""}")
        builder.appendLine("catalog_name=${snapshot?.catalogName?.let(ExerciseNaming::normalize).orEmpty()}")
        builder.appendLine("measurement=${snapshot?.measurementType?.name.orEmpty()}")
        builder.appendLine("resistance=${snapshot?.resistanceBasis?.name.orEmpty()}")
        builder.appendLine("weight_interpretation=${snapshot?.weightInterpretation?.name.orEmpty()}")
        builder.appendLine("primary=${snapshot?.primaryMuscle?.name.orEmpty()}")
        builder.appendLine("secondary=${canonicalSecondaries(snapshot?.secondaryMuscles.orEmpty())}")
        exercise.sets.forEach { set ->
            builder.append(canonicalSet(set))
        }
        return builder.toString()
    }

    private fun canonicalSet(set: WorkoutImportResolvedSet): String {
        val builder = StringBuilder()
        builder.appendLine("set")
        builder.appendLine("index=${set.setIndex}")
        builder.appendLine("status=${set.status.name}")
        builder.appendLine("reps=${set.reps?.toString().orEmpty()}")
        builder.appendLine("duration_seconds=${set.durationSeconds?.toString().orEmpty()}")
        builder.appendLine("distance_meters=${WorkoutImportNumbers.canonical(set.distanceMeters)}")
        builder.appendLine("load_kind=${set.loadKind?.name.orEmpty()}")
        builder.appendLine("weight_kg=${WorkoutImportNumbers.canonical(set.weightKg)}")
        return builder.toString()
    }

    private fun canonicalSecondaries(muscles: List<MuscleGroup>): String {
        return muscles.map { it.name }.distinct().sorted().joinToString(",")
    }

    private fun canonicalKilograms(value: Double?): String {
        if (value == null) {
            return ""
        }
        return WorkoutImportNumbers.canonical(BigDecimal.valueOf(value))
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\n", "\\n")
    }
}
