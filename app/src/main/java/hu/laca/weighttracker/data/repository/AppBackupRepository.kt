package hu.laca.weighttracker.data.repository

import androidx.room.withTransaction
import hu.laca.weighttracker.data.appbackup.AppBackupError
import hu.laca.weighttracker.data.appbackup.AppBackupErrorCode
import hu.laca.weighttracker.data.appbackup.AppBackupFormat
import hu.laca.weighttracker.data.appbackup.AppBackupJson
import hu.laca.weighttracker.data.appbackup.AppBackupParseResult
import hu.laca.weighttracker.data.appbackup.AppBackupRestoreResult
import hu.laca.weighttracker.data.appbackup.AppBackupSnapshot
import hu.laca.weighttracker.data.appbackup.AppBackupSource
import hu.laca.weighttracker.data.local.WeightDatabase
import hu.laca.weighttracker.data.preferences.ThemePreferences
import hu.laca.weighttracker.domain.theme.AppearanceCodec
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException

class AppBackupRepository(
    private val database: WeightDatabase,
    private val themePreferences: ThemePreferences,
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

    suspend fun restoreJson(content: String): AppBackupRestoreResult {
        return when (val parsed = AppBackupJson.parse(content)) {
            is AppBackupParseResult.Failure -> AppBackupRestoreResult.Invalid(parsed.errors)
            is AppBackupParseResult.Success -> restoreSnapshot(parsed.snapshot)
        }
    }

    suspend fun restoreBytes(bytes: ByteArray): AppBackupRestoreResult {
        return when (val parsed = AppBackupJson.parse(bytes)) {
            is AppBackupParseResult.Failure -> AppBackupRestoreResult.Invalid(parsed.errors)
            is AppBackupParseResult.Success -> restoreSnapshot(parsed.snapshot)
        }
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
        return AppBackupRestoreResult.Success
    }
}
