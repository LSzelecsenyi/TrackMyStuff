package app.mymusclemap.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.account.AccountLocalDataEraser
import app.mymusclemap.domain.account.accountDatabaseName
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class AccountDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userA = "11111111-1111-1111-1111-111111111111"
    private val userB = "22222222-2222-2222-2222-222222222222"
    private val nameA = accountDatabaseName(userA)
    private val nameB = accountDatabaseName(userB)

    @Before
    fun clean() {
        listOf(nameA, nameB).forEach { name ->
            AccountDatabaseLocation.legacyAccountFile(context, name).delete()
            AccountDatabaseLocation.accountFile(context, name).delete()
            File(context.noBackupFilesDir, "migrated_$name").delete()
        }
        AccountDatabaseLocation.legacyUnclaimedFile(context).delete()
        context.getDatabasePath("androidx.work.workdb").delete()
    }

    @Test
    fun walRowsMoveAfterAVerifiedCopy() {
        val source = AccountDatabaseLocation.legacyAccountFile(context, nameA)
        source.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(source, null)
        database.rawQuery("PRAGMA journal_mode=WAL", null).use { cursor -> cursor.moveToFirst() }
        database.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { cursor -> cursor.moveToFirst() }
        database.execSQL("CREATE TABLE notes (body TEXT NOT NULL)")
        database.execSQL("INSERT INTO notes (body) VALUES ('from-wal')")
        database.close()

        AccountDatabaseMigrator(context).migrateNamed(nameA)

        assertFalse(source.exists())
        assertFalse(File(source.path + "-wal").exists())
        val destination = AccountDatabaseLocation.accountFile(context, nameA)
        assertTrue(destination.isFile)
        assertTrue(sqliteIntegrityOk(destination))
        SQLiteDatabase.openDatabase(destination.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { opened ->
            opened.rawQuery("SELECT body FROM notes", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("from-wal", cursor.getString(0))
            }
        }
        assertEquals(context.filesDir, destination.parentFile?.parentFile)
    }

    @Test
    fun aFailedMigrationLeavesTheOriginalAndDoesNotCreateAnEmptyDatabase() {
        val source = AccountDatabaseLocation.legacyAccountFile(context, nameA)
        source.parentFile?.mkdirs()
        source.writeText("not a database")
        val destination = AccountDatabaseLocation.accountFile(context, nameA)

        try {
            AccountDatabaseMigrator(context).migrateNamed(nameA)
            fail("migration should fail")
        } catch (error: AccountDatabaseMigrationException) {
            assertEquals(nameA, error.fileName)
        }

        assertEquals("not a database", source.readText())
        assertFalse(destination.exists())
        assertFalse(File(AccountDatabaseLocation.databasesDirectory(context), "$nameA.partial").exists())
    }

    @Test
    fun bothCopiesWithoutACompletedMarkerAreLeftUntouched() {
        val source = AccountDatabaseLocation.legacyAccountFile(context, nameA)
        val destination = AccountDatabaseLocation.accountFile(context, nameA)
        writeSqlite(source, "source-row")
        writeSqlite(destination, "dest-row")

        try {
            AccountDatabaseMigrator(context).migrateNamed(nameA)
            fail("coexisting copies should stop the migration")
        } catch (error: AccountDatabaseMigrationException) {
            assertTrue(error.message.orEmpty().contains("both"))
        }

        assertEquals("source-row", readNote(source))
        assertEquals("dest-row", readNote(destination))
    }

    @Test
    fun incompleteDestinationIsReplacedFromTheSource() {
        val source = AccountDatabaseLocation.legacyAccountFile(context, nameA)
        val destination = AccountDatabaseLocation.accountFile(context, nameA)
        writeSqlite(source, "kept")
        destination.parentFile?.mkdirs()
        destination.writeText("partial")
        File(context.noBackupFilesDir, "migrated_$nameA").writeText("ok")

        AccountDatabaseMigrator(context).migrateNamed(nameA)

        assertFalse(source.exists())
        assertEquals("kept", readNote(destination))
    }

    @Test
    fun anIncompleteCheckpointIsNotTreatedAsCommitted() {
        val checkpoint = WalCheckpoint(busy = 1, log = 4, checkpointed = 2)
        assertFalse(checkpoint.complete)
        try {
            requireCompleteCheckpoint(File(nameA), checkpoint)
            fail("incomplete checkpoint should fail")
        } catch (error: IOException) {
            assertTrue(error.message.orEmpty().contains("busy=1"))
        }
    }

    @Test
    fun legacyUnclaimedDataAndWorkManagerStayOutsideTheBackupDirectory() {
        val legacy = AccountDatabaseLocation.legacyUnclaimedFile(context)
        legacy.parentFile?.mkdirs()
        legacy.writeText("unclaimed")
        val work = context.getDatabasePath("androidx.work.workdb")
        work.writeText("work")

        AccountDatabaseMigrator(context).migrateAll()

        assertTrue(legacy.isFile)
        assertTrue(work.isFile)
        val backedUp = AccountDatabaseLocation.userDataDirectory(context)
        assertFalse(File(backedUp, legacy.name).exists())
        assertFalse(File(backedUp, work.name).exists())
        assertEquals(legacy.parentFile, work.parentFile)
        assertNotEquals(legacy.parentFile, backedUp)
    }

    @Test
    fun newerSchemaIsNotReplacedWithAnEmptyDatabase() = runTest {
        val created = WeightDatabase.create(context, nameA, allowMainThreadQueries = true)
        created.weightMeasurementDao().getAllAscending()
        created.close()
        val file = AccountDatabaseLocation.accountFile(context, nameA)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.version = 99
        }

        try {
            val reopened = WeightDatabase.create(context, nameA, allowMainThreadQueries = true)
            reopened.weightMeasurementDao().getAllAscending()
            reopened.close()
            fail("a newer schema should not open")
        } catch (error: Throwable) {
            assertEquals(DatabaseOpenProblem.NewerSchema, databaseOpenProblemFor(error))
        }

        assertTrue(file.isFile)
        assertEquals(99, sqliteUserVersion(file))
    }

    @Test
    fun eachAccountOpensOnlyItsOwnDatabase() = runTest {
        val first = WeightDatabase.create(context, nameA, allowMainThreadQueries = true)
        first.weightMeasurementDao().insert(
            WeightMeasurementEntity(date = "2026-02-02", weightKg = 80.0, createdAt = 1, updatedAt = 1)
        )
        first.close()
        val second = WeightDatabase.create(context, nameB, allowMainThreadQueries = true)
        assertTrue(second.weightMeasurementDao().getAllAscending().isEmpty())
        second.close()
        val again = WeightDatabase.create(context, nameA, allowMainThreadQueries = true)
        assertEquals(80.0, again.weightMeasurementDao().getByDate("2026-02-02")!!.weightKg, 0.0)
        again.close()
        assertNotEquals(
            AccountDatabaseLocation.accountFile(context, nameA).absolutePath,
            AccountDatabaseLocation.accountFile(context, nameB).absolutePath
        )
    }

    @Test
    fun restoredDatabaseIsNotOpenedForANewUser() = runTest {
        val original = WeightDatabase.create(context, nameA, allowMainThreadQueries = true)
        original.weightMeasurementDao().insert(
            WeightMeasurementEntity(date = "2026-03-03", weightKg = 70.0, createdAt = 2, updatedAt = 2)
        )
        original.close()
        val saved = File(context.cacheDir, "restored-account.db")
        AccountDatabaseLocation.accountFile(context, nameA).copyTo(saved, overwrite = true)
        assertTrue(AccountLocalDataEraser(context).erase(userA))
        assertFalse(AccountDatabaseLocation.accountFile(context, nameA).exists())

        saved.copyTo(AccountDatabaseLocation.accountFile(context, nameA), overwrite = true)
        val created = WeightDatabase.create(context, nameB, allowMainThreadQueries = true)
        assertTrue(created.weightMeasurementDao().getAllAscending().isEmpty())
        created.close()
        assertEquals("2026-03-03", readWeightDate(AccountDatabaseLocation.accountFile(context, nameA)))
        assertTrue(AccountDatabaseLocation.accountFile(context, nameB).isFile)
    }

    private fun writeSqlite(file: File, note: String) {
        file.parentFile?.mkdirs()
        val database = SQLiteDatabase.openOrCreateDatabase(file, null)
        database.execSQL("CREATE TABLE notes (body TEXT NOT NULL)")
        database.execSQL("INSERT INTO notes (body) VALUES (?)", arrayOf(note))
        database.close()
    }

    private fun readNote(file: File): String {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { opened ->
            opened.rawQuery("SELECT body FROM notes", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                return cursor.getString(0)
            }
        }
    }

    private fun readWeightDate(file: File): String {
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { opened ->
            opened.rawQuery("SELECT date FROM weight_measurements", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                return cursor.getString(0)
            }
        }
    }
}
