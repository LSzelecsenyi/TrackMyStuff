package app.mymusclemap.domain.workoutimport

import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.workout.TemplateNaming

object WorkoutImportCanonical {
    fun workout(workout: WorkoutImportWorkout): String {
        val builder = StringBuilder()
        builder.appendLine("v1")
        builder.appendLine("workout_date=${workout.workoutDate}")
        builder.appendLine("workout_name=${TemplateNaming.normalize(workout.name)}")
        builder.appendLine("started_at=${workout.startedAt}")
        builder.appendLine("finished_at=${workout.finishedAt}")
        builder.appendLine("notes=${escape(workout.notes.orEmpty())}")
        builder.appendLine("body_weight_kg=${WorkoutImportNumbers.canonical(workout.bodyWeightKg)}")
        workout.exercises.forEach { exercise ->
            builder.append(exercise(exercise))
        }
        return builder.toString()
    }

    fun exercise(exercise: WorkoutImportExercise): String {
        val builder = StringBuilder()
        builder.appendLine("exercise")
        builder.appendLine("index=${exercise.exerciseIndex}")
        builder.appendLine("name=${ExerciseNaming.normalize(exercise.name)}")
        exercise.sets.forEach { set ->
            builder.append(set(set))
        }
        return builder.toString()
    }

    fun set(set: WorkoutImportSet): String {
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

    fun document(document: WorkoutImportDocument): List<String> {
        return document.workouts.map(::workout)
    }

    private fun escape(value: String): String {
        return value.replace("\\", "\\\\").replace("\n", "\\n")
    }
}
