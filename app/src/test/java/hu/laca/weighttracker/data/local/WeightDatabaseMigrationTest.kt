package hu.laca.weighttracker.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
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
            .addMigrations(MIGRATION_3_4)
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

    private companion object {
        const val TEST_DB = "weight-migration-test.db"
        const val V2_DB = "weight-migration-v2.db"
        const val V3_DB = "weight-migration-v3.db"
    }
}
