package app.mymusclemap.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExtraExerciseOriginMigrationTest {
    @Test
    fun existingSessionExercisesStayPlanned() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE workout_session_exercises (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                sessionId INTEGER NOT NULL,
                                exerciseId INTEGER NOT NULL,
                                position INTEGER NOT NULL,
                                name TEXT NOT NULL,
                                category TEXT NOT NULL,
                                movementPattern TEXT NOT NULL,
                                measurementType TEXT NOT NULL,
                                resistanceBasis TEXT NOT NULL,
                                weightInterpretation TEXT NOT NULL,
                                primaryMuscle TEXT NOT NULL,
                                notes TEXT
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val database = helper.writableDatabase
        database.execSQL(
            """
            INSERT INTO workout_session_exercises (
                sessionId, exerciseId, position, name, category, movementPattern,
                measurementType, resistanceBasis, weightInterpretation, primaryMuscle, notes
            ) VALUES (1, 2, 0, 'Bench', 'STRENGTH', 'HORIZONTAL_PUSH', 'REPETITIONS_AND_WEIGHT',
                'EXTERNAL', 'TOTAL', 'CHEST', NULL)
            """.trimIndent()
        )
        assertEquals(16, database.version)
        MIGRATION_16_17.migrate(database)
        database.query("SELECT addedDuringWorkout, name FROM workout_session_exercises").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals("Bench", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
        database.close()
    }

    private companion object {
        const val NAME = "extra-exercise-origin-migration"
    }
}
