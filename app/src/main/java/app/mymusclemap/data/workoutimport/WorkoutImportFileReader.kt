package app.mymusclemap.data.workoutimport

sealed class WorkoutImportFileReadResult {
    data class Success(
        val bytes: ByteArray,
        val displayName: String
    ) : WorkoutImportFileReadResult() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return displayName == other.displayName && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            return 31 * bytes.contentHashCode() + displayName.hashCode()
        }
    }

    data object TooLarge : WorkoutImportFileReadResult()
    data object PermissionDenied : WorkoutImportFileReadResult()
    data object Unreadable : WorkoutImportFileReadResult()
}

fun interface WorkoutImportFileReader {
    suspend fun read(uri: String, maxBytes: Int): WorkoutImportFileReadResult
}
