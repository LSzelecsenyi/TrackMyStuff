package app.mymusclemap.dev

import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupSnapshot
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.appbackup.AppBackupTables
import app.mymusclemap.data.appbackup.AppBackupValidator
import app.mymusclemap.data.local.BodyMeasurementEntity
import app.mymusclemap.data.local.ExerciseEntity
import app.mymusclemap.data.local.ExerciseMuscleEntity
import app.mymusclemap.data.local.ScheduledWorkoutEntity
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseMuscleEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.data.local.WeeklyWorkoutGoalEntity
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.local.WorkoutTemplateExerciseEntity
import app.mymusclemap.data.local.WorkoutTemplateSetEntity
import app.mymusclemap.data.local.toModel
import app.mymusclemap.domain.body.BodyMeasurementParser
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.exercise.ExerciseDraft
import app.mymusclemap.domain.exercise.ExerciseNaming
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MuscleRole
import app.mymusclemap.domain.exercise.StarterCatalog
import app.mymusclemap.domain.statistics.StatisticsRange
import app.mymusclemap.domain.statistics.TrainingStatistics
import app.mymusclemap.domain.statistics.TrainingStatisticsLogic
import app.mymusclemap.domain.statistics.WorkoutSetVolume
import app.mymusclemap.domain.theme.AppearanceCodec
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.ScheduledWorkout
import app.mymusclemap.domain.workout.SessionExerciseItem
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import app.mymusclemap.domain.workout.TemplateNaming
import app.mymusclemap.domain.workout.WeekVerdict
import app.mymusclemap.domain.workout.WeeklyGoalLogic
import app.mymusclemap.domain.workout.WeeklyGoalRevision
import app.mymusclemap.domain.workout.WorkoutSessionAggregate
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import kotlin.math.round
import kotlin.math.sin

/**
 * Development-only generator for an importable app backup.
 *
 * It builds [AppBackupSnapshot] rows from the starter catalog and the same
 * entity model the app exports, then encodes them with [AppBackupJson]. Nothing
 * here is referenced from production source, and it never opens the app database.
 *
 * The checked-in example is [DemoTrainingHistoryPaths.backupFile], under
 * `app/src/test/resources`, because `/dev` is gitignored. Normal tests
 * always use [referenceDate] and ignore Gradle properties. Regenerate the file
 * from the repository root. Quote each `-P` on PowerShell:
 *
 * `gradlew.bat :app:testDebugUnitTest --tests app.mymusclemap.dev.DemoTrainingHistoryTest.writesDemoBackupFileWhenRequested "-Pdemo.backup.write=true" "-Pdemo.referenceDate=2026-10-02"`
 *
 * Omit `-Pdemo.referenceDate` to write the fixed [referenceDate] dataset.
 * Restore that file yourself through Settings. This generator does not seed the app.
 */
object DemoTrainingHistoryGenerator {
    const val REFERENCE_DATE_PROPERTY: String = "demo.referenceDate"

    /** Fixed date for tests. Never [LocalDate.now]. */
    val referenceDate: LocalDate = LocalDate.of(2026, 9, 26)

    private val referenceDatePattern = Regex("""\d{4}-\d{2}-\d{2}""")

    /**
     * Resolves the opt-in backup date. Blank means [referenceDate].
     * Anything else must be a real `YYYY-MM-DD` date.
     */
    fun referenceDateFromProperty(raw: String?): LocalDate {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return referenceDate
        if (!referenceDatePattern.matches(value)) {
            throw IllegalArgumentException(invalidReferenceDateMessage(value))
        }
        return try {
            LocalDate.parse(value)
        } catch (error: DateTimeParseException) {
            throw IllegalArgumentException(invalidReferenceDateMessage(value), error)
        }
    }

    private fun invalidReferenceDateMessage(value: String): String {
        return "Invalid $REFERENCE_DATE_PROPERTY '$value'. Expected YYYY-MM-DD."
    }

    private val zone: ZoneOffset = ZoneOffset.UTC
    private const val WORKOUT_START_HOUR = 9

    fun calendar(referenceDate: LocalDate = this.referenceDate): DemoCalendar {
        val historyStart = referenceDate.minusMonths(18)
        val breakStart = referenceDate.minusWeeks(10)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val breakEnd = breakStart.plusDays(13)
        return DemoCalendar(
            referenceDate = referenceDate,
            historyStart = historyStart,
            breakStart = breakStart,
            breakEnd = breakEnd,
            returnEnd = breakEnd.plusDays(14)
        )
    }

    fun generate(referenceDate: LocalDate = this.referenceDate): AppBackupSnapshot {
        val calendar = calendar(referenceDate)
        val catalog = buildCatalog(calendar)
        val templates = buildTemplates(calendar, catalog)
        val weights = buildWeights(calendar)
        val bodyMeasurements = buildBodyMeasurements(calendar)
        val outcomes = assignOutcomes(buildSlots(calendar), calendar)
        val training = shapeWeeklyGoalScenario(
            buildTraining(outcomes, calendar, catalog, templates, weights),
            calendar,
            templates
        )
        val snapshot = AppBackupSnapshot(
            formatVersion = AppBackupFormat.FORMAT_VERSION,
            schemaVersion = AppBackupFormat.SCHEMA_VERSION,
            exportedAt = calendar.referenceDate.atTime(18, 0).toInstant(zone),
            source = AppBackupSource(
                applicationId = "app.mymusclemap.debug",
                versionName = "0.1.0-debug"
            ),
            tables = AppBackupTables(
                weightMeasurements = weights.sortedBy { it.id },
                exercises = catalog.map { it.entity }.sortedBy { it.id },
                exerciseMuscles = catalog.flatMap { it.muscles }
                    .sortedWith(compareBy({ it.exerciseId }, { it.muscleGroup })),
                workoutTemplates = templates.templates.sortedBy { it.id },
                workoutTemplateExercises = templates.exercises.sortedBy { it.id },
                workoutTemplateSets = templates.sets.sortedBy { it.id },
                scheduledWorkouts = training.scheduled.sortedBy { it.id },
                workoutSessions = training.sessions.sortedBy { it.id },
                workoutSessionExercises = training.sessionExercises.sortedBy { it.id },
                workoutSessionExerciseMuscles = training.sessionMuscles
                    .sortedWith(compareBy({ it.sessionExerciseId }, { it.muscleGroup })),
                workoutSessionSets = training.sessionSets.sortedBy { it.id },
                bodyMeasurements = bodyMeasurements.sortedBy { it.id },
                weeklyWorkoutGoals = training.goals.sortedBy { it.effectiveWeekStart }
            ),
            settings = AppearanceCodec.encode(AppearanceSettings.Default)
        )
        val errors = AppBackupValidator.validate(snapshot)
        check(errors.isEmpty()) {
            errors.joinToString(separator = "\n") { "${it.code} ${it.detail.orEmpty()}" }
        }
        check(
            snapshot.tables.workoutSessions.none {
                it.status == SessionStatus.COMPLETED.name &&
                    LocalDate.parse(it.workoutDate).isAfter(calendar.referenceDate)
            }
        ) { "Completed session is after the reference date" }
        return snapshot
    }

    fun encode(referenceDate: LocalDate = this.referenceDate): String {
        return AppBackupJson.encode(generate(referenceDate))
    }

    fun writeBackup(
        target: File = DemoTrainingHistoryPaths.backupFile(),
        referenceDate: LocalDate = this.referenceDate
    ): File {
        target.parentFile?.mkdirs()
        target.writeText(encode(referenceDate))
        return target
    }

    internal fun statistics(snapshot: AppBackupSnapshot, range: StatisticsRange): TrainingStatistics {
        val today = snapshot.exportedAt.atZone(zone).toLocalDate()
        return TrainingStatisticsLogic.assemble(
            aggregates(snapshot.tables),
            scheduledModels(snapshot.tables),
            today,
            range
        )
    }

