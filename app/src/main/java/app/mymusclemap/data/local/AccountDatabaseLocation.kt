package app.mymusclemap.data.local

import android.content.Context
import java.io.File

/**
 * Account Room files live in files/strict_user_data and are opened by absolute path.
 * A relative database name cannot contain a separator, and creating a database in a
 * subdirectory of the Android databases directory is not reliable. The legacy unclaimed
 * database and every other database, including WorkManager, stay in the databases
 * directory and outside Auto Backup.
 */
object AccountDatabaseLocation {
    const val DIRECTORY_NAME = "strict_user_data"
    const val LEGACY_UNCLAIMED_NAME = "weight_tracker.db"
    private val ACCOUNT_FILE = Regex(
        "account_[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.db"
    )

    fun isAccountDatabase(name: String): Boolean = ACCOUNT_FILE.matches(name)

    fun databasesDirectory(context: Context): File {
        return context.applicationContext.getDatabasePath(LEGACY_UNCLAIMED_NAME).parentFile
            ?: error("Android databases directory is unavailable")
    }

    fun userDataDirectory(context: Context): File {
        return File(context.applicationContext.filesDir, DIRECTORY_NAME)
    }

    fun accountFile(context: Context, fileName: String): File {
        require(isAccountDatabase(fileName)) { fileName }
        return File(userDataDirectory(context), fileName)
    }

    /** Pre-migration location: databases/account_<userId>.db */
    fun legacyAccountFile(context: Context, fileName: String): File {
        require(isAccountDatabase(fileName)) { fileName }
        return context.applicationContext.getDatabasePath(fileName)
    }

    fun legacyUnclaimedFile(context: Context): File {
        return context.applicationContext.getDatabasePath(LEGACY_UNCLAIMED_NAME)
    }
}
