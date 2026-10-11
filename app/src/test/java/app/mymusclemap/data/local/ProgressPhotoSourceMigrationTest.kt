package app.mymusclemap.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProgressPhotoSourceMigrationTest {
    @Test
    fun version15PhotosGainNullableSourceColumnsWithoutChangingTheRow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(NAME)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE progress_photos (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                date TEXT NOT NULL,
                                fileName TEXT NOT NULL,
                                createdAt INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL
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
            INSERT INTO progress_photos (date, fileName, createdAt, updatedAt)
            VALUES ('2026-01-02', '11111111-1111-1111-1111-111111111111.jpg', 5, 6)
            """.trimIndent()
        )
        assertEquals(15, database.version)
        MIGRATION_15_16.migrate(database)
        database.query(
            "SELECT date, fileName, createdAt, updatedAt, sourceUri, sourceFingerprint FROM progress_photos"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-01-02", cursor.getString(0))
            assertEquals("11111111-1111-1111-1111-111111111111.jpg", cursor.getString(1))
            assertEquals(5, cursor.getLong(2))
            assertEquals(6, cursor.getLong(3))
            assertTrue(cursor.isNull(4))
            assertTrue(cursor.isNull(5))
        }
        database.close()
    }

    private companion object {
        const val NAME = "progress-photo-source-migration"
    }
}