    fun analyze(snapshot: AppBackupSnapshot): DemoHistoryAnalysis {
        val today = snapshot.exportedAt.atZone(zone).toLocalDate()
        val calendar = calendar(today)
        val tables = snapshot.tables
        val aggregates = aggregates(tables)
        val scheduledModels = scheduledModels(tables)
        val sessionsBySchedule = tables.workoutSessions
            .mapNotNull { session -> session.scheduledWorkoutId?.let { it to session } }
            .toMap()
        val completedSessions = tables.workoutSessions.filter { it.status == SessionStatus.COMPLETED.name }
        val completedDates = completedSessions.map { LocalDate.parse(it.workoutDate) }.sorted()
        val ranges = StatisticsRange.entries.map { range ->
            val stats = TrainingStatisticsLogic.assemble(aggregates, scheduledModels, today, range)
            val bench = stats.exercises.firstOrNull { it.name == "Bench Press" }
            DemoRangeStats(
                range = range,
                workouts = stats.activity.workoutCount,
                volumeKg = stats.volume.totalKg,
                volumeWeeks = stats.volume.trend.size,
                adherencePercent = stats.adherence.percent,
                planned = stats.adherence.plannedCount,
                completedScheduled = stats.adherence.completedCount,
                missed = stats.adherence.missedCount,
                benchHistoryPoints = bench?.history?.size ?: 0,
                muscles = stats.muscleDistribution.size
            )
        }
        val all = TrainingStatisticsLogic.assemble(
            aggregates,
            scheduledModels,
            today,
            StatisticsRange.All
        )
        val weeklyVolume = weeklyVolume(aggregates)
        val positiveWeeks = weeklyVolume.filter { it > 0.0 }
        val breakVolume = aggregates
            .filter { aggregate ->
                val date = aggregate.session.workoutDate
                !date.isBefore(calendar.breakStart) && !date.isAfter(calendar.breakEnd)
            }
            .sumOf { aggregate ->
                aggregate.exercises.sumOf { item ->
                    item.sets.sumOf { set -> WorkoutSetVolume.volumeKg(item.exercise, set) ?: 0.0 }
                }
            }
        val benchLoads = benchLoads(aggregates)
        val beforeBreak = benchLoads.last { it.date.isBefore(calendar.breakStart) }
        val afterBreak = benchLoads.first { it.date.isAfter(calendar.breakEnd) }
        val examples = scheduleExamples(tables, sessionsBySchedule, today)
        val late = tables.scheduledWorkouts.count { row ->
            val session = sessionsBySchedule[row.id] ?: return@count false
            row.cancelledAt == null &&
                row.originalScheduledDate == row.scheduledDate &&
                LocalDate.parse(session.workoutDate).isAfter(LocalDate.parse(row.scheduledDate))
        }
        val rescheduled = tables.scheduledWorkouts.count { row ->
            row.cancelledAt == null && row.originalScheduledDate != row.scheduledDate
        }
        return DemoHistoryAnalysis(
            referenceDate = today,
            historyStart = calendar.historyStart,
            firstCompleted = completedDates.first(),
            lastCompleted = completedDates.last(),
            breakStart = calendar.breakStart,
            breakEnd = calendar.breakEnd,
            bodyWeightEntries = tables.weightMeasurements.size,
            firstBodyWeightKg = tables.weightMeasurements.minBy { it.date }.weightKg,
            lastBodyWeightKg = tables.weightMeasurements.maxBy { it.date }.weightKg,
            workoutTemplates = tables.workoutTemplates.size,
            exercises = tables.exercises.size,
            scheduledOccurrences = tables.scheduledWorkouts.size,
            completedScheduled = all.adherence.completedCount,
            lateCompletions = late,
            missedOccurrences = all.adherence.missedCount,
            cancelledOccurrences = tables.scheduledWorkouts.count { it.cancelledAt != null },
            rescheduledOccurrences = rescheduled,
            unplannedWorkouts = completedSessions.count { it.scheduledWorkoutId == null },
            completedSessions = completedSessions.size,
            completedSets = tables.workoutSessionSets.count { it.status == SessionSetStatus.COMPLETED.name },
            volumeSets = aggregates.sumOf { aggregate ->
                aggregate.exercises.sumOf { item -> item.sets.count { WorkoutSetVolume.isVolumeEligible(item.exercise, it) } }
            },
            adherencePercent = all.adherence.percent,
            adherencePlanned = all.adherence.plannedCount,
            adherenceCompleted = all.adherence.completedCount,
            ranges = ranges,
            bench = DemoBenchProgression(
                firstDate = benchLoads.first().date,
                firstKg = benchLoads.first().kg,
                latestDate = benchLoads.last().date,
                latestKg = benchLoads.last().kg,
                beforeBreakKg = beforeBreak.kg,
                afterBreakKg = afterBreak.kg,
                hasDecrease = benchLoads.zipWithNext().any { (earlier, later) -> later.kg < earlier.kg },
                historyPoints = benchLoads.size
            ),
            heaviestWeekKg = positiveWeeks.maxOrNull() ?: 0.0,
            lightestPositiveWeekKg = positiveWeeks.minOrNull() ?: 0.0,
            breakVolumeKg = breakVolume,
            examples = examples
        )
    }

    private fun aggregates(tables: AppBackupTables): List<WorkoutSessionAggregate> {
        val exercisesBySession = tables.workoutSessionExercises.groupBy { it.sessionId }
        val setsByExercise = tables.workoutSessionSets.groupBy { it.sessionExerciseId }
        val musclesByExercise = tables.workoutSessionExerciseMuscles.groupBy { it.sessionExerciseId }
        return tables.workoutSessions.map { session ->
            val exercises = exercisesBySession[session.id].orEmpty().sortedBy { it.position }
            WorkoutSessionAggregate(
                session = session.toModel(),
                exercises = exercises.map { exercise ->
                    SessionExerciseItem(
                        exercise = exercise.toModel(musclesByExercise[exercise.id].orEmpty()),
                        sets = setsByExercise[exercise.id].orEmpty().sortedBy { it.position }.map { it.toModel() }
                    )
                }
            )
        }
    }

    private fun scheduledModels(tables: AppBackupTables): List<ScheduledWorkout> {
        val sessionsBySchedule = tables.workoutSessions.associateBy { it.scheduledWorkoutId }
        return tables.scheduledWorkouts.map { row ->
            val session = sessionsBySchedule[row.id]
            ScheduledWorkout(
                id = row.id,
                scheduledDate = LocalDate.parse(row.scheduledDate),
                templateId = row.templateId,
                templateName = row.templateName,
                exerciseCount = 0,
                plannedSetCount = 0,
                templateArchived = false,
                sessionId = session?.id,
                sessionStatus = session?.status?.let(SessionStatus::valueOf),
                createdAt = row.createdAt,
                originalScheduledDate = LocalDate.parse(row.originalScheduledDate),
                cancelledAt = row.cancelledAt
            )
        }
    }

    private fun weeklyVolume(aggregates: List<WorkoutSessionAggregate>): List<Double> {
        return aggregates
            .filter { it.session.status == SessionStatus.COMPLETED }
            .groupBy { isoWeekKey(it.session.workoutDate) }
            .values
            .map { week ->
                week.sumOf { aggregate ->
                    aggregate.exercises.sumOf { item ->
                        item.sets.sumOf { set -> WorkoutSetVolume.volumeKg(item.exercise, set) ?: 0.0 }
                    }
                }
            }
    }

    private fun isoWeekKey(date: LocalDate): Pair<Int, Int> {
        val weekFields = java.time.temporal.WeekFields.ISO
        return date.get(weekFields.weekBasedYear()) to date.get(weekFields.weekOfWeekBasedYear())
    }

    private fun benchLoads(aggregates: List<WorkoutSessionAggregate>): List<BenchLoad> {
        return aggregates
            .filter { it.session.status == SessionStatus.COMPLETED }
            .sortedBy { it.session.workoutDate }
            .mapNotNull { aggregate ->
                val kg = aggregate.exercises
                    .filter { it.exercise.name == "Bench Press" }
                    .flatMap { it.sets }
                    .filter {
                        it.status == SessionSetStatus.COMPLETED &&
                            it.actualLoadKind == PlannedLoadKind.EXTERNAL_WEIGHT
                    }
                    .mapNotNull { it.actualWeightKg }
                    .maxOrNull() ?: return@mapNotNull null
                BenchLoad(aggregate.session.workoutDate, kg)
            }
    }

    private fun scheduleExamples(
        tables: AppBackupTables,
        sessionsBySchedule: Map<Long, WorkoutSessionEntity>,
        today: LocalDate
    ): List<DemoScheduleExample> {
        val scheduled = tables.scheduledWorkouts
        fun example(label: String, row: ScheduledWorkoutEntity): DemoScheduleExample {
            val session = sessionsBySchedule[row.id]
            return DemoScheduleExample(
                label = label,
                templateName = row.templateName,
                originalScheduledDate = LocalDate.parse(row.originalScheduledDate),
                scheduledDate = LocalDate.parse(row.scheduledDate),
                workoutDate = session?.workoutDate?.let(LocalDate::parse),
                cancelled = row.cancelledAt != null
            )
        }
        val onTime = scheduled.first { row ->
            val session = sessionsBySchedule[row.id] ?: return@first false
            row.cancelledAt == null &&
                row.originalScheduledDate == row.scheduledDate &&
                session.workoutDate == row.scheduledDate
        }
        val late = scheduled.first { row ->
            val session = sessionsBySchedule[row.id] ?: return@first false
            row.cancelledAt == null &&
                row.originalScheduledDate == row.scheduledDate &&
                LocalDate.parse(session.workoutDate).isAfter(LocalDate.parse(row.scheduledDate))
        }
        val missed = scheduled.first { row ->
            row.cancelledAt == null &&
                LocalDate.parse(row.scheduledDate).isBefore(today) &&
                sessionsBySchedule[row.id] == null
        }
        val cancelled = scheduled.first { it.cancelledAt != null && !LocalDate.parse(it.scheduledDate).isAfter(today) }
        val rescheduled = scheduled.first { row ->
            val session = sessionsBySchedule[row.id] ?: return@first false
            row.cancelledAt == null &&
                row.originalScheduledDate != row.scheduledDate &&
                session.workoutDate == row.scheduledDate
        }
        val futureCancelled = scheduled.first {
            it.cancelledAt != null && LocalDate.parse(it.scheduledDate).isAfter(today)
        }
        val unplanned = tables.workoutSessions.first { it.scheduledWorkoutId == null }
        return listOf(
            example("on-time", onTime),
            example("late", late),
            example("missed", missed),
            example("cancelled", cancelled),
            example("rescheduled", rescheduled),
            example("future-cancelled", futureCancelled),
            DemoScheduleExample(
                label = "unplanned",
                templateName = unplanned.templateName,
                originalScheduledDate = null,
                scheduledDate = null,
                workoutDate = LocalDate.parse(unplanned.workoutDate),
                cancelled = false
            )
        )
    }

    private fun buildCatalog(calendar: DemoCalendar): List<CatalogExercise> {
        val createdAt = epoch(calendar.historyStart, 8)
        return StarterCatalog.drafts.mapIndexed { index, draft ->
            val id = index + 1L
            val name = ExerciseNaming.displayName(draft.name)
            val muscles = buildList {
                add(
                    ExerciseMuscleEntity(
                        exerciseId = id,
                        muscleGroup = draft.primaryMuscle.name,
                        role = MuscleRole.PRIMARY.name
                    )
                )
                draft.secondaryMuscles.forEach { muscle ->
                    add(
                        ExerciseMuscleEntity(
                            exerciseId = id,
                            muscleGroup = muscle.name,
                            role = MuscleRole.SECONDARY.name
                        )
                    )
                }
            }
            CatalogExercise(
                id = id,
                draft = draft.copy(name = name),
                entity = ExerciseEntity(
                    id = id,
                    name = name,
                    normalizedName = ExerciseNaming.normalize(name),
                    category = draft.category.name,
                    movementPattern = draft.movementPattern.name,
                    measurementType = draft.measurementType.name,
                    resistanceBasis = draft.resistanceBasis.name,
                    weightInterpretation = draft.weightInterpretation.name,
                    notes = null,
                    archived = false,
                    createdAt = createdAt,
                    updatedAt = createdAt,
                    custom = false
                ),
                muscles = muscles
            )
        }
    }

