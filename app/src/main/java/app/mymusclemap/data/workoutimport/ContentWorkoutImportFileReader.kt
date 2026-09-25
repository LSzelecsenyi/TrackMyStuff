package app.mymusclemap.data.workoutimport

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

class ContentWorkoutImportFileReader(
    private val context: Context
) : WorkoutImportFileReader {
    override suspend fun read(uri: String, maxBytes: Int): WorkoutImportFileReadResult {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return WorkoutImportFileReadResult.Unreadable
        val resolver = context.contentResolver
        return try {
            val displayName = displayName(resolver, parsed)
            val stream = resolver.openInputStream(parsed) ?: return WorkoutImportFileReadResult.Unreadable
            stream.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(chunk)
                    if (count <= 0) {
                        break
                    }
                    total += count
                    if (total > maxBytes) {
                        return WorkoutImportFileReadResult.TooLarge
                    }
                    buffer.write(chunk, 0, count)
                }
                WorkoutImportFileReadResult.Success(buffer.toByteArray(), displayName)
            }
        } catch (_: SecurityException) {
            WorkoutImportFileReadResult.PermissionDenied
        } catch (_: FileNotFoundException) {
            WorkoutImportFileReadResult.Unreadable
        } catch (_: Exception) {
            WorkoutImportFileReadResult.Unreadable
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val queried = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
        return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "CSV"
    }
}
