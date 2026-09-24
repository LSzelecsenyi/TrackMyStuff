package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import hu.laca.weighttracker.domain.exercise.MuscleGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeightDatabaseMigrationTest {
    @Test
    fun chainedMigrationFromVersion1PreservesWeightMeasurements() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(TEST_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(Version1Callback())
                .build()
        )
        helper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO weight_measurements (date, weightKg, createdAt, updatedAt)
                VALUES ('2026-03-11', 82.4, 100, 200)
                """.trimIndent()
            )
            close()
        }

        val database = Room.databaseBuilder(context, WeightDatabase::class.java, TEST_DB)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val stored = database.weightMeasurementDao().getByDate("2026-03-11")
            assertEquals("2026-03-11", stored?.date)
            assertEquals(82.4, stored!!.weightKg, 0.0)
            assertEquals(100L, stored.createdAt)
            assertEquals(200L, stored.updatedAt)
            assertTrue(database.exerciseDao().observeAll().first().isEmpty())
            assertTrue(database.workoutTemplateDao().observeAll().first().isEmpty())
            assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFromVersion2PreservesWeightsExercisesAndMuscles() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(V2_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(V2_DB)
                .callback(Version2Callback())
                .build()
        )
        helper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO weight_measurements (date, weightKg, createdAt, updatedAt)
                VALUES ('2026-09-15', 88.3, 10, 20)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO exercises (name, normalizedName, category, movementPattern, measurementType,
                    resistanceBasis, weightInterpretation, notes, archived, createdAt, updatedAt)
                VALUES ('Húzódzkodás', 'húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS',
                    'BODYWEIGHT', 'NOT_APPLICABLE', NULL, 0, 10, 20)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO exercise_muscles (exerciseId, muscleGroup, role)
                VALUES (1, 'LATS', 'PRIMARY'), (1, 'BICEPS', 'SECONDARY')
                """.trimIndent()
            )
            close()
        }

        val database = Room.databaseBuilder(context, WeightDatabase::class.java, V2_DB)
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            val weight = database.weightMeasurementDao().getByDate("2026-09-15")
            assertEquals(88.3, weight!!.weightKg, 0.0)
            val exercise = database.exerciseDao().getById(1)!!
            assertEquals("Húzódzkodás", exercise.name)
            val muscles = database.exerciseDao().getMuscles(1)
            assertEquals(2, muscles.size)
            assertTrue(database.workoutTemplateDao().observeAll().first().isEmpty())
            assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFromVersion3PreservesTemplates() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(V3_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(V3_DB)
                .callback(Version3Callback())
                .build()
        )
        helper.writableDatabase.apply {
            execSQL(
                """
                INSERT INTO weight_measurements (date, weightKg, createdAt, updatedAt)
                VALUES ('2026-09-15', 88.3, 10, 20)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO exercises (name, normalizedName, category, movementPattern, measurementType,
                    resistanceBasis, weightInterpretation, notes, archived, createdAt, updatedAt)
                VALUES ('Húzódzkodás', 'húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS',
                    'BODYWEIGHT', 'NOT_APPLICABLE', NULL, 0, 10, 20)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO exercise_muscles (exerciseId, muscleGroup, role) VALUES (1, 'LATS', 'PRIMARY')"
            )
            execSQL(
                """
                INSERT INTO workout_templates (name, normalizedName, notes, archived, createdAt, updatedAt)
                VALUES ('Push A', 'push a', NULL, 0, 10, 20)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_template_exercises (templateId, exerciseId, position, notes, createdAt)
                VALUES (1, 1, 0, NULL, 10)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_template_sets (templateExerciseId, position, minReps, maxReps, loadKind, weightKg, durationSeconds, distanceMeters)
                VALUES (1, 0, 8, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL)
                """.trimIndent()
            )
            close()
        }

        val database = Room.databaseBuilder(context, WeightDatabase::class.java, V3_DB)
            .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(88.3, database.weightMeasurementDao().getByDate("2026-09-15")!!.weightKg, 0.0)
            assertEquals("Húzódzkodás", database.exerciseDao().getById(1)!!.name)
            assertEquals("Push A", database.workoutTemplateDao().getById(1)!!.name)
            assertEquals(1, database.workoutTemplateDao().getExercises(1).size)
            assertTrue(database.workoutSessionDao().observeAll().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun migrationFromVersion4PreservesSessionsAndAddsNullableImportFingerprint() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(V4_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(V4_DB)
                .callback(Version4Callback())
                .build()
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO weight_measurements (date, weightKg, createdAt, updatedAt) VALUES ('2026-09-15', 88.3, 10, 20)")
            execSQL(
                """
                INSERT INTO exercises (name, normalizedName, category, movementPattern, measurementType,
                    resistanceBasis, weightInterpretation, notes, archived, createdAt, updatedAt)
                VALUES ('Húzódzkodás', 'húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS',
                    'BODYWEIGHT', 'NOT_APPLICABLE', NULL, 0, 10, 20)
                """.trimIndent()
            )
            execSQL("INSERT INTO exercise_muscles (exerciseId, muscleGroup, role) VALUES (1, 'LATS', 'PRIMARY'), (1, 'BICEPS', 'SECONDARY')")
            execSQL(
                "INSERT INTO workout_templates (name, normalizedName, notes, archived, createdAt, updatedAt) VALUES ('Push A', 'push a', NULL, 0, 10, 20)"
            )
            execSQL(
                "INSERT INTO workout_template_exercises (templateId, exerciseId, position, notes, createdAt) VALUES (1, 1, 0, NULL, 10)"
            )
            execSQL(
                """
                INSERT INTO workout_template_sets (templateExerciseId, position, minReps, maxReps, loadKind, weightKg, durationSeconds, distanceMeters)
                VALUES (1, 0, 8, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_sessions (templateId, templateName, status, workoutDate, startedAt, finishedAt, abandonedAt,
                    notes, bodyWeightKg, bodyWeightSource, bodyWeightSourceDate, createdAt, updatedAt, activeLock)
                VALUES (1, 'Push A', 'COMPLETED', '2026-09-14', 1000, 2000, NULL, 'done', 88.3, 'MEASURED_SAME_DAY', '2026-09-14', 1000, 2000, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_session_exercises (sessionId, exerciseId, position, name, category, movementPattern,
                    measurementType, resistanceBasis, weightInterpretation, primaryMuscle, notes)
                VALUES (1, 1, 0, 'Húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS', 'BODYWEIGHT', 'NOT_APPLICABLE', 'LATS', NULL)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO workout_session_exercise_muscles (sessionExerciseId, muscleGroup, role) VALUES (1, 'LATS', 'PRIMARY'), (1, 'BICEPS', 'SECONDARY')"
            )
            execSQL(
                """
                INSERT INTO workout_session_sets (sessionExerciseId, position, plannedMinReps, plannedMaxReps, plannedLoadKind,
                    plannedWeightKg, plannedDurationSeconds, plannedDistanceMeters, actualReps, actualLoadKind, actualWeightKg,
                    actualDurationSeconds, actualDistanceMeters, status, completedAt, addedDuringWorkout)
                VALUES (1, 0, 8, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 'COMPLETED', 2000, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_sessions (templateId, templateName, status, workoutDate, startedAt, finishedAt, abandonedAt,
                    notes, bodyWeightKg, bodyWeightSource, bodyWeightSourceDate, createdAt, updatedAt, activeLock)
                VALUES (1, 'Push A', 'IN_PROGRESS', '2026-09-15', 3000, NULL, NULL, NULL, NULL, 'UNKNOWN', NULL, 3000, 3000, 1)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_session_exercises (sessionId, exerciseId, position, name, category, movementPattern,
                    measurementType, resistanceBasis, weightInterpretation, primaryMuscle, notes)
                VALUES (2, 1, 0, 'Húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS', 'BODYWEIGHT', 'NOT_APPLICABLE', 'LATS', NULL)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO workout_session_exercise_muscles (sessionExerciseId, muscleGroup, role) VALUES (2, 'LATS', 'PRIMARY')"
            )
            execSQL(
                """
                INSERT INTO workout_session_sets (sessionExerciseId, position, plannedMinReps, plannedMaxReps, plannedLoadKind,
                    plannedWeightKg, plannedDurationSeconds, plannedDistanceMeters, actualReps, actualLoadKind, actualWeightKg,
                    actualDurationSeconds, actualDistanceMeters, status, completedAt, addedDuringWorkout)
                VALUES (2, 0, 8, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 'PENDING', NULL, 0)
                """.trimIndent()
            )
            close()
        }

        val database = Room.databaseBuilder(context, WeightDatabase::class.java, V4_DB)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(88.3, database.weightMeasurementDao().getByDate("2026-09-15")!!.weightKg, 0.0)
            val sessions = database.workoutSessionDao().observeAll().first()
            assertEquals(2, sessions.size)
            val completed = sessions.single { it.status == "COMPLETED" }
            val active = sessions.single { it.status == "IN_PROGRESS" }
            assertEquals(1L, completed.id)
            assertEquals(2L, active.id)
            assertEquals(1L, completed.templateId)
            assertEquals(1L, active.templateId)
            assertNull(completed.importFingerprint)
            assertNull(active.importFingerprint)
            assertNull(completed.scheduledWorkoutId)
            assertNull(active.scheduledWorkoutId)
            assertEquals("done", completed.notes)
            assertEquals(88.3, completed.bodyWeightKg)
            assertEquals(1, database.workoutSessionDao().getExercises(1).size)
            assertEquals(2, database.workoutSessionDao().getMuscles(1).size)
            assertEquals("COMPLETED", database.workoutSessionDao().getSets(1).single().status)
            assertEquals("PENDING", database.workoutSessionDao().getSets(2).single().status)
            val fk = database.openHelper.readableDatabase.query("PRAGMA foreign_key_check")
            assertFalse(fk.moveToFirst())
            fk.close()
            val indexes = mutableListOf<String>()
            val indexCursor = database.openHelper.readableDatabase.query("PRAGMA index_list('workout_sessions')")
            while (indexCursor.moveToNext()) {
                indexes += indexCursor.getString(indexCursor.getColumnIndexOrThrow("name"))
            }
            indexCursor.close()
            assertTrue(indexes.contains("index_workout_sessions_importFingerprint"))
            assertTrue(indexes.contains("index_workout_sessions_activeLock"))
            assertTrue(indexes.contains("index_workout_sessions_scheduledWorkoutId"))
            val uniqueCursor = database.openHelper.readableDatabase.query("PRAGMA index_list('workout_sessions')")
            var fingerprintUnique = false
            while (uniqueCursor.moveToNext()) {
                if (uniqueCursor.getString(uniqueCursor.getColumnIndexOrThrow("name")) == "index_workout_sessions_importFingerprint") {
                    fingerprintUnique = uniqueCursor.getInt(uniqueCursor.getColumnIndexOrThrow("unique")) == 1
                }
            }
            uniqueCursor.close()
            assertTrue(fingerprintUnique)
            val info = database.openHelper.readableDatabase.query("PRAGMA table_info('workout_sessions')")
            var templateNotNull = true
            while (info.moveToNext()) {
                if (info.getString(1) == "templateId") {
                    templateNotNull = info.getInt(3) == 1
                }
            }
            info.close()
            assertFalse(templateNotNull)
        } finally {
            database.close()
        }
    }

    @Test
    fun migratedSchemaMatchesFreshVersion6Install() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(FRESH_DB)
        context.deleteDatabase(MIGRATED_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(MIGRATED_DB)
                .callback(Version4Callback())
                .build()
        )
        helper.writableDatabase.close()
        val migrated = Room.databaseBuilder(context, WeightDatabase::class.java, MIGRATED_DB)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        val fresh = Room.databaseBuilder(context, WeightDatabase::class.java, FRESH_DB)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(sessionColumns(fresh), sessionColumns(migrated))
            assertEquals(sessionIndexes(fresh), sessionIndexes(migrated))
            assertEquals(scheduledColumns(fresh), scheduledColumns(migrated))
            assertEquals(scheduledIndexes(fresh), scheduledIndexes(migrated))
        } finally {
            migrated.close()
            fresh.close()
        }
    }

    @Test
    fun migrationFromVersion5PreservesSessionsAndAddsScheduledWorkouts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(V5_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(V5_DB)
                .callback(Version5Callback())
                .build()
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO weight_measurements (date, weightKg, createdAt, updatedAt) VALUES ('2026-09-15', 88.3, 10, 20)")
            execSQL(
                """
                INSERT INTO exercises (name, normalizedName, category, movementPattern, measurementType,
                    resistanceBasis, weightInterpretation, notes, archived, createdAt, updatedAt)
                VALUES ('Húzódzkodás', 'húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS',
                    'BODYWEIGHT', 'NOT_APPLICABLE', NULL, 0, 10, 20)
                """.trimIndent()
            )
            execSQL("INSERT INTO exercise_muscles (exerciseId, muscleGroup, role) VALUES (1, 'LATS', 'PRIMARY')")
            execSQL(
                "INSERT INTO workout_templates (name, normalizedName, notes, archived, createdAt, updatedAt) VALUES ('Push A', 'push a', NULL, 0, 10, 20)"
            )
            execSQL(
                "INSERT INTO workout_templates (name, normalizedName, notes, archived, createdAt, updatedAt) VALUES ('Pull A', 'pull a', NULL, 0, 11, 21)"
            )
            execSQL(
                "INSERT INTO workout_template_exercises (templateId, exerciseId, position, notes, createdAt) VALUES (1, 1, 0, NULL, 10)"
            )
            execSQL(
                """
                INSERT INTO workout_template_sets (templateExerciseId, position, minReps, maxReps, loadKind, weightKg, durationSeconds, distanceMeters)
                VALUES (1, 0, 8, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_sessions (templateId, templateName, status, workoutDate, startedAt, finishedAt, abandonedAt,
                    notes, bodyWeightKg, bodyWeightSource, bodyWeightSourceDate, createdAt, updatedAt, activeLock, importFingerprint)
                VALUES (1, 'Push A', 'COMPLETED', '2026-09-14', 1000, 2000, NULL, 'done', 88.3, 'MEASURED_SAME_DAY', '2026-09-14', 1000, 2000, NULL, 'fp-1')
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_session_exercises (sessionId, exerciseId, position, name, category, movementPattern,
                    measurementType, resistanceBasis, weightInterpretation, primaryMuscle, notes)
                VALUES (1, 1, 0, 'Húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS', 'BODYWEIGHT', 'NOT_APPLICABLE', 'LATS', NULL)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO workout_session_exercise_muscles (sessionExerciseId, muscleGroup, role) VALUES (1, 'LATS', 'PRIMARY')"
            )
            execSQL(
                """
                INSERT INTO workout_session_sets (sessionExerciseId, position, plannedMinReps, plannedMaxReps, plannedLoadKind,
                    plannedWeightKg, plannedDurationSeconds, plannedDistanceMeters, actualReps, actualLoadKind, actualWeightKg,
                    actualDurationSeconds, actualDistanceMeters, status, completedAt, addedDuringWorkout)
                VALUES (1, 0, 8, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 'COMPLETED', 2000, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_sessions (templateId, templateName, status, workoutDate, startedAt, finishedAt, abandonedAt,
                    notes, bodyWeightKg, bodyWeightSource, bodyWeightSourceDate, createdAt, updatedAt, activeLock, importFingerprint)
                VALUES (1, 'Push A', 'IN_PROGRESS', '2026-09-15', 3000, NULL, NULL, NULL, NULL, 'UNKNOWN', NULL, 3000, 3000, 1, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO workout_session_exercises (sessionId, exerciseId, position, name, category, movementPattern,
                    measurementType, resistanceBasis, weightInterpretation, primaryMuscle, notes)
                VALUES (2, 1, 0, 'Húzódzkodás', 'STRENGTH', 'VERTICAL_PULL', 'REPETITIONS', 'BODYWEIGHT', 'NOT_APPLICABLE', 'LATS', NULL)
                """.trimIndent()
            )
            execSQL(
                "INSERT INTO workout_session_exercise_muscles (sessionExerciseId, muscleGroup, role) VALUES (2, 'LATS', 'PRIMARY')"
            )
            execSQL(
                """
                INSERT INTO workout_session_sets (sessionExerciseId, position, plannedMinReps, plannedMaxReps, plannedLoadKind,
                    plannedWeightKg, plannedDurationSeconds, plannedDistanceMeters, actualReps, actualLoadKind, actualWeightKg,
                    actualDurationSeconds, actualDistanceMeters, status, completedAt, addedDuringWorkout)
                VALUES (2, 0, 8, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 8, 'BODYWEIGHT_ONLY', NULL, NULL, NULL, 'PENDING', NULL, 0)
                """.trimIndent()
            )
            close()
        }

        val database = Room.databaseBuilder(context, WeightDatabase::class.java, V5_DB)
            .addMigrations(MIGRATION_5_6)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(6, database.openHelper.readableDatabase.version)
            assertEquals(88.3, database.weightMeasurementDao().getByDate("2026-09-15")!!.weightKg, 0.0)
            assertEquals("Húzódzkodás", database.exerciseDao().getById(1)!!.name)
            assertEquals("Push A", database.workoutTemplateDao().getById(1)!!.name)
            val session = database.workoutSessionDao().getById(1)!!
            assertEquals("fp-1", session.importFingerprint)
            assertNull(session.scheduledWorkoutId)
            assertEquals("done", session.notes)
            assertEquals(1, database.workoutSessionDao().getExercises(1).size)
            val active = database.workoutSessionDao().getById(2)!!
            assertEquals("IN_PROGRESS", active.status)
            assertNull(active.scheduledWorkoutId)
            assertEquals(1, database.workoutSessionDao().getExercises(2).size)
            assertEquals(1, database.workoutSessionDao().getMuscles(1).size)
            assertTrue(database.scheduledWorkoutDao().observeOnDate("2026-09-15").first().isEmpty())
            val sessionIdx = sessionIndexes(database)
            assertTrue(sessionIdx.contains("index_workout_sessions_scheduledWorkoutId:1"))
            val scheduledIdx = scheduledIndexes(database)
            assertTrue(scheduledIdx.contains("index_scheduled_workouts_scheduledDate_templateId:1"))
            val sessionColumn = tableColumns(database, "workout_sessions").first { it.startsWith("scheduledWorkoutId:") }
            assertTrue(sessionColumn.startsWith("scheduledWorkoutId:INTEGER:0"))

            val firstId = database.scheduledWorkoutDao().insert(
                ScheduledWorkoutEntity(
                    scheduledDate = "2026-09-15",
                    templateId = 1L,
                    createdAt = 50L
                )
            )
            database.scheduledWorkoutDao().insert(
                ScheduledWorkoutEntity(
                    scheduledDate = "2026-09-15",
                    templateId = 2L,
                    createdAt = 51L
                )
            )
            var duplicateFailed = false
            try {
                database.scheduledWorkoutDao().insert(
                    ScheduledWorkoutEntity(
                        scheduledDate = "2026-09-15",
                        templateId = 1L,
                        createdAt = 52L
                    )
                )
            } catch (_: Exception) {
                duplicateFailed = true
            }
            assertTrue(duplicateFailed)

            var missingTemplateFailed = false
            try {
                database.scheduledWorkoutDao().insert(
                    ScheduledWorkoutEntity(
                        scheduledDate = "2026-09-16",
                        templateId = 999L,
                        createdAt = 53L
                    )
                )
            } catch (_: Exception) {
                missingTemplateFailed = true
            }
            assertTrue(missingTemplateFailed)

            val clock = java.time.Clock.fixed(java.time.Instant.ofEpochMilli(5_000L), java.time.ZoneOffset.UTC)
            val dateProvider = hu.laca.weighttracker.domain.FixedDateProvider(java.time.LocalDate.parse("2026-09-15"))
            val sessionRepository = hu.laca.weighttracker.data.repository.WorkoutSessionRepository(
                sessionDao = database.workoutSessionDao(),
                templateDao = database.workoutTemplateDao(),
                exerciseDao = database.exerciseDao(),
                weightRepository = hu.laca.weighttracker.data.repository.WeightRepository(
                    database.weightMeasurementDao(),
                    clock
                ),
                clock = clock,
                dateProvider = dateProvider
            )
            assertEquals(
                hu.laca.weighttracker.domain.workout.AbandonWorkoutResult.Abandoned,
                sessionRepository.abandon(2L)
            )
            assertNull(database.workoutSessionDao().getById(2))
            val started = sessionRepository.start(1L, firstId)
            assertTrue(started is hu.laca.weighttracker.domain.workout.StartWorkoutResult.Started)
            val startedId = (started as hu.laca.weighttracker.domain.workout.StartWorkoutResult.Started).sessionId
            assertEquals(firstId, database.workoutSessionDao().getById(startedId)!!.scheduledWorkoutId)
            assertNull(database.workoutSessionDao().getById(1)!!.scheduledWorkoutId)

            var deleteLinkedFailed = false
            try {
                database.scheduledWorkoutDao().deleteById(firstId)
            } catch (_: Exception) {
                deleteLinkedFailed = true
            }
            assertTrue(deleteLinkedFailed)
            assertEquals(firstId, database.workoutSessionDao().getById(startedId)!!.scheduledWorkoutId)

            var secondLinkFailed = false
            try {
                database.workoutSessionDao().insertSession(
                    WorkoutSessionEntity(
                        templateId = 1L,
                        templateName = "Push A",
                        status = "COMPLETED",
                        workoutDate = "2026-09-16",
                        startedAt = 3L,
                        finishedAt = 4L,
                        abandonedAt = null,
                        notes = null,
                        bodyWeightKg = null,
                        bodyWeightSource = "UNKNOWN",
                        bodyWeightSourceDate = null,
                        createdAt = 3L,
                        updatedAt = 4L,
                        activeLock = null,
                        scheduledWorkoutId = firstId
                    )
                )
            } catch (_: Exception) {
                secondLinkFailed = true
            }
            assertTrue(secondLinkFailed)

            val fk = database.openHelper.readableDatabase.query("PRAGMA foreign_key_check")
            assertFalse(fk.moveToFirst())
            fk.close()
        } finally {
            database.close()
        }
    }

    @Test
    fun existingVersion6DatabaseOpensWithoutMigration() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(CURRENT_V6_REOPEN_DB)
        val created = Room.databaseBuilder(context, WeightDatabase::class.java, CURRENT_V6_REOPEN_DB)
            .allowMainThreadQueries()
            .build()
        val exerciseId = created.exerciseDao().insert(
            ExerciseEntity(
                name = "Nyomás",
                normalizedName = "nyomás",
                category = "STRENGTH",
                movementPattern = "HORIZONTAL_PUSH",
                measurementType = "REPETITIONS",
                resistanceBasis = "BODYWEIGHT",
                weightInterpretation = "NOT_APPLICABLE",
                notes = null,
                archived = false,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        created.exerciseDao().insertMuscles(
            listOf(ExerciseMuscleEntity(exerciseId, "CHEST", "PRIMARY"))
        )
        created.close()

        val reopened = Room.databaseBuilder(context, WeightDatabase::class.java, CURRENT_V6_REOPEN_DB)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(6, reopened.openHelper.readableDatabase.version)
            val stored = reopened.exerciseDao().getById(exerciseId)!!
                .toModel(reopened.exerciseDao().getMuscles(exerciseId))
            assertEquals(MuscleGroup.CHEST, stored.primaryMuscle)
            val neckId = reopened.exerciseDao().insert(
                ExerciseEntity(
                    name = "Nyakhajlítás",
                    normalizedName = "nyakhajlítás",
                    category = "STRENGTH",
                    movementPattern = "OTHER",
                    measurementType = "REPETITIONS",
                    resistanceBasis = "BODYWEIGHT",
                    weightInterpretation = "NOT_APPLICABLE",
                    notes = null,
                    archived = false,
                    createdAt = 2L,
                    updatedAt = 2L
                )
            )
            reopened.exerciseDao().insertMuscles(
                listOf(ExerciseMuscleEntity(neckId, "NECK", "PRIMARY"))
            )
            assertEquals(6, reopened.openHelper.readableDatabase.version)
            val neck = reopened.exerciseDao().getById(neckId)!!
                .toModel(reopened.exerciseDao().getMuscles(neckId))
            assertEquals(MuscleGroup.NECK, neck.primaryMuscle)
            assertEquals("NECK", reopened.exerciseDao().getMuscles(neckId).single().muscleGroup)
        } finally {
            reopened.close()
        }
    }

    private class Version1Callback : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `weight_measurements` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `date` TEXT NOT NULL,
                    `weightKg` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_measurements_date` ON `weight_measurements` (`date`)"
            )
        }

        override fun onUpgrade(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) = Unit
    }

    private class Version2Callback : SupportSQLiteOpenHelper.Callback(2) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            Version1Callback().onCreate(db)
            MIGRATION_1_2.migrate(db)
        }

        override fun onUpgrade(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) = Unit
    }

    private class Version3Callback : SupportSQLiteOpenHelper.Callback(3) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            Version2Callback().onCreate(db)
            MIGRATION_2_3.migrate(db)
        }

        override fun onUpgrade(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) = Unit
    }

    private class Version4Callback : SupportSQLiteOpenHelper.Callback(4) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            Version3Callback().onCreate(db)
            MIGRATION_3_4.migrate(db)
        }

        override fun onUpgrade(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) = Unit
    }

    private class Version5Callback : SupportSQLiteOpenHelper.Callback(5) {
        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            Version4Callback().onCreate(db)
            MIGRATION_4_5.migrate(db)
        }

        override fun onUpgrade(
            db: androidx.sqlite.db.SupportSQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) = Unit
    }

    private companion object {
        const val TEST_DB = "weight-migration-test.db"
        const val V2_DB = "weight-migration-v2.db"
        const val V3_DB = "weight-migration-v3.db"
        const val V4_DB = "weight-migration-v4.db"
        const val V5_DB = "weight-migration-v5.db"
        const val FRESH_DB = "weight-fresh-v6.db"
        const val MIGRATED_DB = "weight-migrated-v6.db"
        const val CURRENT_V6_REOPEN_DB = "weight-current-v6-reopen.db"

        fun sessionColumns(database: WeightDatabase): List<String> {
            return tableColumns(database, "workout_sessions")
        }

        fun scheduledColumns(database: WeightDatabase): List<String> {
            return tableColumns(database, "scheduled_workouts")
        }

        fun tableColumns(database: WeightDatabase, table: String): List<String> {
            val cursor = database.openHelper.readableDatabase.query("PRAGMA table_info('$table')")
            val columns = mutableListOf<String>()
            while (cursor.moveToNext()) {
                columns += listOf(
                    cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull")).toString(),
                    cursor.getInt(cursor.getColumnIndexOrThrow("pk")).toString()
                ).joinToString(":")
            }
            cursor.close()
            return columns
        }

        fun sessionIndexes(database: WeightDatabase): List<String> {
            return tableIndexes(database, "workout_sessions")
        }

        fun scheduledIndexes(database: WeightDatabase): List<String> {
            return tableIndexes(database, "scheduled_workouts")
        }

        fun tableIndexes(database: WeightDatabase, table: String): List<String> {
            val cursor = database.openHelper.readableDatabase.query("PRAGMA index_list('$table')")
            val indexes = mutableListOf<String>()
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name.startsWith("sqlite_autoindex")) {
                    continue
                }
                indexes += "$name:${cursor.getInt(cursor.getColumnIndexOrThrow("unique"))}"
            }
            cursor.close()
            return indexes.sorted()
        }
    }
}
