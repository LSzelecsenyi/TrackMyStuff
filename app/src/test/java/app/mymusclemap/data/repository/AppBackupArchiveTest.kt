package app.mymusclemap.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupErrorCode
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupParseResult
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.appbackup.emptyTables
import app.mymusclemap.data.local.ProgressPhotoEntity
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.progress.ProgressPhotoStore
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class AppBackupArchiveTest {
    private lateinit var context: Context
    private lateinit var database: WeightDatabase
    private lateinit var preferences: ThemePreferences
    private lateinit var photoDirectory: File
    private lateinit var store: ProgressPhotoStore

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = ThemePreferences(context)
        photoDirectory = File(context.cacheDir, "archive-photos-${System.nanoTime()}").apply { mkdirs() }
        store = ProgressPhotoStore(photoDirectory)
    }

    @After
    fun tearDown() {
        database.close()
        photoDirectory.parentFile?.listFiles()
            ?.filter { it.name.startsWith("archive-photos-") || it.name.startsWith("progress_photos") }
            ?.forEach { it.deleteRecursively() }
    }

    @Test
    fun archiveRoundTripRestoresSeveralPhotosAndReplacesThePreviousLibrary() = runTest {
        val keptWeight = WeightMeasurementEntity(id = 7, date = "2026-09-01", weightKg = 80.0, createdAt = 1, updatedAt = 1)
        database.appBackupDao().replaceAll(emptyTables().copy(weightMeasurements = listOf(keptWeight)))
        val first = savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val second = savePhoto("22222222-2222-2222-2222-222222222222.jpg", "2026-09-02", Color.BLUE)
        val exported = ByteArrayOutputStream()
        val written = repository().exportArchive(source(), exported)
        assertEquals(app.mymusclemap.data.appbackup.AppBackupWriteResult.Written(0), written)

        database.appBackupDao().replaceAll(
            emptyTables().copy(
                weightMeasurements = listOf(
                    WeightMeasurementEntity(id = 9, date = "2026-01-01", weightKg = 50.0, createdAt = 1, updatedAt = 1)
                )
            )
        )
        database.progressPhotoDao().insert(photo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg", "2026-01-01"))
        database.progressPhotoDao().insert(photo("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg", "2026-01-02"))
        File(photoDirectory, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg").writeBytes(jpeg(Color.GREEN))
        File(photoDirectory, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg").writeBytes(jpeg(Color.YELLOW))

        val restored = repository().restore(ByteArrayInputStream(exported.toByteArray()))
        assertEquals(AppBackupRestoreResult.Success, restored)
        assertEquals(listOf(keptWeight), database.appBackupDao().loadTables().weightMeasurements)
        val photos = database.progressPhotoDao().getAll().sortedBy { it.fileName }
        assertEquals(listOf(first.fileName, second.fileName), photos.map { it.fileName })
        assertEquals(listOf(first.id, second.id), photos.map { it.id })
        assertArrayEquals(photoBytes.getValue(first.fileName), File(photoDirectory, first.fileName).readBytes())
        assertArrayEquals(photoBytes.getValue(second.fileName), File(photoDirectory, second.fileName).readBytes())
        assertFalse(File(photoDirectory, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg").exists())
        assertFalse(File(photoDirectory, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb.jpg").exists())
        assertFalse(store.restoreMarker().exists())
        assertFalse(store.stagingDirectory().exists())
    }

    @Test
    fun exportSkipsAPhotoWhoseFileIsMissing() = runTest {
        val present = savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        database.progressPhotoDao().insert(photo("22222222-2222-2222-2222-222222222222.jpg", "2026-09-02"))
        val exported = ByteArrayOutputStream()
        val written = repository().exportArchive(source(), exported)
        assertEquals(app.mymusclemap.data.appbackup.AppBackupWriteResult.Written(1), written)
        val names = zipNames(exported.toByteArray())
        assertTrue(names.contains("progress_photos/${present.fileName}"))
        assertFalse(names.any { it.contains("22222222-2222-2222-2222-222222222222") })
        val data = zipText(exported.toByteArray(), AppBackupFormat.ARCHIVE_DATA)
        assertFalse(data.contains("22222222-2222-2222-2222-222222222222"))
        assertEquals(1, JSONObject(zipText(exported.toByteArray(), AppBackupFormat.ARCHIVE_MANIFEST)).getInt("progressPhotoCount"))
    }

    @Test
    fun legacyJsonSchemasRestoreWithoutRemovingLocalPhotos() = runTest {
        val local = savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val weight = WeightMeasurementEntity(id = 4, date = "2026-08-01", weightKg = 70.0, createdAt = 1, updatedAt = 1)
        database.appBackupDao().replaceAll(emptyTables().copy(weightMeasurements = listOf(weight)))
        val json = JSONObject(
            AppBackupRepository(database, preferences) { Instant.parse("2026-09-01T00:00:00Z") }
                .exportJson(source())
        )
        listOf(6, 7, 8).forEach { schema ->
            database.appBackupDao().replaceAll(
                emptyTables().copy(
                    weightMeasurements = listOf(
                        WeightMeasurementEntity(id = 99, date = "2026-01-01", weightKg = 41.0, createdAt = 1, updatedAt = 1)
                    )
                )
            )
            val legacy = JSONObject(json.toString()).put("schemaVersion", schema)
            if (schema < 8) {
                legacy.getJSONObject("tables").remove("body_measurements")
                legacy.getJSONObject("tables").remove("weekly_workout_goals")
            }
            val result = repository().restore(ByteArrayInputStream(legacy.toString().toByteArray()))
            assertEquals(schema.toString(), AppBackupRestoreResult.Success, result)
            assertEquals(listOf(weight.date), database.weightMeasurementDao().let { database.appBackupDao().loadTables().weightMeasurements.map { it.date } })
            assertEquals(local.id, database.progressPhotoDao().getAll().single().id)
            assertTrue(File(photoDirectory, local.fileName).isFile)
        }
    }

    @Test
    fun looseSchemaNineJsonIsRejected() = runTest {
        savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val exported = ByteArrayOutputStream()
        repository().exportArchive(source(), exported)
        val data = zipText(exported.toByteArray(), AppBackupFormat.ARCHIVE_DATA)
        assertTrue(AppBackupJson.parse(data) is AppBackupParseResult.Failure)
        val restored = repository().restore(ByteArrayInputStream(data.toByteArray()))
        assertEquals(
            AppBackupErrorCode.UnsupportedSchemaVersion,
            (restored as AppBackupRestoreResult.Invalid).errors.first().code
        )
        assertEquals(1, database.progressPhotoDao().getAll().size)
    }

    @Test
    fun corruptZipIsRejectedBeforeDataChanges() = runTest {
        val local = savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val bytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x01)
        val restored = repository().restore(ByteArrayInputStream(bytes))
        assertEquals(AppBackupErrorCode.CorruptBackup, (restored as AppBackupRestoreResult.Invalid).errors.first().code)
        assertEquals(local.id, database.progressPhotoDao().getAll().single().id)
        assertTrue(File(photoDirectory, local.fileName).isFile)
        assertFalse(store.stagingDirectory().exists())
    }

    @Test
    fun missingReferencedPhotoRejectsTheArchive() = runTest {
        savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        savePhoto("22222222-2222-2222-2222-222222222222.jpg", "2026-09-02", Color.BLUE)
        val exported = ByteArrayOutputStream()
        repository().exportArchive(source(), exported)
        val trimmed = rewriteZip(exported.toByteArray()) { name, _ ->
            name != "progress_photos/22222222-2222-2222-2222-222222222222.jpg"
        }
        database.appBackupDao().replaceAll(emptyTables())
        val marker = savePhoto("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg", "2026-01-01", Color.GREEN)
        val before = database.progressPhotoDao().getAll().map { it.fileName }.toSet()
        val restored = repository().restore(ByteArrayInputStream(trimmed))
        assertEquals(AppBackupErrorCode.IncompleteBackup, (restored as AppBackupRestoreResult.Invalid).errors.first().code)
        assertEquals(before, database.progressPhotoDao().getAll().map { it.fileName }.toSet())
        assertTrue(File(photoDirectory, marker.fileName).isFile)
    }

    @Test
    fun invalidPhotoBytesRejectTheArchive() = runTest {
        savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val exported = ByteArrayOutputStream()
        repository().exportArchive(source(), exported)
        val damaged = rewriteZip(exported.toByteArray()) { name, bytes ->
            if (name.startsWith("progress_photos/")) "not-a-jpeg".toByteArray() else bytes
        }
        val local = database.progressPhotoDao().getAll().single()
        val restored = repository().restore(ByteArrayInputStream(damaged))
        assertEquals(AppBackupErrorCode.InvalidPhoto, (restored as AppBackupRestoreResult.Invalid).errors.first().code)
        assertEquals(local.fileName, database.progressPhotoDao().getAll().single().fileName)
    }

    @Test
    fun zipSlipEntryIsRejectedAndDoesNotEscapeStaging() = runTest {
        val local = savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val zip = ByteArrayOutputStream()
        ZipOutputStream(zip).use { output ->
            output.putNextEntry(ZipEntry("../../escaped.txt"))
            output.write("no".toByteArray())
            output.closeEntry()
        }
        val escaped = File(photoDirectory.parentFile, "escaped.txt")
        escaped.delete()
        val restored = repository().restore(ByteArrayInputStream(zip.toByteArray()))
        assertEquals(AppBackupErrorCode.CorruptBackup, (restored as AppBackupRestoreResult.Invalid).errors.first().code)
        assertFalse(escaped.exists())
        assertEquals(local.fileName, database.progressPhotoDao().getAll().single().fileName)
        assertFalse(store.stagingDirectory().exists())
    }

    @Test
    fun unsupportedContainerVersionIsRejected() = runTest {
        savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val exported = ByteArrayOutputStream()
        repository().exportArchive(source(), exported)
        val changed = rewriteZip(exported.toByteArray()) { name, bytes ->
            if (name == AppBackupFormat.ARCHIVE_MANIFEST) {
                JSONObject(bytes.toString(Charsets.UTF_8)).put("containerVersion", 99).toString().toByteArray()
            } else {
                bytes
            }
        }
        val restored = repository().restore(ByteArrayInputStream(changed))
        assertEquals(
            AppBackupErrorCode.UnsupportedContainerVersion,
            (restored as AppBackupRestoreResult.Invalid).errors.first().code
        )
        assertEquals(1, database.progressPhotoDao().getAll().size)
    }

    @Test
    fun failureAfterValidationKeepsTheCurrentLibrary() = runTest {
        savePhoto("11111111-1111-1111-1111-111111111111.jpg", "2026-09-01", Color.RED)
        val exported = ByteArrayOutputStream()
        repository().exportArchive(source(), exported)
        val local = savePhoto("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg", "2026-01-01", Color.GREEN)
        database.progressPhotoDao().deleteById(
            database.progressPhotoDao().getAll().single { it.fileName.startsWith("11111111") }.id
        )
        File(photoDirectory, "11111111-1111-1111-1111-111111111111.jpg").delete()
        val failing = AppBackupRepository(
            database,
            preferences,
            progressPhotoStore = store,
            beforeDestructiveRestore = { error("stop") }
        )
        val restored = failing.restore(ByteArrayInputStream(exported.toByteArray()))
        assertEquals(AppBackupErrorCode.RestoreFailed, (restored as AppBackupRestoreResult.Invalid).errors.first().code)
        assertEquals(local.fileName, database.progressPhotoDao().getAll().single().fileName)
        assertTrue(File(photoDirectory, local.fileName).isFile)
        assertFalse(File(photoDirectory, "11111111-1111-1111-1111-111111111111.jpg").exists())
        assertFalse(store.restoreMarker().exists())
        assertFalse(store.stagingDirectory().exists())
    }

    @Test
    fun interruptedPhotoSwapFinishesOnRecovery() = runTest {
        val stagedName = "33333333-3333-3333-3333-333333333333.jpg"
        val previousName = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg"
        database.progressPhotoDao().insert(photo(stagedName, "2026-09-03"))
        File(photoDirectory, previousName).writeBytes(jpeg(Color.GREEN))
        val stagedBytes = jpeg(Color.BLUE)
        val staging = store.stagingDirectory().apply { mkdirs() }
        File(staging, stagedName).writeBytes(stagedBytes)
        app.mymusclemap.data.appbackup.AppBackupArchive.writeMarker(
            store.restoreMarker(),
            app.mymusclemap.data.appbackup.PhotoRestoreMarker(
                previousFileNames = setOf(previousName),
                stagedFileNames = setOf(stagedName)
            )
        )
        repository().recoverInterruptedPhotoRestore()
        assertArrayEquals(stagedBytes, File(photoDirectory, stagedName).readBytes())
        assertFalse(File(photoDirectory, previousName).exists())
        assertFalse(store.restoreMarker().exists())
        assertFalse(store.stagingDirectory().exists())
    }

    private fun repository(): AppBackupRepository {
        return AppBackupRepository(database, preferences, progressPhotoStore = store) {
            Instant.parse("2026-09-28T12:00:00Z")
        }
    }

    private fun source() = AppBackupSource("app.mymusclemap", "0.1.0")

    private val photoBytes = HashMap<String, ByteArray>()

    private suspend fun savePhoto(fileName: String, date: String, color: Int): ProgressPhotoEntity {
        val bytes = jpeg(color)
        photoBytes[fileName] = bytes
        File(photoDirectory, fileName).writeBytes(bytes)
        val id = database.progressPhotoDao().insert(photo(fileName, date))
        return database.progressPhotoDao().getById(id)!!
    }

    private fun photo(fileName: String, date: String): ProgressPhotoEntity {
        return ProgressPhotoEntity(date = date, fileName = fileName, createdAt = 1, updatedAt = 1)
    }

    private fun jpeg(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    private fun zipNames(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                names += entry.name
                zip.readBytes()
                zip.closeEntry()
            }
        }
        return names
    }

    private fun zipText(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val body = zip.readBytes()
                if (entry.name == name) return body.toString(Charsets.UTF_8)
            }
        }
        error(name)
    }

    private fun rewriteZip(bytes: ByteArray, transform: (String, ByteArray) -> Any): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val body = input.readBytes()
                    val changed = transform(entry.name, body)
                    val next = when (changed) {
                        is ByteArray -> changed
                        is Boolean -> if (changed) body else null
                        else -> error("transform")
                    }
                    if (next != null) {
                        zip.putNextEntry(ZipEntry(entry.name))
                        zip.write(next)
                        zip.closeEntry()
                    }
                }
            }
        }
        return output.toByteArray()
    }
}