    private fun buildTemplates(calendar: DemoCalendar, catalog: List<CatalogExercise>): TemplateBundle {
        val byName = catalog.associateBy { it.draft.name }
        val createdAt = epoch(calendar.historyStart, 8)
        val updatedAt = epoch(calendar.referenceDate, 18)
        val templateIds = Ids()
        val exerciseIds = Ids()
        val setIds = Ids()
        val templates = mutableListOf<WorkoutTemplateEntity>()
        val exercises = mutableListOf<WorkoutTemplateExerciseEntity>()
        val sets = mutableListOf<WorkoutTemplateSetEntity>()
        val ids = linkedMapOf<PlanKind, Long>()
        PlanKind.entries.forEach { plan ->
            val templateId = templateIds.next()
            ids[plan] = templateId
            val name = templateName(plan)
            templates += WorkoutTemplateEntity(
                id = templateId,
                name = name,
                normalizedName = TemplateNaming.normalize(name),
                notes = templateNotes(plan),
                archived = false,
                createdAt = createdAt,
                updatedAt = updatedAt
            )
            templateExerciseNames(plan).forEachIndexed { position, exerciseName ->
                val exercise = byName.getValue(exerciseName)
                val relationId = exerciseIds.next()
                exercises += WorkoutTemplateExerciseEntity(
                    id = relationId,
                    templateId = templateId,
                    exerciseId = exercise.id,
                    position = position,
                    notes = null,
                    createdAt = createdAt
                )
                repeat(4) { setPosition ->
                    sets += templateSet(setIds.next(), relationId, setPosition, exercise)
                }
            }
        }
        return TemplateBundle(templates, exercises, sets, ids)
    }

    private fun templateSet(
        id: Long,
        templateExerciseId: Long,
        position: Int,
        exercise: CatalogExercise
    ): WorkoutTemplateSetEntity {
        val draft = exercise.draft
        return when (draft.measurementType) {
            MeasurementType.DURATION -> WorkoutTemplateSetEntity(
                id = id,
                templateExerciseId = templateExerciseId,
                position = position,
                minReps = null,
                maxReps = null,
                loadKind = PlannedLoadKind.BODYWEIGHT_ONLY.name,
                weightKg = null,
                durationSeconds = 45,
                distanceMeters = null
            )
            MeasurementType.REPETITIONS -> {
                val added = draft.name == "Dip" || draft.name == "Pull-Up"
                WorkoutTemplateSetEntity(
                    id = id,
                    templateExerciseId = templateExerciseId,
                    position = position,
                    minReps = if (added) 5 else 6,
                    maxReps = if (added) 8 else 12,
                    loadKind = if (added) PlannedLoadKind.ADDED_WEIGHT.name else PlannedLoadKind.BODYWEIGHT_ONLY.name,
                    weightKg = if (added) 5.0 else null,
                    durationSeconds = null,
                    distanceMeters = null
                )
            }
            MeasurementType.REPETITIONS_AND_WEIGHT -> {
                val profile = loadProfiles.getValue(draft.name)
                WorkoutTemplateSetEntity(
                    id = id,
                    templateExerciseId = templateExerciseId,
                    position = position,
                    minReps = profile.baseReps,
                    maxReps = profile.baseReps + 2,
                    loadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                    weightKg = workingLoad(profile, ordinal = 48, regressing = false, lighter = false),
                    durationSeconds = null,
                    distanceMeters = null
                )
            }
            else -> error("Starter exercise ${draft.name} is not used by the demo plans")
        }
    }

    private fun buildWeights(calendar: DemoCalendar): List<WeightMeasurementEntity> {
        val totalDays = ChronoUnit.DAYS.between(calendar.historyStart, calendar.referenceDate)
            .toInt()
            .coerceAtLeast(1)
        val plateauStart = totalDays / 2
        val ids = Ids()
        val rows = mutableListOf<WeightMeasurementEntity>()
        var frozenTenths: Int? = null
        var day = 0
        var date = calendar.historyStart
        while (!date.isAfter(calendar.referenceDate)) {
            val measured = day % 2 == 0 && day % 11 != 0
            if (measured) {
                var tenths = 826 - (day * 40 / totalDays)
                if (day in plateauStart until plateauStart + 21) {
                    if (frozenTenths == null) frozenTenths = tenths
                    tenths = frozenTenths
                } else {
                    tenths += (day * 3 % 7) - 3
                }
                val stamp = epoch(date, 7)
                rows += WeightMeasurementEntity(
                    id = ids.next(),
                    date = date.toString(),
                    weightKg = tenths.coerceIn(760, 880) / 10.0,
                    createdAt = stamp,
                    updatedAt = stamp
                )
            }
            date = date.plusDays(1)
            day += 1
        }
        return rows
    }

    private data class BodySeriesShape(
        val type: BodyMeasurementType,
        val start: Double,
        val end: Double,
        val noise: Double,
        val everyDays: Long,
        val offsetDays: Long
    )

    private fun buildBodyMeasurements(calendar: DemoCalendar): List<BodyMeasurementEntity> {
        val shapes = listOf(
            BodySeriesShape(BodyMeasurementType.WAIST, 92.0, 84.5, 1.1, 14, 2),
            BodySeriesShape(BodyMeasurementType.BODY_FAT, 22.4, 16.8, 0.6, 21, 5),
            BodySeriesShape(BodyMeasurementType.CHEST, 98.0, 104.0, 0.8, 28, 8),
            BodySeriesShape(BodyMeasurementType.UPPER_ARM, 33.5, 37.0, 0.4, 18, 4),
            BodySeriesShape(BodyMeasurementType.THIGH, 58.0, 61.5, 0.7, 25, 11),
            BodySeriesShape(BodyMeasurementType.HIPS, 102.0, 96.5, 0.9, 16, 6)
        )
        val ids = Ids()
        val rows = mutableListOf<BodyMeasurementEntity>()
        shapes.forEach { shape ->
            val dates = linkedSetOf<LocalDate>()
            var date = calendar.historyStart.plusDays(shape.offsetDays)
            while (!date.isAfter(calendar.referenceDate)) {
                if (!inBreak(date, calendar)) dates += date
                date = date.plusDays(shape.everyDays)
            }
            listOf(calendar.referenceDate.minusDays(18), calendar.referenceDate.minusDays(2)).forEach { extra ->
                if (!extra.isBefore(calendar.historyStart) && !extra.isAfter(calendar.referenceDate) && !inBreak(extra, calendar)) {
                    dates += extra
                }
            }
            dates.sorted().forEach { sampleDate ->
                val stamp = epoch(sampleDate, 7)
                rows += BodyMeasurementEntity(
                    id = ids.next(),
                    type = shape.type.code,
                    date = sampleDate.toString(),
                    value = demoBodyValue(calendar, shape, sampleDate),
                    source = "MANUAL",
                    externalId = null,
                    createdAt = stamp,
                    updatedAt = stamp
                )
            }
        }
        return rows
    }

    private fun demoBodyValue(calendar: DemoCalendar, shape: BodySeriesShape, date: LocalDate): Double {
        val span = ChronoUnit.DAYS.between(calendar.historyStart, calendar.referenceDate).coerceAtLeast(1)
        val t = ChronoUnit.DAYS.between(calendar.historyStart, date).toDouble() / span.toDouble()
        val wobble = sin(t * 9.0 + shape.type.ordinal) * shape.noise
        val step = ((date.toEpochDay() % 5) - 2) * (shape.noise / 4.0)
        val raw = shape.start + (shape.end - shape.start) * t + wobble + step
        return BodyMeasurementParser.quantize(raw.coerceIn(shape.type.minimum, shape.type.maximum))
    }

