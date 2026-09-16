package hu.laca.weighttracker.domain.musclemap

import hu.laca.weighttracker.domain.exercise.MuscleGroup
import hu.laca.weighttracker.domain.workout.SessionStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object MuscleHeatmapAssembler {
    val anatomicalGroups: List<MuscleGroup> = MuscleGroup.entries.filter {
        it != MuscleGroup.FULL_BODY && it != MuscleGroup.CARDIOVASCULAR
    }

    fun assemble(
        exercises: List<MuscleTrainingExercise>,
        today: LocalDate
    ): MuscleHeatmapState {
        val latestByGroup = linkedMapOf<MuscleGroup, LocalDate>()
        var fullBody: LocalDate? = null
        var cardiovascular: LocalDate? = null
        var hasCompletedWorkouts = false

        exercises.forEach { exercise ->
            if (exercise.status != SessionStatus.COMPLETED) {
                return@forEach
            }
            hasCompletedWorkouts = true
            if (exercise.completedSetCount <= 0) {
                return@forEach
            }
            val date = exercise.workoutDate
            record(latestByGroup, exercise.primaryMuscle, date)
            exercise.secondaryMuscles.forEach { group ->
                record(latestByGroup, group, date)
            }
            if (exercise.primaryMuscle == MuscleGroup.FULL_BODY ||
                MuscleGroup.FULL_BODY in exercise.secondaryMuscles
            ) {
                fullBody = later(fullBody, date)
            }
            if (exercise.primaryMuscle == MuscleGroup.CARDIOVASCULAR ||
                MuscleGroup.CARDIOVASCULAR in exercise.secondaryMuscles
            ) {
                cardiovascular = later(cardiovascular, date)
            }
        }

        val entries = anatomicalGroups.associateWith { group ->
            val last = latestByGroup[group]
            val daysAgo = last?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(0) }
            MuscleHeatmapEntry(
                group = group,
                lastTrained = last,
                daysAgo = daysAgo,
                band = bandFor(daysAgo)
            )
        }
        return MuscleHeatmapState(
            entries = entries,
            hasCompletedWorkouts = hasCompletedWorkouts,
            fullBody = fullBody?.let { entryFor(MuscleGroup.FULL_BODY, it, today) },
            cardiovascular = cardiovascular?.let { entryFor(MuscleGroup.CARDIOVASCULAR, it, today) }
        )
    }

    private fun entryFor(
        group: MuscleGroup,
        last: LocalDate,
        today: LocalDate
    ): MuscleHeatmapEntry {
        val daysAgo = ChronoUnit.DAYS.between(last, today).toInt().coerceAtLeast(0)
        return MuscleHeatmapEntry(
            group = group,
            lastTrained = last,
            daysAgo = daysAgo,
            band = bandFor(daysAgo)
        )
    }

    fun bandFor(daysAgo: Int?): MuscleRecencyBand {
        return when {
            daysAgo == null -> MuscleRecencyBand.NEVER
            daysAgo == 0 -> MuscleRecencyBand.TODAY
            daysAgo in 1..2 -> MuscleRecencyBand.DAYS_1_2
            daysAgo in 3..4 -> MuscleRecencyBand.DAYS_3_4
            daysAgo in 5..6 -> MuscleRecencyBand.DAYS_5_6
            daysAgo in 7..13 -> MuscleRecencyBand.DAYS_7_13
            else -> MuscleRecencyBand.DAYS_14_PLUS
        }
    }

    private fun record(
        latestByGroup: MutableMap<MuscleGroup, LocalDate>,
        group: MuscleGroup,
        date: LocalDate
    ) {
        if (group == MuscleGroup.FULL_BODY || group == MuscleGroup.CARDIOVASCULAR) {
            return
        }
        latestByGroup[group] = later(latestByGroup[group], date)
    }

    private fun later(current: LocalDate?, candidate: LocalDate): LocalDate {
        return if (current == null || candidate.isAfter(current)) candidate else current
    }
}
