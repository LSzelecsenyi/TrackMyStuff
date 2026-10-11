package app.mymusclemap.data.local

import android.content.Context
import java.io.IOException

/**
 * Checkpoints account databases before Auto Backup copies them.
 * A checkpoint request is not treated as success unless SQLite reports every frame was committed.
 * WAL files are left in place so an incomplete checkpoint is not omitted from the copy.
 */
class AccountDatabaseCheckpointer(context: Context) {
    private val appContext = context.applicationContext

    fun checkpointOrThrow() {
        val directory = AccountDatabaseLocation.userDataDirectory(appContext)
        val files = directory.listFiles()
            ?.filter { it.isFile && AccountDatabaseLocation.isAccountDatabase(it.name) }
            .orEmpty()
        files.forEach { file ->
            val checkpoint = try {
                checkpointWalTruncating(file)
            } catch (error: Exception) {
                throw IOException("Could not checkpoint ${file.name}", error)
            }
            requireCompleteCheckpoint(file, checkpoint)
        }
    }
}
