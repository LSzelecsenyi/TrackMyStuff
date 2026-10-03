package app.mymusclemap.ui.founder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

object FounderShare {
    fun text(context: Context, subject: String, body: String): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        return try {
            context.startActivity(Intent.createChooser(intent, subject))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
