package app.mymusclemap.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RestoreSidecarTest {
    @Test
    fun replacedDatabaseDropsSidecarsThatThisRestoreDidNotWrite() {
        val directory = tempDir()
        val database = accountFile(directory, FIRST_ACCOUNT)
        val wal = sidecar(database, "-wal")
        val shm = sidecar(database, "-shm")
        val journal = sidecar(database, "-journal")

        discardUnrestoredAccountSidecars(listOf(database))

        assertTrue(database.isFile)
        assertFalse(wal.exists())
        assertFalse(shm.exists())
        assertFalse(journal.exists())
    }

    @Test
    fun restoredWalAndSharedMemoryStayEvenWhenTheHeaderSaysRollback() {
        val directory = tempDir()
        val database = accountFile(directory, FIRST_ACCOUNT, writeVersion = 1)
        val wal = sidecar(database, "-wal")
        val shm = sidecar(database, "-shm")

        discardUnrestoredAccountSidecars(listOf(database, wal, shm))

        assertTrue(database.isFile)
        assertTrue(wal.isFile)
        assertTrue(shm.isFile)
    }

    @Test
    fun restoredWalModeDatabaseKeepsItsSidecars() {
        val directory = tempDir()
        val database = accountFile(directory, FIRST_ACCOUNT, writeVersion = 2)
        val wal = sidecar(database, "-wal")
        val shm = sidecar(database, "-shm")
        val journal = sidecar(database, "-journal")

        discardUnrestoredAccountSidecars(listOf(database, wal, shm, journal))

        assertTrue(wal.isFile)
        assertTrue(shm.isFile)
        assertTrue(journal.isFile)
    }

    @Test
    fun unrelatedAccountDatabaseAndItsSidecarsStay() {
        val directory = tempDir()
        val replaced = accountFile(directory, FIRST_ACCOUNT, writeVersion = 1)
        val replacedWal = sidecar(replaced, "-wal")
        val other = accountFile(directory, SECOND_ACCOUNT, writeVersion = 1)
        val otherWal = sidecar(other, "-wal")
        val otherShm = sidecar(other, "-shm")
        val otherJournal = sidecar(other, "-journal")

        discardUnrestoredAccountSidecars(listOf(replaced))

        assertFalse(replacedWal.exists())
        assertTrue(other.isFile)
        assertTrue(otherWal.isFile)
        assertTrue(otherShm.isFile)
        assertTrue(otherJournal.isFile)
    }

    @Test
    fun nonAccountFilesStay() {
        val directory = tempDir()
        val notes = File(directory, "notes.db")
        notes.writeBytes(byteArrayOf(1, 2, 3))
        val wal = sidecar(notes, "-wal")

        discardUnrestoredAccountSidecars(listOf(notes))

        assertTrue(notes.isFile)
        assertTrue(wal.isFile)
    }

    private fun accountFile(directory: File, userId: String, writeVersion: Int? = null): File {
        val file = File(directory, "account_$userId.db")
        file.writeBytes(if (writeVersion == null) byteArrayOf(9, 9, 9) else sqliteHeader(writeVersion))
        return file
    }

    private fun sidecar(database: File, suffix: String): File {
        return File(database.parentFile, database.name + suffix).also { it.writeBytes(byteArrayOf(1)) }
    }

    private fun sqliteHeader(writeVersion: Int): ByteArray {
        val header = ByteArray(100)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(header)
        header[18] = writeVersion.toByte()
        return header
    }

    private fun tempDir(): File = Files.createTempDirectory("restore-sidecars").toFile()

    private companion object {
        const val FIRST_ACCOUNT = "8b26550a-0cb2-4b49-a1b9-a536b479bbe7"
        const val SECOND_ACCOUNT = "11111111-2222-3333-4444-555555555555"
    }
}
