package app.mymusclemap.data.account

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AccountLocalDataEraserTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun eraseRemovesOnlyTheRequestedAccount() {
        val deleted = UUID.randomUUID().toString()
        val kept = UUID.randomUUID().toString()
        val deletedDatabase = writeDatabase(deleted)
        val keptDatabase = writeDatabase(kept)
        val deletedPhotos = writePhotos(deleted)
        val keptPhotos = writePhotos(kept)
        val deletedPrefs = writeDatastore("pro_discovery_$deleted.preferences_pb")
        val keptPrefs = writeDatastore("pro_discovery_$kept.preferences_pb")
        val theme = writeDatastore("weight_tracker_settings.preferences_pb")
        val language = writeDatastore("app_language.preferences_pb")
        val outbox = writeDatastore("founder_workout_outbox.preferences_pb")
        val exported = File(context.cacheDir, "exported-backup.zip")
        exported.writeText("zip")

        val eraser = AccountLocalDataEraser(context)
        assertTrue(eraser.markPending(deleted))
        assertEquals(deleted, eraser.pendingUserId())
        assertTrue(eraser.erase(deleted))

        assertFalse(deletedDatabase.exists())
        assertFalse(deletedPhotos.exists())
        assertFalse(deletedPrefs.exists())
        assertFalse(outbox.exists())
        assertTrue(keptDatabase.exists())
        assertTrue(keptPhotos.exists())
        assertTrue(keptPrefs.exists())
        assertTrue(theme.exists())
        assertTrue(language.exists())
        assertTrue(exported.exists())
        eraser.clearPending()
        assertNull(eraser.pendingUserId())
    }

    @Test
    fun anInvalidUserIdDoesNotErase() {
        val kept = UUID.randomUUID().toString()
        val database = writeDatabase(kept)
        val eraser = AccountLocalDataEraser(context)
        assertFalse(eraser.erase("not-a-user"))
        assertFalse(eraser.markPending("../other"))
        assertTrue(database.exists())
    }

    private fun writeDatabase(userId: String): File {
        val file = context.getDatabasePath("account_$userId.db")
        file.parentFile?.mkdirs()
        file.writeText("db")
        File(file.path + "-wal").writeText("wal")
        return file
    }

    private fun writePhotos(userId: String): File {
        val directory = File(context.filesDir, "progress_photos_$userId")
        directory.mkdirs()
        File(directory, "photo.jpg").writeText("photo")
        return directory
    }

    private fun writeDatastore(name: String): File {
        val directory = File(context.filesDir, "datastore")
        directory.mkdirs()
        val file = File(directory, name)
        file.writeText("prefs")
        return file
    }
}