    /**
     * The last seven weeks, measured from [DemoCalendar.referenceDate], are real
     * completed sessions and real schedules. The streak is not written down.
     *
     * Week offsets are Mondays: -6 success, -5 success, -4 success, -3 success,
     * -2 miss, -1 success, and the current week 2 completed plus 2 still planned.
     */
    private fun shapeWeeklyGoalScenario(
        training: TrainingRows,
        calendar: DemoCalendar,
        templates: TemplateBundle
    ): TrainingRows {
        val sessions = training.sessions.toMutableList()
        val exercises = training.sessionExercises.toMutableList()
        val muscles = training.sessionMuscles.toMutableList()
        val sets = training.sessionSets.toMutableList()
        val scheduled = training.scheduled.toMutableList()
        var nextSessionId = (sessions.maxOfOrNull { it.id } ?: 0L) + 1L
        var nextExerciseId = (exercises.maxOfOrNull { it.id } ?: 0L) + 1L
        var nextSetId = (sets.maxOfOrNull { it.id } ?: 0L) + 1L
        var nextScheduleId = (scheduled.maxOfOrNull { it.id } ?: 0L) + 1L
        val currentMonday = calendar.referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val historicalTargets = listOf(-6 to 4, -5 to 5, -4 to 4, -3 to 4, -2 to 2, -1 to 4)
        historicalTargets.forEach { (offset, target) ->
            val weekStart = currentMonday.plusWeeks(offset.toLong())
            shapeCompletedCount(
                weekStart = weekStart,
                target = target,
                mustInclude = null,
                calendar = calendar,
                sessions = sessions,
                exercises = exercises,
                muscles = muscles,
                sets = sets,
                scheduled = scheduled,
                nextSessionId = { nextSessionId++ },
                nextExerciseId = { nextExerciseId++ },
                nextSetId = { nextSetId++ }
            )
            cancelOpenPlans(weekStart, sessions, scheduled, calendar)
        }
        shapeCompletedCount(
            weekStart = currentMonday,
            target = 2,
            mustInclude = calendar.referenceDate,
            calendar = calendar,
            sessions = sessions,
            exercises = exercises,
            muscles = muscles,
            sets = sets,
            scheduled = scheduled,
            nextSessionId = { nextSessionId++ },
            nextExerciseId = { nextExerciseId++ },
            nextSetId = { nextSetId++ }
        )
        addAbandonedSample(
            weekStart = currentMonday.plusWeeks(-2),
            calendar = calendar,
            sessions = sessions,
            exercises = exercises,
            muscles = muscles,
            sets = sets,
            nextSessionId = { nextSessionId++ },
            nextExerciseId = { nextExerciseId++ },
            nextSetId = { nextSetId++ }
        )
        shapePlannedCount(
            weekStart = currentMonday,
            target = 2,
            calendar = calendar,
            templates = templates,
            sessions = sessions,
            scheduled = scheduled,
            nextScheduleId = { nextScheduleId++ }
        )
        val goalMonday = currentMonday.plusWeeks(-6)
        val createdAt = epoch(goalMonday, 8)
        val goals = listOf(
            WeeklyWorkoutGoalEntity(
                id = 1,
                effectiveWeekStart = goalMonday.toString(),
                workoutsPerWeek = 4,
                graceWeek = false,
                createdAt = createdAt,
                updatedAt = createdAt
            )
        )
        val counts = sessions
            .filter { it.status == SessionStatus.COMPLETED.name }
            .groupingBy { LocalDate.parse(it.workoutDate) }
            .eachCount()
        val status = WeeklyGoalLogic.evaluate(
            goals.map { row ->
                WeeklyGoalRevision(
                    effectiveWeekStart = LocalDate.parse(row.effectiveWeekStart),
                    workoutsPerWeek = row.workoutsPerWeek,
                    graceWeek = row.graceWeek
                )
            },
            counts,
            calendar.referenceDate
        )
        check(status.current.goal == 4 && status.current.completed == 2 && status.current.streak == 1) {
            "Current week should be 2/4 with streak 1, was ${status.current}"
        }
        check(status.current.verdict == WeekVerdict.InProgress)
        historicalTargets.forEach { (offset, target) ->
            val week = status.progressOn(currentMonday.plusWeeks(offset.toLong()))
            check(week.completed == target) { "Week $offset completed ${week.completed}, expected $target" }
        }
        check(status.progressOn(currentMonday.plusWeeks(-6)).streak == 1)
        check(status.progressOn(currentMonday.plusWeeks(-5)).streak == 2)
        check(status.progressOn(currentMonday.plusWeeks(-4)).streak == 3)
        check(status.progressOn(currentMonday.plusWeeks(-3)).streak == 4)
        check(status.progressOn(currentMonday.plusWeeks(-2)).verdict == WeekVerdict.Missed)
        check(status.progressOn(currentMonday.plusWeeks(-2)).streak == 0)
        check(status.progressOn(currentMonday.plusWeeks(-1)).streak == 1)
        return training.copy(
            scheduled = scheduled,
            sessions = sessions,
            sessionExercises = exercises,
            sessionMuscles = muscles,
            sessionSets = sets,
            goals = goals
        )
    }

    private fun shapeCompletedCount(
        weekStart: LocalDate,
        target: Int,
        mustInclude: LocalDate?,
        calendar: DemoCalendar,
        sessions: MutableList<WorkoutSessionEntity>,
        exercises: MutableList<WorkoutSessionExerciseEntity>,
        muscles: MutableList<WorkoutSessionExerciseMuscleEntity>,
        sets: MutableList<WorkoutSessionSetEntity>,
        scheduled: MutableList<ScheduledWorkoutEntity>,
        nextSessionId: () -> Long,
        nextExerciseId: () -> Long,
        nextSetId: () -> Long
    ) {
        val weekEnd = weekStart.plusDays(6)
        fun inWeek(date: LocalDate) = !date.isBefore(weekStart) && !date.isAfter(weekEnd)
        fun completedInWeek() = sessions.filter {
            it.status == SessionStatus.COMPLETED.name && inWeek(LocalDate.parse(it.workoutDate))
        }
        val chosen = linkedSetOf<Long>()
        val byDate = completedInWeek().groupBy { LocalDate.parse(it.workoutDate) }
        if (mustInclude != null) {
            byDate[mustInclude].orEmpty().minByOrNull { it.id }?.let { chosen += it.id }
        }
        val eligibleDays = (0L..6L).map { weekStart.plusDays(it) }
            .filter { !it.isAfter(calendar.referenceDate) }
        for (date in eligibleDays) {
            if (chosen.size == target) break
            byDate[date].orEmpty().filter { it.id !in chosen }.minByOrNull { it.id }?.let { chosen += it.id }
        }
        for (date in eligibleDays) {
            if (chosen.size == target) break
            byDate[date].orEmpty().filter { it.id !in chosen }.forEach { extra ->
                if (chosen.size < target) chosen += extra.id
            }
        }
        completedInWeek().filter { it.id !in chosen }.forEach { extra ->
            dropCompletedSession(extra, sessions, exercises, muscles, sets, scheduled, calendar)
        }
        if (mustInclude != null && completedInWeek().none { LocalDate.parse(it.workoutDate) == mustInclude }) {
            val source = sessions.first { it.status == SessionStatus.COMPLETED.name }
            addCopiedSession(
                source = source,
                date = mustInclude,
                status = SessionStatus.COMPLETED,
                sessions = sessions,
                exercises = exercises,
                muscles = muscles,
                sets = sets,
                nextSessionId = nextSessionId,
                nextExerciseId = nextExerciseId,
                nextSetId = nextSetId
            )
        }
        while (completedInWeek().size > target) {
            val extra = completedInWeek().first { LocalDate.parse(it.workoutDate) != mustInclude }
            dropCompletedSession(extra, sessions, exercises, muscles, sets, scheduled, calendar)
        }
        while (completedInWeek().size < target) {
            val occupied = completedInWeek().map { LocalDate.parse(it.workoutDate) }.toSet()
            val date = eligibleDays.firstOrNull { it !in occupied } ?: eligibleDays.first()
            val source = sessions.first { it.status == SessionStatus.COMPLETED.name }
            addCopiedSession(
                source = source,
                date = date,
                status = SessionStatus.COMPLETED,
                sessions = sessions,
                exercises = exercises,
                muscles = muscles,
                sets = sets,
                nextSessionId = nextSessionId,
                nextExerciseId = nextExerciseId,
                nextSetId = nextSetId
            )
        }
    }

    private fun addAbandonedSample(
        weekStart: LocalDate,
        calendar: DemoCalendar,
        sessions: MutableList<WorkoutSessionEntity>,
        exercises: MutableList<WorkoutSessionExerciseEntity>,
        muscles: MutableList<WorkoutSessionExerciseMuscleEntity>,
        sets: MutableList<WorkoutSessionSetEntity>,
        nextSessionId: () -> Long,
        nextExerciseId: () -> Long,
        nextSetId: () -> Long
    ) {
        val date = (0L..6L).map { weekStart.plusDays(it) }
            .last { !it.isAfter(calendar.referenceDate) }
        val source = sessions.first { it.status == SessionStatus.COMPLETED.name }
        addCopiedSession(
            source = source,
            date = date,
            status = SessionStatus.ABANDONED,
            sessions = sessions,
            exercises = exercises,
            muscles = muscles,
            sets = sets,
            nextSessionId = nextSessionId,
            nextExerciseId = nextExerciseId,
            nextSetId = nextSetId
        )
    }

    private fun cancelOpenPlans(
        weekStart: LocalDate,
        sessions: List<WorkoutSessionEntity>,
        scheduled: MutableList<ScheduledWorkoutEntity>,
        calendar: DemoCalendar
    ) {
        val weekEnd = weekStart.plusDays(6)
        val completedScheduleIds = sessions
            .filter { it.status == SessionStatus.COMPLETED.name }
            .mapNotNull { it.scheduledWorkoutId }
            .toSet()
        scheduled.indices.forEach { index ->
            val row = scheduled[index]
            val date = LocalDate.parse(row.scheduledDate)
            if (row.cancelledAt == null &&
                !date.isBefore(weekStart) &&
                !date.isAfter(weekEnd) &&
                row.id !in completedScheduleIds
            ) {
                scheduled[index] = row.copy(cancelledAt = epoch(calendar.referenceDate, 21))
            }
        }
    }

