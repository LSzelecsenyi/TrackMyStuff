package app.mymusclemap.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import app.mymusclemap.data.local.AccountDatabaseCheckpointer
import app.mymusclemap.data.local.AccountDatabaseMigrator
import app.mymusclemap.data.local.discardUnrestoredAccountSidecars
import java.io.File
import java.io.IOException

/**
 * File-based Auto Backup.
 *
 * Restricted backup mode instantiates [android.app.Application], not
 * [app.mymusclemap.WeightTrackerApplication], and does not install content providers.
 * This agent therefore does not open [app.mymusclemap.AppContainer], a session, or WorkManager.
 * Include and exclude behavior stays in the backup XML. Key/value backup stores nothing.
 */
class StrictBackupAgent : BackupAgent() {
    private val restoredFiles = mutableListOf<File>()

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?
    ) = Unit

    override fun onFullBackup(data: FullBackupDataOutput) {
        val failures = AccountDatabaseMigrator(this).migrateAll()
        if (failures.isNotEmpty()) {
            throw IOException(
                "Account database migration did not finish. Original files were left in place."
            )
        }
        AccountDatabaseCheckpointer(this).checkpointOrThrow()
        super.onFullBackup(data)
    }

    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        super.onRestoreFile(data, size, destination, type, mode, mtime)
        if (type == FULL_BACKUP_FILE) {
            restoredFiles += destination
        }
    }

    override fun onRestoreFinished() {
        discardUnrestoredAccountSidecars(restoredFiles)
        restoredFiles.clear()
    }

    private companion object {
        /** Matches the platform file type passed to [onRestoreFile]. Directories are 2. */
        const val FULL_BACKUP_FILE = 1
    }
}
