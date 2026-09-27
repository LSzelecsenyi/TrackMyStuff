package app.mymusclemap.ui.progress

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.data.repository.ProgressPhotoRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class ProgressPhotosViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 9, 27)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-09-27T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun freeEntryStaysVisibleAndAddOpensProUntilEntitled() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = database(context)
        val directory = directory(context)
        try {
            val repository = repository(database, directory)
            val locked = viewModel(repository, SelectiveFeatureEntitlements(emptySet()))
            val empty = locked.uiState.first { it.loaded }
            assertTrue(empty.photos.isEmpty())
            assertFalse(empty.canAdd)
            assertTrue(empty.showProBadge)
            locked.requestAdd()
            assertEquals(AppFeature.ProgressPhotos, locked.uiState.value.lockedFeature)
            assertFalse(locked.uiState.value.launchPicker)
            locked.onPickerCancelled()
            assertTrue(locked.uiState.value.photos.isEmpty())
            assertNull(locked.uiState.value.message)

            val entitled = viewModel(repository, SelectiveFeatureEntitlements(setOf(AppFeature.ProgressPhotos)))
            entitled.uiState.first { it.loaded }
            entitled.requestAdd()
            assertTrue(entitled.uiState.value.launchPicker)
            assertNull(entitled.uiState.value.lockedFeature)
            entitled.consumeLaunchPicker()
            entitled.import(jpeg().inputStream())
            val saved = entitled.uiState.first { it.photos.size == 1 }
            entitled.import(jpeg().inputStream())
            val both = entitled.uiState.first { it.photos.size == 2 }
            assertEquals(today, both.photos[0].date)
            assertEquals(today, both.photos[1].date)
            assertTrue(both.photos[0].id > both.photos[1].id)

            val downgraded = viewModel(repository, SelectiveFeatureEntitlements(emptySet()))
            val kept = downgraded.uiState.first { it.photos.size == 2 }
            assertFalse(kept.canAdd)
            downgraded.requestAdd()
            assertEquals(AppFeature.ProgressPhotos, downgraded.uiState.value.lockedFeature)
            assertEquals(2, downgraded.uiState.value.photos.size)
            val existing = kept.photos.first()
            downgraded.delete(existing.id)
            val afterDelete = downgraded.uiState.first { it.photos.size == 1 }
            assertFalse(afterDelete.photos.any { it.id == existing.id })
            assertEquals(saved.photos.single().id, afterDelete.photos.single().id)
        } finally {
            database.close()
        }
    }

    @Test
    fun compareSelectionStopsAtTwoAndMissingFileStaysDeletable() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = database(context)
        val directory = directory(context)
        try {
            val repository = repository(database, directory)
            val viewModel = viewModel(repository, SelectiveFeatureEntitlements(setOf(AppFeature.ProgressPhotos)))
            viewModel.uiState.first { it.loaded }
            viewModel.import(jpeg().inputStream())
            viewModel.uiState.first { it.photos.size == 1 }
            viewModel.import(jpeg().inputStream())
            val ready = viewModel.uiState.first { it.photos.size == 2 }
            viewModel.beginCompare()
            viewModel.toggleCompareSelection(ready.photos[0].id)
            viewModel.toggleCompareSelection(ready.photos[1].id)
            viewModel.toggleCompareSelection(ready.photos[0].id)
            assertEquals(listOf(ready.photos[1].id), viewModel.uiState.value.selectedIds)
            viewModel.toggleCompareSelection(ready.photos[0].id)
            assertEquals(2, viewModel.uiState.value.selectedIds.size)
            viewModel.uiState.first { it.latestThumbnail != null }
            val file = File(directory, ready.photos[0].fileName)
            assertTrue(file.isFile)
            for (attempt in 1..20) {
                if (file.delete() || !file.exists()) break
                Thread.sleep(20)
            }
            assertFalse(file.exists())
            viewModel.import(jpeg().inputStream())
            val withMissing = viewModel.uiState.first { state -> state.photos.any { it.missing } }
            val missing = withMissing.photos.single { it.missing }
            viewModel.delete(missing.id)
            viewModel.uiState.first { state -> state.photos.none { it.id == missing.id } }
        } finally {
            database.close()
        }
    }

    private fun viewModel(
        repository: ProgressPhotoRepository,
        entitlements: SelectiveFeatureEntitlements
    ): ProgressPhotosViewModel {
        return ProgressPhotosViewModel(repository, entitlements)
    }

    private fun repository(database: WeightDatabase, directory: File): ProgressPhotoRepository {
        return ProgressPhotoRepository(
            dao = database.progressPhotoDao(),
            store = ProgressPhotoStore(directory),
            clock = clock,
            dateProvider = dateProvider,
            maxEdge = 16
        )
    }

    private fun database(context: Context): WeightDatabase {
        return Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private fun directory(context: Context): File {
        return File(context.cacheDir, "progress-vm-${System.nanoTime()}").apply { mkdirs() }
    }

    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.GREEN)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }
}