    private fun shapePlannedCount(
        weekStart: LocalDate,
        target: Int,
        calendar: DemoCalendar,
        templates: TemplateBundle,
        sessions: List<WorkoutSessionEntity>,
        scheduled: MutableList<ScheduledWorkoutEntity>,
        nextScheduleId: () -> Long
    ) {
        val weekEnd = weekStart.plusDays(6)
        val completedScheduleIds = sessions
            .filter { it.status == SessionStatus.COMPLETED.name }
            .mapNotNull { it.scheduledWorkoutId }
            .toSet()
        fun isOpen(row: ScheduledWorkoutEntity): Boolean {
            val date = LocalDate.parse(row.scheduledDate)
            return row.cancelledAt == null &&
                !date.isBefore(weekStart) &&
                !date.isAfter(weekEnd) &&
                row.id !in completedScheduleIds
        }
        val openIndexes = scheduled.indices.filter { isOpen(scheduled[it]) }
        val ranked = openIndexes.sortedWith(
            compareBy<Int> { index ->
                val date = LocalDate.parse(scheduled[index].scheduledDate)
                if (date.isAfter(calendar.referenceDate)) 0 else 1
            }.thenBy { index -> scheduled[index].scheduledDate }
        )
        val distinctDays = ranked.distinctBy { scheduled[it].scheduledDate }
        val keep = (if (distinctDays.size >= target) distinctDays.take(target) else ranked.take(target)).toSet()
        ranked.filter { it !in keep }.forEach { index ->
            val row = scheduled[index]
            scheduled[index] = row.copy(cancelledAt = epoch(LocalDate.parse(row.scheduledDate), 21))
        }
        var missing = target - keep.size
        val usedDates = keep.map { LocalDate.parse(scheduled[it].scheduledDate) }.toMutableSet()
        val completedDates = sessions
            .filter {
                it.status == SessionStatus.COMPLETED.name &&
                    !LocalDate.parse(it.workoutDate).isBefore(weekStart) &&
                    !LocalDate.parse(it.workoutDate).isAfter(weekEnd)
            }
            .map { LocalDate.parse(it.workoutDate) }
            .toSet()
        val candidates = (0L..6L).map { weekStart.plusDays(it) }.sortedBy { date ->
            when {
                date.isAfter(calendar.referenceDate) -> 0
                date !in completedDates -> 1
                else -> 2
            }
        }
        for (date in candidates) {
            if (missing == 0) break
            if (date in usedDates) continue
            val template = templates.ids.entries.firstOrNull { (_, templateId) ->
                scheduled.none {
                    it.cancelledAt == null &&
                        it.scheduledDate == date.toString() &&
                        it.templateId == templateId
                }
            } ?: continue
            scheduled += ScheduledWorkoutEntity(
                id = nextScheduleId(),
                scheduledDate = date.toString(),
                originalScheduledDate = date.toString(),
                templateId = template.value,
                templateName = templateName(template.key),
                createdAt = epoch(calendar.referenceDate, 8),
                cancelledAt = null
            )
            usedDates += date
            missing -= 1
        }
        check(missing == 0) { "Could not place $target planned workouts in the current week" }
    }

    private fun dropCompletedSession(
        session: WorkoutSessionEntity,
        sessions: MutableList<WorkoutSessionEntity>,
        exercises: MutableList<WorkoutSessionExerciseEntity>,
        muscles: MutableList<WorkoutSessionExerciseMuscleEntity>,
        sets: MutableList<WorkoutSessionSetEntity>,
        scheduled: MutableList<ScheduledWorkoutEntity>,
        calendar: DemoCalendar
    ) {
        val exerciseIds = exercises.filter { it.sessionId == session.id }.map { it.id }.toSet()
        sets.removeAll { it.sessionExerciseId in exerciseIds }
        muscles.removeAll { it.sessionExerciseId in exerciseIds }
        exercises.removeAll { it.sessionId == session.id }
        sessions.removeAll { it.id == session.id }
        val scheduleId = session.scheduledWorkoutId ?: return
        val index = scheduled.indexOfFirst { it.id == scheduleId }
        if (index >= 0 && scheduled[index].cancelledAt == null) {
            scheduled[index] = scheduled[index].copy(cancelledAt = epoch(calendar.referenceDate, 21))
        }
    }

    private fun addCopiedSession(
        source: WorkoutSessionEntity,
        date: LocalDate,
        status: SessionStatus,
        sessions: MutableList<WorkoutSessionEntity>,
        exercises: MutableList<WorkoutSessionExerciseEntity>,
        muscles: MutableList<WorkoutSessionExerciseMuscleEntity>,
        sets: MutableList<WorkoutSessionSetEntity>,
        nextSessionId: () -> Long,
        nextExerciseId: () -> Long,
        nextSetId: () -> Long
    ) {
        val sessionId = nextSessionId()
        val startedAt = epoch(date, WORKOUT_START_HOUR)
        val endedAt = startedAt + 45L * 60_000L
        sessions += source.copy(
            id = sessionId,
            clientWorkoutId = demoClientWorkoutId(sessionId),
            status = status.name,
            workoutDate = date.toString(),
            startedAt = startedAt,
            finishedAt = if (status == SessionStatus.COMPLETED) endedAt else null,
            abandonedAt = if (status == SessionStatus.ABANDONED) endedAt else null,
            notes = if (status == SessionStatus.ABANDONED) "Abandoned" else source.notes,
            createdAt = endedAt,
            updatedAt = endedAt,
            activeLock = null,
            importFingerprint = null,
            scheduledWorkoutId = null
        )
        val copiedExercises = exercises.filter { it.sessionId == source.id }
        copiedExercises.forEach { exercise ->
            val exerciseId = nextExerciseId()
            val copiedSets = sets.filter { it.sessionExerciseId == exercise.id }
            exercises += exercise.copy(id = exerciseId, sessionId = sessionId)
            muscles += muscles.filter { it.sessionExerciseId == exercise.id }.map { muscle ->
                muscle.copy(sessionExerciseId = exerciseId)
            }
            sets += copiedSets.map { set ->
                set.copy(
                    id = nextSetId(),
                    sessionExerciseId = exerciseId,
                    status = if (status == SessionStatus.ABANDONED) {
                        SessionSetStatus.PENDING.name
                    } else {
                        set.status
                    },
                    completedAt = if (status == SessionStatus.COMPLETED) endedAt else null
                )
            }
        }
    }

    private fun buildSlots(calendar: DemoCalendar): List<PlannedSlot> {
        val horizon = calendar.referenceDate.plusDays(9)
        val occurrences = mutableMapOf<PlanKind, Int>()
        val slots = mutableListOf<PlannedSlot>()
        var weekStart = calendar.historyStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var weekIndex = 0
        while (!weekStart.isAfter(horizon)) {
            val phase = phaseOf(weekStart, calendar.referenceDate)
            val lighterWeek = weekIndex % 10 == 4 || weekStart == recentLighterMonday(calendar.referenceDate)
            var days = planDays(phase, weekIndex)
                .map { (day, plan) -> weekStart.with(day) to plan }
                .sortedBy { it.first }
            if (fewerSessions(weekStart, weekIndex, calendar) && days.size > 3) {
                val last = days.last()
                if (last.first != calendar.referenceDate) {
                    days = days.dropLast(1)
                }
            }
            days.forEach { (date, plan) ->
                if (date.isBefore(calendar.historyStart) || date.isAfter(horizon)) return@forEach
                val occurrence = occurrences.getOrDefault(plan, 0)
                occurrences[plan] = occurrence + 1
                val lighter = lighterWeek || inReturn(date, calendar)
                slots += PlannedSlot(
                    date = date,
                    plan = plan,
                    weekIndex = weekIndex,
                    phase = phase,
                    lighter = lighter,
                    peakVolume = phase == Phase.PEAK && weekIndex % 7 == 2 && !lighter,
                    occurrence = occurrence
                )
            }
            weekStart = weekStart.plusWeeks(1)
            weekIndex += 1
        }
        return slots
    }

    private fun assignOutcomes(slots: List<PlannedSlot>, calendar: DemoCalendar): List<Outcome> {
        val lastFuture = slots.indexOfLast { it.date.isAfter(calendar.referenceDate) }
        val preliminary = slots.mapIndexed { index, slot ->
            val fate = when {
                slot.date.isAfter(calendar.referenceDate) && index == lastFuture -> Fate.FUTURE_CANCELLED
                slot.date.isAfter(calendar.referenceDate) -> Fate.FUTURE
                inBreak(slot.date, calendar) -> Fate.CANCELLED
                slot.date != calendar.referenceDate &&
                    !inReturn(slot.date, calendar) &&
                    index % 29 == 0 -> Fate.CANCELLED
                slot.date != calendar.referenceDate &&
                    !inReturn(slot.date, calendar) &&
                    index % 8 == 1 -> Fate.MISSED
                index % 7 == 3 -> Fate.LATE
                index % 11 == 5 && slot.date != calendar.referenceDate -> Fate.RESCHEDULED
                else -> Fate.ON_TIME
            }
            val workoutDate = when (fate) {
                Fate.ON_TIME -> slot.date
                Fate.LATE -> lateWorkoutDate(slot.date, index, calendar)
                else -> null
            }
            val resolvedFate = if (fate == Fate.LATE && workoutDate == null) Fate.ON_TIME else fate
            Outcome(
                slot = slot,
                fate = resolvedFate,
                scheduledDate = slot.date,
                workoutDate = if (resolvedFate == Fate.ON_TIME) slot.date else workoutDate
            )
        }
        val occupied = preliminary
            .filter { it.fate != Fate.RESCHEDULED }
            .map { it.scheduledDate to it.slot.plan }
            .toMutableSet()
        return preliminary.map { outcome ->
            if (outcome.fate != Fate.RESCHEDULED) return@map outcome
            val target = (1L..3L)
                .map { outcome.slot.date.plusDays(it) }
                .firstOrNull { candidate ->
                    !candidate.isAfter(calendar.referenceDate) &&
                        !inBreak(candidate, calendar) &&
                        (candidate to outcome.slot.plan) !in occupied
                }
            if (target == null) {
                occupied += outcome.slot.date to outcome.slot.plan
                outcome.copy(fate = Fate.ON_TIME, workoutDate = outcome.slot.date)
            } else {
                occupied += target to outcome.slot.plan
                outcome.copy(scheduledDate = target, workoutDate = target)
            }
        }
    }

