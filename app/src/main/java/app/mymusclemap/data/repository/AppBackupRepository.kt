package app.mymusclemap.data.repository

import androidx.room.withTransaction
import app.mymusclemap.data.appbackup.AppBackupArchive
import app.mymusclemap.data.appbackup.AppBackupError
import app.mymusclemap.data.appbackup.AppBackupErrorCode
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupParseResult
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSnapshot
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.appbackup.AppBackupWriteResult
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.domain.theme.AppearanceCodec
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class AppBackupRepository(
    private val database: WeightDatabase,
    private val themePreferences: ThemePreferences,
    private val progressPhotoStore: ProgressPhotoStore? = null,
    private val onHeatmapDataChanged: () -> Unit = {},
    private val beforeDestructiveRestore: suspend () -> Unit = {},
    private val instantSource: () -> Instant = { Instant.now() }
) {
    suspend fun exportJson(source: AppBackupSource): String {
        val tables = database.appBackupDao().loadTables()
        val settings = AppearanceCodec.encode(themePreferences.current())
        val snapshot = AppBackupSnapshot(
            formatVersion = AppBackupFormat.FORMAT_VERSION,
            schemaVersion = AppBackupFormat.SCHEMA_VERSION,
            exportedAt = instantSource(),
            source = source,
            tables = tables,
            settings = settings
        )
        return AppBackupJson.encode(snapshot)
    }

    suspend fun exportArchive(source: AppBackupSource, output: OutputStream): AppBackupWriteResult {
        val store = progressPhotoStore ?: return AppBackupWriteResult.Failed
        val loaded = database.appBackupDao().loadTables()
        val kept = ArrayList<app.mymusclemap.data.local.ProgressPhotoEntity>()
        val files = ArrayList<File>()
        var skipped = 0
        loaded.progressPhotos.forEach { photo ->
            val file = store.resolve(photo.fileName)
            if (file != null && file.isFile) {
                kept += photo
                files += file
            } else {
                skipped += 1
            }
        }
        val snapshot = AppBackupSnapshot(
            formatVersion = AppBackupFormat.FORMAT_VERSION,
            schemaVersion = AppBackupFormat.ARCHIVE_DATA_SCHEMA_VERSION,
            exportedAt = instantSource(),
            source = source,
            tables = loaded.copy(progressPhotos = kept, replacesProgressPhotos = true),
            settings = AppearanceCodec.encode(themePreferences.current())
        )
        return try {
            AppBackupArchive.export(snapshot, files, output)
            AppBackupWriteResult.Written(skipped)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            AppBackupWriteResult.Failed
        }
    }

    suspend fun restoreJson(content: String): AppBackupRestoreResult {
        return when (val parsed = AppBackupJson.parse(content)) {
            is AppBackupParseResult.Failure -> AppBackupRestoreResult.Invalid(parsed.errors)
            is AppBackupParseResult.Success -> restoreSnapshot(parsed.snapshot)
        }
    }

    suspend fun restoreBytes(bytes: ByteArray): AppBackupRestoreResult {
        return restore(bytes.inputStream())
    }

    suspend fun restore(input: InputStream): AppBackupRestoreResult {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(8)
        val header = ByteArray(4)
        val read = buffered.read(header)
        buffered.reset()
        return if (looksLikeZip(header, read)) {
            restoreArchive(buffered)
        } else {
            val bytes = buffered.readBytes()
            if (bytes.size > AppBackupFormat.MAX_UTF8_BYTES) {
                return AppBackupRestoreResult.Invalid(
                    listOf(AppBackupError(AppBackupErrorCode.FileTooLarge, "file"))
                )
            }
            restoreJson(bytes.toString(Charsets.UTF_8))
        }
    }

    suspend fun recoverInterruptedPhotoRestore() {
        val store = progressPhotoStore ?: return
        try {
            val markerFile = store.restoreMarker()
            if (!markerFile.isFile) {
                store.stagingDirectory().deleteRecursively()
                return
            }
            val marker = AppBackupArchive.readMarker(markerFile) ?: return
            val current = database.appBackupDao().loadTables().progressPhotos
                .map { it.fileName }
                .toSet()
            when {
                current == marker.stagedFileNames -> finishPhotoSwap(store, marker.stagedFileNames)
                current == marker.previousFileNames -> {
                    store.stagingDirectory().deleteRecursively()
                    markerFile.delete()
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Leave the marker in place so the next launch can retry the file swap.
        }
    }

    private suspend fun restoreArchive(input: InputStream): AppBackupRestoreResult {
        val store = progressPhotoStore ?: return AppBackupRestoreResult.Invalid(
            listOf(AppBackupError(AppBackupErrorCode.RestoreFailed, "photos"))
        )
        val staging = store.stagingDirectory()
        val parsed = AppBackupArchive.inspect(input, staging)
        if (parsed is AppBackupParseResult.Failure) {
            return AppBackupRestoreResult.Invalid(parsed.errors)
        }
        val snapshot = (parsed as AppBackupParseResult.Success).snapshot
        val previous = database.appBackupDao().loadTables().progressPhotos
            .map { it.fileName }
            .filter(ProgressPhotoStore::isPortableFileName)
            .toSet()
        val staged = snapshot.tables.progressPhotos.map { it.fileName }.toSet()
        val markerFile = store.restoreMarker()
        AppBackupArchive.writeMarker(
            markerFile,
            app.mymusclemap.data.appbackup.PhotoRestoreMarker(previous, staged)
        )
        try {
            beforeDestructiveRestore()
            database.withTransaction {
                database.appBackupDao().replaceAll(snapshot.tables)
            }
        } catch (cancelled: CancellationException) {
            markerFile.delete()
            staging.deleteRecursively()
            throw cancelled
        } catch (_: Exception) {
            markerFile.delete()
            staging.deleteRecursively()
            return AppBackupRestoreResult.Invalid(
                listOf(AppBackupError(AppBackupErrorCode.RestoreFailed, "database"))
            )
        }
        try {
            finishPhotoSwap(store, staged)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AppBackupRestoreResult.Invalid(
                listOf(AppBackupError(AppBackupErrorCode.RestoreFailed, "photos"))
            )
        }
        themePreferences.replaceAppearance(AppearanceCodec.decodeFrom(snapshot.settings))
        onHeatmapDataChanged()
        return AppBackupRestoreResult.Success
    }

    private fun finishPhotoSwap(store: ProgressPhotoStore, staged: Set<String>) {
        val staging = store.stagingDirectory()
        staged.forEach { name ->
            val live = store.resolve(name)
            if (live != null && live.isFile) return@forEach
            val source = File(staging, name)
            if (!source.isFile || !store.copyFrom(name, source)) {
                error("photo")
            }
        }
        store.deleteJpegsExcept(staged)
        staging.deleteRecursively()
        store.restoreMarker().delete()
    }

    private fun looksLikeZip(header: ByteArray, read: Int): Boolean {
        if (read < 4) return false
        return header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte() &&
            (
                (header[2] == 0x03.toByte() && header[3] == 0x04.toByte()) ||
                    (header[2] == 0x05.toByte() && header[3] == 0x06.toByte()) ||
                    (header[2] == 0x07.toByte() && header[3] == 0x08.toByte())
                )
    }

    private suspend fun restoreSnapshot(snapshot: AppBackupSnapshot): AppBackupRestoreResult {
        try {
            database.withTransaction {
                database.appBackupDao().replaceAll(snapshot.tables)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return AppBackupRestoreResult.Invalid(
                listOf(AppBackupError(AppBackupErrorCode.InvalidValue, "restore"))
            )
        }
        themePreferences.replaceAppearance(AppearanceCodec.decodeFrom(snapshot.settings))
        onHeatmapDataChanged()
        return AppBackupRestoreResult.Success
    }
}
