package app.mymusclemap.data.repository

import app.mymusclemap.data.local.ProgressPhotoDao
import app.mymusclemap.data.local.ProgressPhotoEntity
import app.mymusclemap.data.local.toModel
import app.mymusclemap.data.progress.ProgressPhotoProcessor
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.progress.ProgressPhoto
import app.mymusclemap.domain.progress.ProgressPhotoImportResult
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

class ProgressPhotoRepository(
    private val dao: ProgressPhotoDao,
    private val store: ProgressPhotoStore,
    private val clock: Clock,
    private val dateProvider: DateProvider,
    private val maxEdge: Int = ProgressPhotoProcessor.MAX_EDGE
) {
    fun observeAll(): Flow<List<ProgressPhoto>> {
        return dao.observeAll().map { rows -> rows.map { it.toModel() } }
    }

    suspend fun import(source: InputStream, date: LocalDate? = null): ProgressPhotoImportResult {
        return withContext(Dispatchers.IO) {
            if (date != null && date.isAfter(dateProvider.today())) {
                return@withContext ProgressPhotoImportResult.FutureDate
            }
            store.prepare()
            val fileName = store.newFileName()
            val output = store.resolve(fileName) ?: return@withContext ProgressPhotoImportResult.Unreadable
            val processed = ProgressPhotoProcessor.process(source, output, maxEdge)
            if (processed == null) {
                store.delete(fileName)
                return@withContext ProgressPhotoImportResult.Unreadable
            }
            val storedDate = date ?: processed.capturedOn?.takeUnless { it.isAfter(dateProvider.today()) }
                ?: dateProvider.today()
            if (storedDate.isAfter(dateProvider.today())) {
                store.delete(fileName)
                return@withContext ProgressPhotoImportResult.FutureDate
            }
            try {
                val now = clock.millis()
                val id = dao.insert(
                    ProgressPhotoEntity(
                        date = storedDate.toString(),
                        fileName = fileName,
                        createdAt = now,
                        updatedAt = now
                    )
                )
                val saved = dao.getById(id)?.toModel()
                if (saved == null) {
                    store.delete(fileName)
                    ProgressPhotoImportResult.Unreadable
                } else {
                    ProgressPhotoImportResult.Saved(saved)
                }
            } catch (cancelled: CancellationException) {
                store.delete(fileName)
                throw cancelled
            } catch (_: Exception) {
                store.delete(fileName)
                ProgressPhotoImportResult.Unreadable
            }
        }
    }

    suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) {
            val existing = dao.getById(id) ?: return@withContext
            dao.deleteById(id)
            store.delete(existing.fileName)
        }
    }

    fun isAvailable(fileName: String): Boolean = store.exists(fileName)

    fun decode(fileName: String, edge: Int): Bitmap? {
        val file = store.resolve(fileName) ?: return null
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = ProgressPhotoProcessor.sampleSize(bounds.outWidth, bounds.outHeight, edge)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )?.let { decoded ->
            val scaled = ProgressPhotoProcessor.scaleToEdge(decoded, edge)
            if (scaled !== decoded) decoded.recycle()
            scaled
        }
    }

    suspend fun sweepOrphans() {
        withContext(Dispatchers.IO) {
            val referenced = dao.getAll().map { it.fileName }.toSet()
            store.ownedFileNames()
                .filter { it !in referenced }
                .forEach(store::deleteOwned)
        }
    }
}
