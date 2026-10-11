package app.mymusclemap.data.repository

import app.mymusclemap.data.local.ProgressPhotoDao
import app.mymusclemap.data.local.ProgressPhotoEntity
import app.mymusclemap.data.local.toModel
import app.mymusclemap.data.progress.OriginalBytes
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
import java.io.File
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

class ProgressPhotoRepository(
    private val dao: ProgressPhotoDao,
    private val store: ProgressPhotoStore,
    private val clock: Clock,
    private val dateProvider: DateProvider,
    private val maxEdge: Int = ProgressPhotoProcessor.MAX_EDGE,
    private val openOriginal: (String) -> InputStream? = { null }
) {
    fun observeAll(): Flow<List<ProgressPhoto>> {
        return dao.observeAll().map { rows -> rows.map { it.toModel() } }
    }

    suspend fun import(
        source: InputStream,
        date: LocalDate? = null,
        sourceUri: String? = null
    ): ProgressPhotoImportResult {
        return withContext(Dispatchers.IO) {
            if (date != null && date.isAfter(dateProvider.today())) {
                source.close()
                return@withContext ProgressPhotoImportResult.FutureDate
            }
            store.prepare()
            val fileName = store.newFileName()
            val output = store.resolve(fileName) ?: return@withContext ProgressPhotoImportResult.Unreadable
            val spool = File(output.parentFile, "$fileName.original")
            val prepared = try {
                spoolOriginal(source, spool)
            } catch (cancelled: CancellationException) {
                spool.delete()
                throw cancelled
            }
            if (prepared == null) {
                spool.delete()
                return@withContext ProgressPhotoImportResult.Unreadable
            }
            try {
                val processed = spool.inputStream().use { ProgressPhotoProcessor.process(it, output, maxEdge) }
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
                val now = clock.millis()
                val id = dao.insert(
                    ProgressPhotoEntity(
                        date = storedDate.toString(),
                        fileName = fileName,
                        createdAt = now,
                        updatedAt = now,
                        sourceUri = sourceUri?.takeIf { it.isNotBlank() },
                        sourceFingerprint = prepared
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
            } finally {
                spool.delete()
            }
        }
    }

    /**
     * Replaces the private file for an existing row. The id, progress date, and createdAt stay.
     * Cancellation or failure leaves the existing row intact.
     */
    suspend fun relink(id: Long, source: InputStream, sourceUri: String?): ProgressPhotoImportResult {
        return withContext(Dispatchers.IO) {
            val existing = dao.getById(id) ?: return@withContext ProgressPhotoImportResult.Unreadable.also {
                source.close()
            }
            val output = store.resolve(existing.fileName) ?: return@withContext ProgressPhotoImportResult.Unreadable.also {
                source.close()
            }
            val parent = output.parentFile ?: return@withContext ProgressPhotoImportResult.Unreadable.also {
                source.close()
            }
            val spool = File(parent, "${existing.fileName}.original")
            val incoming = File(parent, "${existing.fileName}.incoming")
            val fingerprint = try {
                spoolOriginal(source, spool)
            } catch (cancelled: CancellationException) {
                spool.delete()
                incoming.delete()
                throw cancelled
            }
            if (fingerprint == null) {
                spool.delete()
                incoming.delete()
                return@withContext ProgressPhotoImportResult.Unreadable
            }
            try {
                val processed = spool.inputStream().use { ProgressPhotoProcessor.process(it, incoming, maxEdge) }
                if (processed == null || !store.replace(existing.fileName, incoming)) {
                    incoming.delete()
                    return@withContext ProgressPhotoImportResult.Unreadable
                }
                try {
                    dao.updateSource(
                        id = existing.id,
                        sourceUri = sourceUri?.takeIf { it.isNotBlank() },
                        sourceFingerprint = fingerprint,
                        updatedAt = clock.millis()
                    )
                    store.discardBackup(existing.fileName)
                } catch (cancelled: CancellationException) {
                    store.rollback(existing.fileName)
                    throw cancelled
                } catch (_: Exception) {
                    store.rollback(existing.fileName)
                    return@withContext ProgressPhotoImportResult.Unreadable
                }
                val saved = dao.getById(existing.id)?.toModel()
                    ?: return@withContext ProgressPhotoImportResult.Unreadable
                ProgressPhotoImportResult.Saved(saved)
            } finally {
                spool.delete()
                if (incoming.exists()) {
                    incoming.delete()
                }
            }
        }
    }

    /**
     * Recreates the private JPEG only when the stored gallery URI still opens and the
     * original bytes match the stored fingerprint. Otherwise the row stays missing.
     */
    suspend fun recoverOriginal(id: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val existing = dao.getById(id) ?: return@withContext false
            if (store.exists(existing.fileName)) {
                return@withContext true
            }
            val uri = existing.sourceUri ?: return@withContext false
            val expected = existing.sourceFingerprint ?: return@withContext false
            val output = store.resolve(existing.fileName) ?: return@withContext false
            val parent = output.parentFile ?: return@withContext false
            val opened = try {
                openOriginal(uri)
            } catch (_: Exception) {
                null
            } ?: return@withContext false
            val spool = File(parent, "${existing.fileName}.original")
            try {
                val actual = spoolOriginal(opened, spool) ?: return@withContext false
                if (actual != expected) {
                    return@withContext false
                }
                val processed = spool.inputStream().use { ProgressPhotoProcessor.process(it, output, maxEdge) }
                processed != null && store.exists(existing.fileName)
            } catch (cancelled: CancellationException) {
                output.delete()
                throw cancelled
            } catch (_: Exception) {
                output.delete()
                false
            } finally {
                spool.delete()
            }
        }
    }

    private fun spoolOriginal(source: InputStream, spool: File): String? {
        return try {
            source.use { input ->
                spool.outputStream().use { output -> input.copyTo(output) }
            }
            if (!spool.isFile || spool.length() == 0L) {
                null
            } else {
                OriginalBytes.sha256(spool)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            spool.delete()
            null
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
