package app.mymusclemap.data.progress

import android.content.Context
import android.content.Intent
import android.net.Uri

object GalleryUriAccess {
    fun retain(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Photo-picker URIs often cannot be persisted. Recovery then stays unavailable.
        }
    }
}
