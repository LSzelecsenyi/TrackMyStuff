package app.mymusclemap.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.ProgressPhotoDao
import app.mymusclemap.data.local.ProgressPhotoEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.progress.ProgressPhotoProcessor
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.progress.ProgressPhotoImportResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ProgressPhotoRepositoryTest {
    private val today = LocalDate.of(2026, 9, 27)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-09-27T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun importScalesKeepsAspectAndAllowsSeveralPhotosOnOneDate() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = database(context)
        val directory = directory(context, "import")
        try {
            val repository = repository(database, directory)
            val wide = repository.import(jpeg(64, 32).inputStream(), today) as ProgressPhotoImportResult.Saved
            val second = repository.import(jpeg(16, 16).inputStream(), today) as ProgressPhotoImportResult.Saved
            val stored = repository.observeAll().first()
            assertEquals(listOf(second.photo.id, wide.photo.id), stored.map { it.id })
            assertTrue(stored.all { it.date == today })
            val decoded = repository.decode(wide.photo.fileName, 32)!!
            assertTrue(decoded.width <= 32)
            assertTrue(decoded.height <= 32)
            assertEquals(2.0, decoded.width.toDouble() / decoded.height.toDouble(), 0.15)
            assertTrue(File(directory, wide.photo.fileName).isFile)
            assertNull(ExifInterface(File(directory, wide.photo.fileName).absolutePath).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        } finally {
            database.close()
        }
    }

    @Test
    fun unreadableInputAndInsertFailureLeaveNoFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = directory(context, "fail")
        val database = database(context)
        try {
            val repository = repository(database, directory)
            assertTrue(repository.import(UnreadableStream()) is ProgressPhotoImportResult.Unreadable)
            assertTrue(directory.list().orEmpty().none { it.endsWith(".jpg") })

            val failingDirectory = directory(context, "insert-fail")
            val failing = ProgressPhotoRepository(
                dao = ThrowingPhotoDao(),
                store = ProgressPhotoStore(failingDirectory),
                clock = clock,
                dateProvider = dateProvider,
                maxEdge = 32
            )
            assertTrue(failing.import(jpeg(8, 8).inputStream()) is ProgressPhotoImportResult.Unreadable)
            assertTrue(failingDirectory.list().orEmpty().none { it.endsWith(".jpg") })
        } finally {
            database.close()
        }
    }

    @Test
    fun futureDateIsRejectedAndDeleteHandlesAMissingFile() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = database(context)
        val directory = directory(context, "delete")
        try {
            val repository = repository(database, directory)
            assertTrue(
                repository.import(jpeg(8, 8).inputStream(), today.plusDays(1)) is ProgressPhotoImportResult.FutureDate
            )
            assertTrue(repository.observeAll().first().isEmpty())
            val saved = repository.import(jpeg(8, 8).inputStream(), today.minusDays(3)) as ProgressPhotoImportResult.Saved
            val file = File(directory, saved.photo.fileName)
            assertTrue(file.delete())
            repository.delete(saved.photo.id)
            assertTrue(repository.observeAll().first().isEmpty())
            repository.delete(saved.photo.id)
        } finally {
            database.close()
        }
    }

    @Test
    fun sweepRemovesOrphanPhotosAndLeavesUnrelatedFiles() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = database(context)
        val directory = directory(context, "sweep")
        try {
            val repository = repository(database, directory)
            val saved = repository.import(jpeg(8, 8).inputStream(), today) as ProgressPhotoImportResult.Saved
            File(directory, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.jpg").writeBytes(jpeg(4, 4))
            File(directory, "notes.txt").writeText("keep")
            repository.sweepOrphans()
            assertTrue(File(directory, saved.photo.fileName).isFile)
            assertFalse(File(directory, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.jpg").exists())
            assertTrue(File(directory, "notes.txt").isFile)
        } finally {
            database.close()
        }
    }

    @Test
    fun orientationRotatesAndExifDateParses() {
        val source = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val rotated = ProgressPhotoProcessor.applyOrientation(source, ExifInterface.ORIENTATION_ROTATE_90)
        assertEquals(2, rotated.width)
        assertEquals(4, rotated.height)
        assertEquals(LocalDate.of(2024, 5, 6), ProgressPhotoProcessor.parseExifDate("2024:05:06 01:02:03"))
        assertEquals(4, ProgressPhotoProcessor.sampleSize(64, 32, 16))
    }

    private fun repository(database: WeightDatabase, directory: File): ProgressPhotoRepository {
        return ProgressPhotoRepository(
            dao = database.progressPhotoDao(),
            store = ProgressPhotoStore(directory),
            clock = clock,
            dateProvider = dateProvider,
            maxEdge = 32
        )
    }

    private fun database(context: Context): WeightDatabase {
        return Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun directory(context: Context, name: String): File {
        return File(context.cacheDir, "progress-photo-$name").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun jpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private class ThrowingPhotoDao : ProgressPhotoDao {
        override fun observeAll(): Flow<List<ProgressPhotoEntity>> = flowOf(emptyList())
        override suspend fun getAll(): List<ProgressPhotoEntity> = emptyList()
        override suspend fun getById(id: Long): ProgressPhotoEntity? = null
        override suspend fun insert(entity: ProgressPhotoEntity): Long {
            throw IllegalStateException("insert failed")
        }
        override suspend fun deleteById(id: Long) = Unit
    }

    private class UnreadableStream : InputStream() {
        override fun read(): Int = throw IOException("unreadable")
    }
}