    private fun buildTraining(
        outcomes: List<Outcome>,
        calendar: DemoCalendar,
        catalog: List<CatalogExercise>,
        templates: TemplateBundle,
        weights: List<WeightMeasurementEntity>
    ): TrainingRows {
        val byName = catalog.associateBy { it.draft.name }
        val scheduledIds = Ids()
        val sessionIds = Ids()
        val sessionExerciseIds = Ids()
        val setIds = Ids()
        val scheduled = mutableListOf<ScheduledWorkoutEntity>()
        val scheduledIdByOutcome = linkedMapOf<Outcome, Long>()
        outcomes.forEach { outcome ->
            val id = scheduledIds.next()
            scheduledIdByOutcome[outcome] = id
            val planningDate = if (outcome.slot.date.isAfter(calendar.referenceDate)) {
                calendar.referenceDate
            } else {
                outcome.slot.date
            }
            val cancelled = outcome.fate == Fate.CANCELLED || outcome.fate == Fate.FUTURE_CANCELLED
            scheduled += ScheduledWorkoutEntity(
                id = id,
                scheduledDate = outcome.scheduledDate.toString(),
                originalScheduledDate = outcome.slot.date.toString(),
                templateId = templates.ids.getValue(outcome.slot.plan),
                templateName = templateName(outcome.slot.plan),
                createdAt = epoch(planningDate, 8),
                cancelledAt = if (cancelled) epoch(planningDate, 9) else null
            )
        }
        val liveDates = outcomes
            .filter { it.fate != Fate.CANCELLED && it.fate != Fate.FUTURE_CANCELLED }
            .map { it.scheduledDate }
            .toSet()
        val events = mutableListOf<TrainingEvent>()
        outcomes.forEach { outcome ->
            val workoutDate = outcome.workoutDate ?: return@forEach
            events += TrainingEvent(workoutDate, outcome, unplannedPlan = null)
        }
        unplannedDates(calendar).forEach { (date, plan) ->
            if (date !in liveDates) {
                events += TrainingEvent(date, outcome = null, unplannedPlan = plan)
            }
        }
        events.sortWith(compareBy({ it.date }, { if (it.outcome != null) 0 else 1 }))
        val sessions = mutableListOf<WorkoutSessionEntity>()
        val sessionExercises = mutableListOf<WorkoutSessionExerciseEntity>()
        val sessionMuscles = mutableListOf<WorkoutSessionExerciseMuscleEntity>()
        val sessionSets = mutableListOf<WorkoutSessionSetEntity>()
        val ordinals = mutableMapOf<String, Int>()
        val weightCursor = WeightCursor(weights)
        var notedReturn = false
        events.forEach { event ->
            check(!inBreak(event.date, calendar)) { "Completed workout falls inside the break: ${event.date}" }
            check(!event.date.isAfter(calendar.referenceDate)) { "Completed workout is in the future: ${event.date}" }
            val plan = event.outcome?.slot?.plan ?: event.unplannedPlan!!
            val names = if (event.outcome == null) {
                templateExerciseNames(plan)
            } else {
                sessionExerciseNames(plan, event.outcome.slot.occurrence)
            }
            val regressing = inReturn(event.date, calendar)
            val lighter = event.outcome?.slot?.lighter == true || regressing
            val peakVolume = event.outcome?.slot?.peakVolume == true
            val startedAt = epoch(event.date, WORKOUT_START_HOUR)
            val durationMinutes = 46L + names.size * 4L + (event.date.dayOfMonth % 5) * 2L
            val finishedAt = startedAt + durationMinutes * 60_000L
            val measurement = weightCursor.at(event.date)
            val notes = when {
                !notedReturn && regressing -> {
                    notedReturn = true
                    "First session back after a break"
                }
                event.outcome == null -> "Unplanned"
                else -> null
            }
            val sessionId = sessionIds.next()
            sessions += WorkoutSessionEntity(
                id = sessionId,
                templateId = templates.ids.getValue(plan),
                templateName = templateName(plan),
                status = SessionStatus.COMPLETED.name,
                workoutDate = event.date.toString(),
                startedAt = startedAt,
                finishedAt = finishedAt,
                abandonedAt = null,
                notes = notes,
                bodyWeightKg = measurement?.weightKg,
                bodyWeightSource = when {
                    measurement == null -> BodyWeightSource.UNKNOWN.name
                    measurement.date == event.date.toString() -> BodyWeightSource.MEASURED_SAME_DAY.name
                    else -> BodyWeightSource.NEAREST_PREVIOUS_MEASUREMENT.name
                },
                bodyWeightSourceDate = measurement?.date,
                createdAt = finishedAt,
                updatedAt = finishedAt,
                activeLock = null,
                importFingerprint = null,
                scheduledWorkoutId = event.outcome?.let { scheduledIdByOutcome.getValue(it) },
                clientWorkoutId = demoClientWorkoutId(sessionId)
            )
            names.forEachIndexed { exerciseIndex, name ->
                val exercise = byName.getValue(name)
                val ordinal = ordinals.getOrDefault(name, 0)
                ordinals[name] = ordinal + 1
                val sessionExerciseId = sessionExerciseIds.next()
                sessionExercises += sessionExercise(sessionExerciseId, sessionId, exerciseIndex, exercise)
                sessionMuscles += exercise.muscles.map { muscle ->
                    WorkoutSessionExerciseMuscleEntity(
                        sessionExerciseId = sessionExerciseId,
                        muscleGroup = muscle.muscleGroup,
                        role = muscle.role
                    )
                }
                sessionSets += performedSets(
                    setIds = setIds,
                    sessionExerciseId = sessionExerciseId,
                    exercise = exercise,
                    ordinal = ordinal,
                    lighter = lighter,
                    regressing = regressing,
                    peakVolume = peakVolume,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    exerciseIndex = exerciseIndex
                )
            }
        }
        return TrainingRows(scheduled, sessions, sessionExercises, sessionMuscles, sessionSets)
    }

    private fun sessionExercise(
        id: Long,
        sessionId: Long,
        position: Int,
        exercise: CatalogExercise
    ): WorkoutSessionExerciseEntity {
        val draft = exercise.draft
        return WorkoutSessionExerciseEntity(
            id = id,
            sessionId = sessionId,
            exerciseId = exercise.id,
            position = position,
            name = draft.name,
            category = draft.category.name,
            movementPattern = draft.movementPattern.name,
            measurementType = draft.measurementType.name,
            resistanceBasis = draft.resistanceBasis.name,
            weightInterpretation = draft.weightInterpretation.name,
            primaryMuscle = draft.primaryMuscle.name,
            notes = null
        )
    }

    private fun performedSets(
        setIds: Ids,
        sessionExerciseId: Long,
        exercise: CatalogExercise,
        ordinal: Int,
        lighter: Boolean,
        regressing: Boolean,
        peakVolume: Boolean,
        startedAt: Long,
        finishedAt: Long,
        exerciseIndex: Int
    ): List<WorkoutSessionSetEntity> {
        val draft = exercise.draft
        val setCount = when {
            lighter || regressing -> 3
            peakVolume && draft.name in compoundNames -> 5
            else -> 4
        }
        val skipping = ordinal % 17 == 0 && setCount >= 4
        val adding = ordinal % 23 == 0 && !lighter && !regressing && !skipping &&
            draft.measurementType != MeasurementType.DURATION
        val rows = mutableListOf<WorkoutSessionSetEntity>()
        repeat(setCount) { index ->
            val skipped = skipping && index == setCount - 1
            rows += when (draft.measurementType) {
                MeasurementType.DURATION -> durationSet(
                    id = setIds.next(),
                    sessionExerciseId = sessionExerciseId,
                    position = index,
                    seconds = (plankSeconds(ordinal, regressing, lighter) - index * 2).coerceAtLeast(15),
                    skipped = skipped,
                    completedAt = stamp(startedAt, finishedAt, exerciseIndex, index),
                    addedDuringWorkout = false
                )
                MeasurementType.REPETITIONS -> {
                    val added = addedWeightKg(draft.name, ordinal)
                    val useAdded = added != null && index == setCount - 1 && !skipped
                    repetitionSet(
                        id = setIds.next(),
                        sessionExerciseId = sessionExerciseId,
                        position = index,
                        reps = (
                            bodyweightReps(draft.name, ordinal, regressing, lighter) -
                                fatigue(index) -
                                if (useAdded) 2 else 0
                            ).coerceAtLeast(3),
                        loadKind = if (useAdded) PlannedLoadKind.ADDED_WEIGHT else PlannedLoadKind.BODYWEIGHT_ONLY,
                        weightKg = if (useAdded) added else null,
                        skipped = skipped,
                        completedAt = stamp(startedAt, finishedAt, exerciseIndex, index),
                        addedDuringWorkout = false
                    )
                }
                MeasurementType.REPETITIONS_AND_WEIGHT -> {
                    val profile = loadProfiles.getValue(draft.name)
                    val load = workingLoad(profile, ordinal, regressing, lighter)
                    val backoff = index == setCount - 1 && ordinal % 4 == 1
                    val setLoad = if (backoff) {
                        (load - profile.increment).coerceAtLeast(profile.increment)
                    } else {
                        load
                    }
                    weightedSet(
                        id = setIds.next(),
                        sessionExerciseId = sessionExerciseId,
                        position = index,
                        reps = (weightedReps(profile, ordinal, regressing, lighter) - fatigue(index)).coerceAtLeast(3),
                        weightKg = roundToIncrement(setLoad, profile.increment),
                        skipped = skipped,
                        completedAt = stamp(startedAt, finishedAt, exerciseIndex, index),
                        addedDuringWorkout = false
                    )
                }
                else -> error("Unsupported measurement for ${draft.name}")
            }
        }
        if (adding) {
            val position = setCount
            rows += when (draft.measurementType) {
                MeasurementType.REPETITIONS -> repetitionSet(
                    id = setIds.next(),
                    sessionExerciseId = sessionExerciseId,
                    position = position,
                    reps = (bodyweightReps(draft.name, ordinal, regressing, lighter) - 1).coerceAtLeast(3),
                    loadKind = PlannedLoadKind.BODYWEIGHT_ONLY,
                    weightKg = null,
                    skipped = false,
                    completedAt = stamp(startedAt, finishedAt, exerciseIndex, position),
                    addedDuringWorkout = true
                )
                MeasurementType.REPETITIONS_AND_WEIGHT -> {
                    val profile = loadProfiles.getValue(draft.name)
                    val load = workingLoad(profile, ordinal, regressing, lighter)
                    weightedSet(
                        id = setIds.next(),
                        sessionExerciseId = sessionExerciseId,
                        position = position,
                        reps = (weightedReps(profile, ordinal, regressing, lighter) - 1).coerceAtLeast(3),
                        weightKg = roundToIncrement(
                            (load - profile.increment).coerceAtLeast(profile.increment),
                            profile.increment
                        ),
                        skipped = false,
                        completedAt = stamp(startedAt, finishedAt, exerciseIndex, position),
                        addedDuringWorkout = true
                    )
                }
                else -> error("Extra set is not used for ${draft.name}")
            }
        }
        return rows
    }

