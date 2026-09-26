package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupErrorCode
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupParseResult
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSnapshot
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.appbackup.AppBackupTables
import app.mymusclemap.data.appbackup.emptyTables
import app.mymusclemap.data.local.ExerciseEntity
import app.mymusclemap.data.local.ExerciseMuscleEntity
import app.mymusclemap.data.local.ScheduledWorkoutEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseEntity
import app.mymusclemap.data.local.WorkoutSessionExerciseMuscleEntity
import app.mymusclemap.data.local.WorkoutSessionSetEntity
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.local.WorkoutTemplateExerciseEntity
import app.mymusclemap.data.local.WorkoutTemplateSetEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.exercise.ExerciseCategory
import app.mymusclemap.domain.exercise.MeasurementType
import app.mymusclemap.domain.exercise.MovementPattern
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.exercise.MuscleRole
import app.mymusclemap.domain.exercise.ResistanceBasis
import app.mymusclemap.domain.exercise.WeightInterpretation
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.theme.HexColor
import app.mymusclemap.domain.theme.HexParseResult
import app.mymusclemap.domain.theme.PaletteType
import app.mymusclemap.domain.theme.ThemeMode
import app.mymusclemap.domain.theme.ThemeSeeds
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.PlannedLoadKind
import app.mymusclemap.domain.workout.SessionSetStatus
import app.mymusclemap.domain.workout.SessionStatus
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AppBackupRepositoryTest {
    private lateinit var sourceDb: WeightDatabase
    private lateinit var targetDb: WeightDatabase
    private lateinit var themePreferences: ThemePreferences

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDb = openDb(context)
        targetDb = openDb(context)
        themePreferences = ThemePreferences(context)
        themePreferences.replaceAppearance(AppearanceSettings.Default)
        themePreferences.clearOnboardingProgress()
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun roundTripRestoresEveryTableAndCustomAppearanceWithoutMerging() = runTest {
        val tables = representativeTables()
        val appearance = customAppearance()
        sourceDb.appBackupDao().replaceAll(tables)
        themePreferences.replaceAppearance(appearance)
        val exportedAt = Instant.parse("2026-09-25T10:15:30Z")
        val json = AppBackupRepository(sourceDb, themePreferences) { exportedAt }
            .exportJson(AppBackupSource("hu.laca.weighttracker.debug", "1.0-debug"))

        val parsed = AppBackupJson.parse(json) as AppBackupParseResult.Success
        assertEquals(AppBackupFormat.FORMAT_VERSION, parsed.snapshot.formatVersion)
        assertEquals(AppBackupFormat.SCHEMA_VERSION, parsed.snapshot.schemaVersion)
        assertEquals("hu.laca.weighttracker.debug", parsed.snapshot.source?.applicationId)
        assertEquals(tables, parsed.snapshot.tables)

        targetDb.appBackupDao().replaceAll(junkTables())
        themePreferences.replaceAppearance(
            AppearanceSettings.Default.copy(mode = ThemeMode.Light)
        )
        val restored = AppBackupRepository(targetDb, themePreferences)
            .restoreJson(json)
        assertEquals(AppBackupRestoreResult.Success, restored)

        val loaded = targetDb.appBackupDao().loadTables()
        assertEquals(tables, loaded)
        assertEquals(appearance.mode, themePreferences.current().mode)
        assertEquals(appearance.paletteType, themePreferences.current().paletteType)
        assertEquals(appearance.customLight.canonical(), themePreferences.current().customLight.canonical())
        assertEquals(appearance.customDark.canonical(), themePreferences.current().customDark.canonical())
        assertEquals(
            tables.exerciseMuscles.map { it.exerciseId }.toSet(),
            loaded.exercises.map { it.id }.intersect(loaded.exerciseMuscles.map { it.exerciseId }.toSet())
        )
        val archivedExercise = loaded.exercises.single { it.archived }
        assertEquals(51L, archivedExercise.id)
        val archivedTemplate = loaded.workoutTemplates.single { it.archived }
        assertEquals(81L, archivedTemplate.id)
        val completed = loaded.workoutSessions.single { it.status == SessionStatus.COMPLETED.name }
        assertEquals(90L, completed.scheduledWorkoutId)
        assertEquals(99L, loaded.scheduledWorkouts.single { it.id == 92L }.cancelledAt)
        assertEquals("2026-09-18", loaded.scheduledWorkouts.single { it.id == 91L }.originalScheduledDate)
        assertEquals("Nyomó nap", loaded.scheduledWorkouts.single { it.id == 90L }.templateName)
        assertEquals("import:abc", completed.importFingerprint)
        assertEquals("Edzés jegyzet", completed.notes)
        val active = loaded.workoutSessions.single { it.status == SessionStatus.IN_PROGRESS.name }
        assertEquals(1, active.activeLock)
        assertEquals(
            loaded.workoutSessionSets.map { it.sessionExerciseId }.toSet(),
            loaded.workoutSessionExercises.map { it.id }.toSet()
        )
        assertTrue(loaded.workoutSessionSets.any { it.addedDuringWorkout })
        assertTrue(loaded.workoutSessionSets.any { it.status == SessionSetStatus.SKIPPED.name })
        assertNotEquals(junkTables().weightMeasurements, loaded.weightMeasurements)
    }

    @Test
    fun explicitAutoincrementIdsAdvanceSqliteSequence() = runTest {
        sourceDb.appBackupDao().replaceAll(representativeTables())
        themePreferences.replaceAppearance(customAppearance())
        val json = AppBackupRepository(sourceDb, themePreferences)
            .exportJson(AppBackupSource("app.mymusclemap.debug", "2.0"))
        assertEquals(AppBackupRestoreResult.Success, AppBackupRepository(targetDb, themePreferences).restoreJson(json))

        val sequence = sqliteSequence("exercises")
        assertEquals(52L, sequence)
        val nextId = targetDb.exerciseDao().insert(
            ExerciseEntity(
                id = 0,
                name = "Új gyakorlat",
                normalizedName = "ujgyakorlat",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.ISOLATION.name,
                measurementType = MeasurementType.REPETITIONS.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                notes = null,
                archived = false,
                createdAt = 9L,
                updatedAt = 9L
            )
        )
        assertEquals(53L, nextId)
    }

    @Test
    fun invalidRestoreLeavesExistingDatabaseAndSettingsUntouched() = runTest {
        val existing = junkTables()
        targetDb.appBackupDao().replaceAll(existing)
        themePreferences.replaceAppearance(customAppearance())
        val beforeAppearance = themePreferences.current()
        val cases = listOf(
            "{",
            "",
            JSONObjectLike.unsupportedSchema(representativeSnapshot()),
            AppBackupJson.encode(
                representativeSnapshot().copy(
                    tables = representativeTables().copy(
                        exerciseMuscles = listOf(
                            ExerciseMuscleEntity(999L, MuscleGroup.CHEST.name, MuscleRole.PRIMARY.name)
                        )
                    )
                )
            )
        )
        cases.forEach { payload ->
            val result = AppBackupRepository(targetDb, themePreferences).restoreJson(payload)
            assertTrue("expected invalid for payload `$payload`", result is AppBackupRestoreResult.Invalid)
            assertEquals(existing, targetDb.appBackupDao().loadTables())
            assertEquals(beforeAppearance.mode, themePreferences.current().mode)
            assertEquals(beforeAppearance.paletteType, themePreferences.current().paletteType)
            assertEquals(
                beforeAppearance.customLight.canonical(),
                themePreferences.current().customLight.canonical()
            )
        }
    }

    @Test
    fun replaceAllRollsBackWhenInsertFailsAfterDeletes() = runTest {
        val original = representativeTables()
        targetDb.appBackupDao().replaceAll(original)
        val invalid = original.copy(
            exerciseMuscles = original.exerciseMuscles + ExerciseMuscleEntity(
                exerciseId = 999L,
                muscleGroup = MuscleGroup.NECK.name,
                role = MuscleRole.SECONDARY.name
            )
        )
        var failed = false
        try {
            targetDb.appBackupDao().replaceAll(invalid)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
        assertEquals(original, targetDb.appBackupDao().loadTables())
    }

    @Test
    fun exportOmitsOnboardingFlagAndRestorePreservesLocalCompletion() = runTest {
        themePreferences.markOnboardingCompleted()
        sourceDb.appBackupDao().replaceAll(representativeTables())
        themePreferences.replaceAppearance(customAppearance())
        val json = AppBackupRepository(sourceDb, themePreferences)
            .exportJson(AppBackupSource("app.mymusclemap.debug", "1.0-debug"))
        val parsed = AppBackupJson.parse(json) as AppBackupParseResult.Success
        assertFalse(parsed.snapshot.settings.containsKey("onboarding_completed"))
        assertFalse(parsed.snapshot.settings.containsKey("onboarding_started"))
        assertFalse(parsed.snapshot.settings.containsKey("onboarding_welcome_pending"))
        assertFalse(parsed.snapshot.settings.containsKey("onboarding_heatmap_seen"))
        assertFalse(parsed.snapshot.settings.containsKey("onboarding_reminder_dismissed"))

        themePreferences.setOnboardingCompleted(false)
        assertEquals(AppBackupRestoreResult.Success, AppBackupRepository(targetDb, themePreferences).restoreJson(json))
        assertFalse(themePreferences.isOnboardingCompleted())

        themePreferences.markOnboardingCompleted()
        assertEquals(AppBackupRestoreResult.Success, AppBackupRepository(targetDb, themePreferences).restoreJson(json))
        assertTrue(themePreferences.isOnboardingCompleted())
        assertEquals(customAppearance().mode, themePreferences.current().mode)

        themePreferences.markOnboardingStarted()
        themePreferences.setHeatmapSeen()
        assertEquals(AppBackupRestoreResult.Success, AppBackupRepository(targetDb, themePreferences).restoreJson(json))
        assertTrue(themePreferences.isOnboardingStarted())
        assertTrue(themePreferences.currentOnboardingFlags().heatmapSeen)
        assertTrue(themePreferences.isOnboardingCompleted())
    }

    @Test
    fun restoreBytesRejectsCorruptUtf8WithoutWriting() = runTest {
        val existing = junkTables()
        targetDb.appBackupDao().replaceAll(existing)
        val result = AppBackupRepository(targetDb, themePreferences)
            .restoreBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
        val invalid = result as AppBackupRestoreResult.Invalid
        assertTrue(invalid.errors.any { it.code == AppBackupErrorCode.InvalidJson })
        assertEquals(existing, targetDb.appBackupDao().loadTables())
    }

    @Test
    fun schema6BackupRestoresScheduledRowsByHydratingTemplateNames() = runTest {
        val json = schema6ScheduledBackup(representativeSnapshot())
        val parsed = AppBackupJson.parse(json) as AppBackupParseResult.Success
        assertEquals(6, parsed.snapshot.schemaVersion)
        val scheduled = parsed.snapshot.tables.scheduledWorkouts
        assertEquals(2, scheduled.size)
        val linked = scheduled.single { it.id == 90L }
        assertEquals("Nyomó nap", linked.templateName)
        assertEquals("2026-09-20", linked.originalScheduledDate)
        assertNull(linked.cancelledAt)
        val pending = scheduled.single { it.id == 91L }
        assertEquals("Régi cardio", pending.templateName)
        assertEquals("2026-09-21", pending.originalScheduledDate)
        assertEquals(AppBackupRestoreResult.Success, AppBackupRepository(targetDb, themePreferences).restoreJson(json))
        val loaded = targetDb.appBackupDao().loadTables().scheduledWorkouts.sortedBy { it.id }
        assertEquals(scheduled, loaded)
        assertEquals(90L, targetDb.workoutSessionDao().getById(200)!!.scheduledWorkoutId)
    }

    private fun sqliteSequence(table: String): Long? {
        val cursor = targetDb.openHelper.readableDatabase.query(
            "SELECT seq FROM sqlite_sequence WHERE name = ?",
            arrayOf(table)
        )
        cursor.use {
            return if (it.moveToFirst()) it.getLong(0) else null
        }
    }

    private fun openDb(context: Context): WeightDatabase {
        return Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }
}

private object JSONObjectLike {
    fun unsupportedSchema(snapshot: AppBackupSnapshot): String {
        val root = JSONObject(AppBackupJson.encode(snapshot))
        root.put("schemaVersion", 5)
        return root.toString()
    }
}

private fun schema6ScheduledBackup(snapshot: AppBackupSnapshot): String {
    val root = JSONObject(AppBackupJson.encode(snapshot))
    root.put("schemaVersion", 6)
    val tables = root.getJSONObject("tables")
    val original = tables.getJSONArray(AppBackupFormat.TABLE_SCHEDULED_WORKOUTS)
    val schema6Rows = JSONArray()
    for (index in 0 until original.length()) {
        val row = original.getJSONObject(index)
        if (!row.isNull("cancelledAt")) {
            continue
        }
        row.remove("originalScheduledDate")
        row.remove("templateName")
        row.remove("cancelledAt")
        schema6Rows.put(row)
    }
    tables.put(AppBackupFormat.TABLE_SCHEDULED_WORKOUTS, schema6Rows)
    return root.toString()
}

private fun customAppearance(): AppearanceSettings {
    val primary = (HexColor.parse("#8A2BE2") as HexParseResult.Valid).rgb
    return AppearanceSettings(
        mode = ThemeMode.Dark,
        paletteType = PaletteType.Custom,
        customLight = ThemeSeeds.copyOfFactoryLight().copy(primary = primary),
        customDark = ThemeSeeds.copyOfFactoryDark()
    )
}

private fun junkTables(): AppBackupTables {
    return emptyTables().copy(
        weightMeasurements = listOf(
            WeightMeasurementEntity(1, "2020-01-01", 70.0, 1, 1)
        )
    )
}

private fun representativeSnapshot(): AppBackupSnapshot {
    return AppBackupSnapshot(
        formatVersion = AppBackupFormat.FORMAT_VERSION,
        schemaVersion = AppBackupFormat.SCHEMA_VERSION,
        exportedAt = Instant.parse("2026-09-25T10:15:30Z"),
        source = AppBackupSource("hu.laca.weighttracker.debug", "1.0-debug"),
        tables = representativeTables(),
        settings = emptyMap()
    )
}

private fun representativeTables(): AppBackupTables {
    return AppBackupTables(
        weightMeasurements = listOf(
            WeightMeasurementEntity(7, "2026-01-01", 83.2, 10, 11),
            WeightMeasurementEntity(9, "2026-09-20", 82.4, 12, 13)
        ),
        exercises = listOf(
            ExerciseEntity(
                id = 50,
                name = "Fekvenyomás",
                normalizedName = "fekvenyomas",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                notes = "lapos pad",
                archived = false,
                createdAt = 20,
                updatedAt = 21
            ),
            ExerciseEntity(
                id = 51,
                name = "Tárogatás",
                normalizedName = "tarogatas",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.ISOLATION.name,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                notes = null,
                archived = true,
                createdAt = 22,
                updatedAt = 23
            ),
            ExerciseEntity(
                id = 52,
                name = "Futás",
                normalizedName = "futas",
                category = ExerciseCategory.CARDIO.name,
                movementPattern = MovementPattern.CARDIO.name,
                measurementType = MeasurementType.DISTANCE_AND_DURATION.name,
                resistanceBasis = ResistanceBasis.NONE.name,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE.name,
                notes = "park",
                archived = false,
                createdAt = 24,
                updatedAt = 25
            )
        ),
        exerciseMuscles = listOf(
            ExerciseMuscleEntity(50, MuscleGroup.CHEST.name, MuscleRole.PRIMARY.name),
            ExerciseMuscleEntity(50, MuscleGroup.TRICEPS.name, MuscleRole.SECONDARY.name),
            ExerciseMuscleEntity(51, MuscleGroup.CHEST.name, MuscleRole.PRIMARY.name),
            ExerciseMuscleEntity(52, MuscleGroup.CARDIOVASCULAR.name, MuscleRole.PRIMARY.name)
        ),
        workoutTemplates = listOf(
            WorkoutTemplateEntity(80, "Nyomó nap", "nyomo nap", "fő edzés", false, 30, 31),
            WorkoutTemplateEntity(81, "Régi cardio", "regi cardio", null, true, 32, 33)
        ),
        workoutTemplateExercises = listOf(
            WorkoutTemplateExerciseEntity(800, 80, 50, 0, "tempo", 34),
            WorkoutTemplateExerciseEntity(801, 80, 51, 1, null, 35),
            WorkoutTemplateExerciseEntity(810, 81, 52, 0, null, 36)
        ),
        workoutTemplateSets = listOf(
            WorkoutTemplateSetEntity(8000, 800, 0, 8, 12, PlannedLoadKind.EXTERNAL_WEIGHT.name, 80.0, null, null),
            WorkoutTemplateSetEntity(8001, 800, 1, 6, 8, PlannedLoadKind.EXTERNAL_WEIGHT.name, 85.0, null, null),
            WorkoutTemplateSetEntity(8010, 801, 0, 10, 12, PlannedLoadKind.EXTERNAL_WEIGHT.name, 12.5, null, null),
            WorkoutTemplateSetEntity(8100, 810, 0, null, null, PlannedLoadKind.NONE.name, null, 1800, 5000.0)
        ),
        scheduledWorkouts = listOf(
            ScheduledWorkoutEntity(
                id = 90,
                scheduledDate = "2026-09-20",
                originalScheduledDate = "2026-09-20",
                templateId = 80,
                templateName = "Nyomó nap",
                createdAt = 40
            ),
            ScheduledWorkoutEntity(
                id = 91,
                scheduledDate = "2026-09-21",
                originalScheduledDate = "2026-09-18",
                templateId = 81,
                templateName = "Régi cardio",
                createdAt = 41
            ),
            ScheduledWorkoutEntity(
                id = 92,
                scheduledDate = "2026-09-20",
                originalScheduledDate = "2026-09-20",
                templateId = 80,
                templateName = "Nyomó nap",
                createdAt = 42,
                cancelledAt = 99L
            )
        ),
        workoutSessions = listOf(
            WorkoutSessionEntity(
                id = 200,
                templateId = 80,
                templateName = "Nyomó nap",
                status = SessionStatus.COMPLETED.name,
                workoutDate = "2026-09-20",
                startedAt = 50,
                finishedAt = 60,
                abandonedAt = null,
                notes = "Edzés jegyzet",
                bodyWeightKg = 82.4,
                bodyWeightSource = BodyWeightSource.MEASURED_SAME_DAY.name,
                bodyWeightSourceDate = "2026-09-20",
                createdAt = 50,
                updatedAt = 60,
                activeLock = null,
                importFingerprint = "import:abc",
                scheduledWorkoutId = 90
            ),
            WorkoutSessionEntity(
                id = 201,
                templateId = 80,
                templateName = "Nyomó nap",
                status = SessionStatus.IN_PROGRESS.name,
                workoutDate = "2026-09-22",
                startedAt = 70,
                finishedAt = null,
                abandonedAt = null,
                notes = "folyamatban",
                bodyWeightKg = 82.0,
                bodyWeightSource = BodyWeightSource.MANUAL.name,
                bodyWeightSourceDate = null,
                createdAt = 70,
                updatedAt = 71,
                activeLock = 1,
                importFingerprint = null,
                scheduledWorkoutId = null
            )
        ),
        workoutSessionExercises = listOf(
            WorkoutSessionExerciseEntity(
                id = 2000,
                sessionId = 200,
                exerciseId = 50,
                position = 0,
                name = "Fekvenyomás",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                primaryMuscle = MuscleGroup.CHEST.name,
                notes = "lapos pad"
            ),
            WorkoutSessionExerciseEntity(
                id = 2001,
                sessionId = 200,
                exerciseId = 51,
                position = 1,
                name = "Tárogatás",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.ISOLATION.name,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                primaryMuscle = MuscleGroup.CHEST.name,
                notes = null
            ),
            WorkoutSessionExerciseEntity(
                id = 2002,
                sessionId = 200,
                exerciseId = 52,
                position = 2,
                name = "Futás",
                category = ExerciseCategory.CARDIO.name,
                movementPattern = MovementPattern.CARDIO.name,
                measurementType = MeasurementType.DISTANCE_AND_DURATION.name,
                resistanceBasis = ResistanceBasis.NONE.name,
                weightInterpretation = WeightInterpretation.NOT_APPLICABLE.name,
                primaryMuscle = MuscleGroup.CARDIOVASCULAR.name,
                notes = "park"
            ),
            WorkoutSessionExerciseEntity(
                id = 2010,
                sessionId = 201,
                exerciseId = 50,
                position = 0,
                name = "Fekvenyomás",
                category = ExerciseCategory.STRENGTH.name,
                movementPattern = MovementPattern.HORIZONTAL_PUSH.name,
                measurementType = MeasurementType.REPETITIONS_AND_WEIGHT.name,
                resistanceBasis = ResistanceBasis.EXTERNAL.name,
                weightInterpretation = WeightInterpretation.TOTAL.name,
                primaryMuscle = MuscleGroup.CHEST.name,
                notes = null
            )
        ),
        workoutSessionExerciseMuscles = listOf(
            WorkoutSessionExerciseMuscleEntity(2000, MuscleGroup.CHEST.name, MuscleRole.PRIMARY.name),
            WorkoutSessionExerciseMuscleEntity(2000, MuscleGroup.TRICEPS.name, MuscleRole.SECONDARY.name),
            WorkoutSessionExerciseMuscleEntity(2001, MuscleGroup.CHEST.name, MuscleRole.PRIMARY.name),
            WorkoutSessionExerciseMuscleEntity(2002, MuscleGroup.CARDIOVASCULAR.name, MuscleRole.PRIMARY.name),
            WorkoutSessionExerciseMuscleEntity(2010, MuscleGroup.CHEST.name, MuscleRole.PRIMARY.name)
        ),
        workoutSessionSets = listOf(
            WorkoutSessionSetEntity(
                id = 20000,
                sessionExerciseId = 2000,
                position = 0,
                plannedMinReps = 8,
                plannedMaxReps = 12,
                plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                plannedWeightKg = 80.0,
                plannedDurationSeconds = null,
                plannedDistanceMeters = null,
                actualReps = 10,
                actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                actualWeightKg = 82.5,
                actualDurationSeconds = null,
                actualDistanceMeters = null,
                status = SessionSetStatus.COMPLETED.name,
                completedAt = 55,
                addedDuringWorkout = false
            ),
            WorkoutSessionSetEntity(
                id = 20001,
                sessionExerciseId = 2000,
                position = 1,
                plannedMinReps = 6,
                plannedMaxReps = 8,
                plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                plannedWeightKg = 85.0,
                plannedDurationSeconds = null,
                plannedDistanceMeters = null,
                actualReps = null,
                actualLoadKind = null,
                actualWeightKg = null,
                actualDurationSeconds = null,
                actualDistanceMeters = null,
                status = SessionSetStatus.SKIPPED.name,
                completedAt = 56,
                addedDuringWorkout = false
            ),
            WorkoutSessionSetEntity(
                id = 20010,
                sessionExerciseId = 2001,
                position = 0,
                plannedMinReps = 10,
                plannedMaxReps = 12,
                plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                plannedWeightKg = 12.5,
                plannedDurationSeconds = null,
                plannedDistanceMeters = null,
                actualReps = 12,
                actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                actualWeightKg = 12.5,
                actualDurationSeconds = null,
                actualDistanceMeters = null,
                status = SessionSetStatus.COMPLETED.name,
                completedAt = 57,
                addedDuringWorkout = false
            ),
            WorkoutSessionSetEntity(
                id = 20020,
                sessionExerciseId = 2002,
                position = 0,
                plannedMinReps = null,
                plannedMaxReps = null,
                plannedLoadKind = PlannedLoadKind.NONE.name,
                plannedWeightKg = null,
                plannedDurationSeconds = 1800,
                plannedDistanceMeters = 5000.0,
                actualReps = null,
                actualLoadKind = PlannedLoadKind.NONE.name,
                actualWeightKg = null,
                actualDurationSeconds = 1760,
                actualDistanceMeters = 5100.0,
                status = SessionSetStatus.COMPLETED.name,
                completedAt = 58,
                addedDuringWorkout = false
            ),
            WorkoutSessionSetEntity(
                id = 20100,
                sessionExerciseId = 2010,
                position = 0,
                plannedMinReps = 8,
                plannedMaxReps = 12,
                plannedLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                plannedWeightKg = 80.0,
                plannedDurationSeconds = null,
                plannedDistanceMeters = null,
                actualReps = 8,
                actualLoadKind = PlannedLoadKind.EXTERNAL_WEIGHT.name,
                actualWeightKg = 80.0,
                actualDurationSeconds = null,
                actualDistanceMeters = null,
                status = SessionSetStatus.PENDING.name,
                completedAt = null,
                addedDuringWorkout = true
            )
        )
    )
}
