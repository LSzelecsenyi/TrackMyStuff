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
    fun migrationFromVersion1PreservesWeightMeasurements() = runTest {
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
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            val stored = database.weightMeasurementDao().getByDate("2026-03-11")
            assertEquals("2026-03-11", stored?.date)
            assertEquals(82.4, stored!!.weightKg, 0.0)
            assertEquals(100L, stored.createdAt)
            assertEquals(200L, stored.updatedAt)
            assertTrue(database.exerciseDao().observeAll().first().isEmpty())
            assertTrue(database.exerciseDao().observeMuscles().first().isEmpty())
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

    private companion object {
        const val TEST_DB = "weight-migration-test.db"
    }
}