    private fun durationSet(
        id: Long,
        sessionExerciseId: Long,
        position: Int,
        seconds: Int,
        skipped: Boolean,
        completedAt: Long,
        addedDuringWorkout: Boolean
    ): WorkoutSessionSetEntity {
        return WorkoutSessionSetEntity(
            id = id,
            sessionExerciseId = sessionExerciseId,
            position = position,
            plannedMinReps = null,
            plannedMaxReps = null,
            plannedLoadKind = PlannedLoadKind.BODYWEIGHT_ONLY.name,
            plannedWeightKg = null,
            plannedDurationSeconds = seconds,
            plannedDistanceMeters = null,
            actualReps = null,
            actualLoadKind = if (skipped) null else PlannedLoadKind.BODYWEIGHT_ONLY.name,
            actualWeightKg = null,
            actualDurationSeconds = if (skipped) null else seconds,
            actualDistanceMeters = null,
            status = if (skipped) SessionSetStatus.SKIPPED.name else SessionSetStatus.COMPLETED.name,
            completedAt = if (skipped) null else completedAt,
            addedDuringWorkout = addedDuringWorkout
        )
    }

    private fun repetitionSet(
        id: Long,
        sessionExerciseId: Long,
        position: Int,
        reps: Int,
        loadKind: PlannedLoadKind,
        weightKg: Double?,
        skipped: Boolean,
        completedAt: Long,
        addedDuringWorkout: Boolean
    ): WorkoutSessionSetEntity {
        return WorkoutSessionSetEntity(
            id = id,
            sessionExerciseId = sessionExerciseId,
            position = position,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = loadKind.name,
            plannedWeightKg = weightKg,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = if (skipped) null else reps,
            actualLoadKind = if (skipped) null else loadKind.name,
            actualWeightKg = if (skipped) null else weightKg,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = if (skipped) SessionSetStatus.SKIPPED.name else SessionSetStatus.COMPLETED.name,
            completedAt = if (skipped) null else completedAt,
            addedDuringWorkout = addedDuringWorkout
        )
    }

    private fun weightedSet(
        id: Long,
        sessionExerciseId: Long,
        position: Int,
        reps: Int,
        weightKg: Double,
        skipped: Boolean,
        completedAt: Long,
        addedDuringWorkout: Boolean
    ): WorkoutSessionSetEntity {
        return WorkoutSessionSetEntity(
            id = id,
            sessionExerciseId = sessionExerciseId,
            position = position,
            plannedMinReps = reps,
            plannedMaxReps = reps,
            plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
            plannedWeightKg = weightKg,
            plannedDurationSeconds = null,
            plannedDistanceMeters = null,
            actualReps = if (skipped) null else reps,
            actualLoadKind = if (skipped) null else PlannedLoadKind.EXTERNAL_WEIGHT.name,
            actualWeightKg = if (skipped) null else weightKg,
            actualDurationSeconds = null,
            actualDistanceMeters = null,
            status = if (skipped) SessionSetStatus.SKIPPED.name else SessionSetStatus.COMPLETED.name,
            completedAt = if (skipped) null else completedAt,
            addedDuringWorkout = addedDuringWorkout
        )
    }

    private fun unplannedDates(calendar: DemoCalendar): List<Pair<LocalDate, PlanKind>> {
        val dates = mutableListOf<Pair<LocalDate, PlanKind>>()
        var weekStart = calendar.historyStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var weekIndex = 0
        while (!weekStart.isAfter(calendar.referenceDate)) {
            val sunday = weekStart.plusDays(6)
            if (weekIndex % 6 == 2 &&
                !sunday.isBefore(calendar.historyStart) &&
                !sunday.isAfter(calendar.referenceDate) &&
                !inBreak(sunday, calendar)
            ) {
                val plan = if (weekIndex % 2 == 0) PlanKind.PUSH else PlanKind.PULL
                dates += sunday to plan
            }
            weekStart = weekStart.plusWeeks(1)
            weekIndex += 1
        }
        return dates
    }

    private fun lateWorkoutDate(original: LocalDate, index: Int, calendar: DemoCalendar): LocalDate? {
        val desired = (index % 3) + 1
        for (delay in desired downTo 1) {
            val candidate = original.plusDays(delay.toLong())
            if (candidate.isAfter(calendar.referenceDate) || inBreak(candidate, calendar)) continue
            return candidate
        }
        return null
    }

    private fun phaseOf(weekStart: LocalDate, reference: LocalDate): Phase {
        val buildStart = reference.minusYears(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val peakStart = reference.minusMonths(6).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return when {
            weekStart < buildStart -> Phase.BASE
            weekStart < peakStart -> Phase.BUILD
            else -> Phase.PEAK
        }
    }

    private fun fewerSessions(weekStart: LocalDate, weekIndex: Int, calendar: DemoCalendar): Boolean {
        if (weekIndex % 8 != 5) return false
        val weekEnd = weekStart.plusDays(6)
        val windowEnd = calendar.returnEnd
        return weekEnd.isBefore(calendar.breakStart) || weekStart.isAfter(windowEnd)
    }

    private fun recentLighterMonday(reference: LocalDate): LocalDate {
        return reference.minusDays(16).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    private fun inBreak(date: LocalDate, calendar: DemoCalendar): Boolean {
        return !date.isBefore(calendar.breakStart) && !date.isAfter(calendar.breakEnd)
    }

    private fun inReturn(date: LocalDate, calendar: DemoCalendar): Boolean {
        val start = calendar.breakEnd.plusDays(1)
        return !date.isBefore(start) && !date.isAfter(calendar.returnEnd)
    }

    private fun planDays(phase: Phase, weekIndex: Int): List<Pair<DayOfWeek, PlanKind>> {
        return when (phase) {
            Phase.BASE -> listOf(
                DayOfWeek.MONDAY to PlanKind.PUSH,
                DayOfWeek.WEDNESDAY to PlanKind.PULL,
                DayOfWeek.FRIDAY to PlanKind.LEGS
            )
            Phase.BUILD -> listOf(
                DayOfWeek.MONDAY to PlanKind.PUSH,
                DayOfWeek.TUESDAY to PlanKind.PULL,
                DayOfWeek.THURSDAY to PlanKind.LEGS,
                DayOfWeek.SATURDAY to if (weekIndex % 2 == 0) PlanKind.PUSH else PlanKind.PULL
            )
            Phase.PEAK -> buildList {
                add(DayOfWeek.MONDAY to PlanKind.PUSH)
                add(DayOfWeek.TUESDAY to PlanKind.PULL)
                add(DayOfWeek.THURSDAY to PlanKind.LEGS)
                if (weekIndex % 4 == 0) {
                    add(DayOfWeek.FRIDAY to if (weekIndex % 8 == 0) PlanKind.PUSH else PlanKind.PULL)
                }
                add(DayOfWeek.SATURDAY to if (weekIndex % 2 == 0) PlanKind.PUSH else PlanKind.PULL)
            }
        }
    }

    private fun templateExerciseNames(plan: PlanKind): List<String> {
        return when (plan) {
            PlanKind.PUSH -> listOf(
                "Bench Press",
                "Overhead Press",
                "Dip",
                "Lateral Raise",
                "Triceps Extension",
                "Push-Up"
            )
            PlanKind.PULL -> listOf(
                "Pull-Up",
                "Barbell Row",
                "Dumbbell Row",
                "Face Pull",
                "Biceps Curl",
                "Lat Pulldown"
            )
            PlanKind.LEGS -> listOf(
                "Squat",
                "Romanian Deadlift",
                "Hip Thrust",
                "Leg Curl",
                "Calf Raise",
                "Plank"
            )
        }
    }

    private fun sessionExerciseNames(plan: PlanKind, occurrence: Int): List<String> {
        return when (plan) {
            PlanKind.PUSH -> templateExerciseNames(plan)
            PlanKind.PULL -> templateExerciseNames(plan).dropLast(1) +
                if (occurrence % 3 == 2) "Chin-Up" else "Lat Pulldown"
            PlanKind.LEGS -> listOf(
                "Squat",
                if (occurrence % 4 == 3) "Deadlift" else "Romanian Deadlift",
                "Hip Thrust",
                if (occurrence % 5 == 4) "Lunge" else "Leg Curl",
                if (occurrence % 3 == 1) "Leg Press" else "Calf Raise",
                "Plank"
            )
        }
    }

    private fun templateName(plan: PlanKind): String {
        return when (plan) {
            PlanKind.PUSH -> "Push"
            PlanKind.PULL -> "Pull"
            PlanKind.LEGS -> "Legs"
        }
    }

    private fun templateNotes(plan: PlanKind): String {
        return when (plan) {
            PlanKind.PUSH -> "Pressing and triceps"
            PlanKind.PULL -> "Pulling and biceps"
            PlanKind.LEGS -> "Lower body and core"
        }
    }

    private fun workingLoad(
        profile: LoadProfile,
        ordinal: Int,
        regressing: Boolean,
        lighter: Boolean
    ): Double {
        var kilograms = profile.startKg + (ordinal / 6) * profile.stepKg
        if (ordinal % 6 == 5) kilograms -= profile.stepKg
        if (regressing) kilograms -= profile.stepKg * 2
        if (lighter) kilograms -= profile.stepKg
        val floor = (profile.startKg - profile.stepKg * 2).coerceAtLeast(profile.increment)
        return roundToIncrement(kilograms.coerceIn(floor, profile.maxKg), profile.increment)
    }

    private fun weightedReps(profile: LoadProfile, ordinal: Int, regressing: Boolean, lighter: Boolean): Int {
        var reps = profile.baseReps + ordinal / 10
        reps += repNoise(ordinal)
        if (regressing) reps -= 2
        if (lighter) reps -= 1
        return reps.coerceIn(3, 15)
    }

    private fun bodyweightReps(name: String, ordinal: Int, regressing: Boolean, lighter: Boolean): Int {
        val start = when (name) {
            "Push-Up" -> 8
            "Pull-Up" -> 4
            "Chin-Up" -> 3
            "Dip" -> 6
            else -> 8
        }
        val cap = when (name) {
            "Push-Up" -> 30
            "Pull-Up", "Chin-Up" -> 15
            "Dip" -> 20
            else -> 20
        }
        var reps = start + ordinal / 4 + repNoise(ordinal)
        if (regressing) reps -= 2
        if (lighter) reps -= 1
        return reps.coerceIn(3, cap)
    }

    private fun repNoise(ordinal: Int): Int {
        return when (ordinal % 5) {
            1 -> 1
            3 -> -1
            4 -> -2
            else -> 0
        }
    }

    private fun plankSeconds(ordinal: Int, regressing: Boolean, lighter: Boolean): Int {
        var seconds = 25 + ordinal * 2
        if (ordinal % 5 == 4) seconds -= 8
        if (regressing) seconds -= 15
        if (lighter) seconds -= 5
        return seconds.coerceIn(20, 120)
    }

    private fun addedWeightKg(name: String, ordinal: Int): Double? {
        val threshold = when (name) {
            "Dip" -> 24
            "Pull-Up" -> 20
            else -> return null
        }
        if (ordinal < threshold || ordinal % 2 != 0) return null
        val steps = (ordinal - threshold) / 8
        return (2.5 + steps * 2.5).coerceAtMost(12.5)
    }

    private fun fatigue(setIndex: Int): Int {
        return when (setIndex) {
            0, 1 -> 0
            2, 3 -> 1
            else -> 2
        }
    }

    private fun stamp(startedAt: Long, finishedAt: Long, exerciseIndex: Int, setIndex: Int): Long {
        val raw = startedAt + (exerciseIndex * 8L + setIndex + 1L) * 60_000L
        return raw.coerceIn(startedAt + 1_000L, finishedAt - 1_000L)
    }

    private fun roundToIncrement(value: Double, increment: Double): Double {
        return round(value / increment).toInt() * increment
    }

    private fun demoClientWorkoutId(sessionId: Long): String {
        return UUID.nameUUIDFromBytes(
            "strict-demo-workout-session:$sessionId".toByteArray(Charsets.UTF_8)
        ).toString()
    }

    private fun epoch(date: LocalDate, hour: Int): Long {
        return date.atTime(hour, 0).toInstant(zone).toEpochMilli()
    }

    private class Ids {
        private var value = 1L
        fun next(): Long = value++
    }

    private class WeightCursor(private val rows: List<WeightMeasurementEntity>) {
        private var index = -1
        fun at(date: LocalDate): WeightMeasurementEntity? {
            while (index + 1 < rows.size && !LocalDate.parse(rows[index + 1].date).isAfter(date)) {
                index += 1
            }
            return rows.getOrNull(index)
        }
    }

    private enum class Phase { BASE, BUILD, PEAK }

    private enum class PlanKind { PUSH, PULL, LEGS }

    private enum class Fate {
        ON_TIME,
        LATE,
        MISSED,
        CANCELLED,
        RESCHEDULED,
        FUTURE,
        FUTURE_CANCELLED
    }

    private data class PlannedSlot(
        val date: LocalDate,
        val plan: PlanKind,
        val weekIndex: Int,
        val phase: Phase,
        val lighter: Boolean,
        val peakVolume: Boolean,
        val occurrence: Int
    )

    private data class Outcome(
        val slot: PlannedSlot,
        val fate: Fate,
        val scheduledDate: LocalDate,
        val workoutDate: LocalDate?
    )

    private data class TrainingEvent(
        val date: LocalDate,
        val outcome: Outcome?,
        val unplannedPlan: PlanKind?
    )

    private data class CatalogExercise(
        val id: Long,
        val draft: ExerciseDraft,
        val entity: ExerciseEntity,
        val muscles: List<ExerciseMuscleEntity>
    )

    private data class TemplateBundle(
        val templates: List<WorkoutTemplateEntity>,
        val exercises: List<WorkoutTemplateExerciseEntity>,
        val sets: List<WorkoutTemplateSetEntity>,
        val ids: Map<PlanKind, Long>
    )

    private data class TrainingRows(
        val scheduled: List<ScheduledWorkoutEntity>,
        val sessions: List<WorkoutSessionEntity>,
        val sessionExercises: List<WorkoutSessionExerciseEntity>,
        val sessionMuscles: List<WorkoutSessionExerciseMuscleEntity>,
        val sessionSets: List<WorkoutSessionSetEntity>,
        val goals: List<WeeklyWorkoutGoalEntity> = emptyList()
    )

    private data class LoadProfile(
        val startKg: Double,
        val stepKg: Double,
        val maxKg: Double,
        val increment: Double,
        val baseReps: Int
    )

    private data class BenchLoad(val date: LocalDate, val kg: Double)

    private val compoundNames = setOf(
        "Bench Press",
        "Overhead Press",
        "Squat",
        "Deadlift",
        "Romanian Deadlift",
        "Hip Thrust",
        "Barbell Row",
        "Pull-Up"
    )

    private val loadProfiles = mapOf(
        "Bench Press" to LoadProfile(40.0, 2.5, 80.0, 2.5, 8),
        "Overhead Press" to LoadProfile(25.0, 2.5, 47.5, 2.5, 6),
        "Barbell Row" to LoadProfile(35.0, 2.5, 70.0, 2.5, 8),
        "Squat" to LoadProfile(50.0, 2.5, 95.0, 2.5, 6),
        "Romanian Deadlift" to LoadProfile(40.0, 2.5, 80.0, 2.5, 8),
        "Deadlift" to LoadProfile(60.0, 2.5, 120.0, 2.5, 5),
        "Hip Thrust" to LoadProfile(40.0, 2.5, 90.0, 2.5, 8),
        "Leg Curl" to LoadProfile(20.0, 2.5, 40.0, 2.5, 10),
        "Calf Raise" to LoadProfile(30.0, 2.5, 60.0, 2.5, 12),
        "Leg Press" to LoadProfile(80.0, 5.0, 160.0, 5.0, 10),
        "Lunge" to LoadProfile(16.0, 2.5, 32.0, 2.5, 8),
        "Dumbbell Row" to LoadProfile(12.0, 1.0, 30.0, 1.0, 8),
        "Lateral Raise" to LoadProfile(4.0, 1.0, 12.0, 1.0, 12),
        "Biceps Curl" to LoadProfile(6.0, 1.0, 14.0, 1.0, 10),
        "Triceps Extension" to LoadProfile(12.0, 1.0, 28.0, 1.0, 10),
        "Face Pull" to LoadProfile(10.0, 2.5, 25.0, 2.5, 12),
        "Lat Pulldown" to LoadProfile(30.0, 2.5, 55.0, 2.5, 10)
    )
}

object DemoTrainingHistoryPaths {
    const val FILE_NAME = "my-muscle-map-demo-18-months.json"

    fun backupFile(): File {
        val cwd = File(System.getProperty("user.dir") ?: error("user.dir is not set"))
        val repoRoot = when {
            File(cwd, "app/src/test").isDirectory -> cwd
            File(cwd, "src/test").isDirectory -> cwd.parentFile
            else -> error("Cannot locate the repository root from $cwd")
        }
        return File(repoRoot, "app/src/test/resources/$FILE_NAME")
    }
}

data class DemoCalendar(
    val referenceDate: LocalDate,
    val historyStart: LocalDate,
    val breakStart: LocalDate,
    val breakEnd: LocalDate,
    val returnEnd: LocalDate
)

data class DemoRangeStats(
    val range: StatisticsRange,
    val workouts: Int,
    val volumeKg: Double?,
    val volumeWeeks: Int,
    val adherencePercent: Int?,
    val planned: Int,
    val completedScheduled: Int,
    val missed: Int,
    val benchHistoryPoints: Int,
    val muscles: Int
)

data class DemoBenchProgression(
    val firstDate: LocalDate,
    val firstKg: Double,
    val latestDate: LocalDate,
    val latestKg: Double,
    val beforeBreakKg: Double,
    val afterBreakKg: Double,
    val hasDecrease: Boolean,
    val historyPoints: Int
)

data class DemoScheduleExample(
    val label: String,
    val templateName: String,
    val originalScheduledDate: LocalDate?,
    val scheduledDate: LocalDate?,
    val workoutDate: LocalDate?,
    val cancelled: Boolean
)

data class DemoHistoryAnalysis(
    val referenceDate: LocalDate,
    val historyStart: LocalDate,
    val firstCompleted: LocalDate,
    val lastCompleted: LocalDate,
    val breakStart: LocalDate,
    val breakEnd: LocalDate,
    val bodyWeightEntries: Int,
    val firstBodyWeightKg: Double,
    val lastBodyWeightKg: Double,
    val workoutTemplates: Int,
    val exercises: Int,
    val scheduledOccurrences: Int,
    val completedScheduled: Int,
    val lateCompletions: Int,
    val missedOccurrences: Int,
    val cancelledOccurrences: Int,
    val rescheduledOccurrences: Int,
    val unplannedWorkouts: Int,
    val completedSessions: Int,
    val completedSets: Int,
    val volumeSets: Int,
    val adherencePercent: Int?,
    val adherencePlanned: Int,
    val adherenceCompleted: Int,
    val ranges: List<DemoRangeStats>,
    val bench: DemoBenchProgression,
    val heaviestWeekKg: Double,
    val lightestPositiveWeekKg: Double,
    val breakVolumeKg: Double,
    val examples: List<DemoScheduleExample>
)
