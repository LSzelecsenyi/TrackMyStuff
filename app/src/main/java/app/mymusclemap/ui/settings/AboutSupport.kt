package app.mymusclemap.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object AboutConfig {
    /** Public privacy-policy URL. Null until a real Play-ready page exists. Set this to enable the Settings row. */
    val privacyPolicyUrl: String? = null
}

object FeedbackComposer {
    const val RECIPIENT = "laszlo.szelecsenyi@gmail.com"

    fun createIntent(recipient: String = RECIPIENT, subject: String, body: String): Intent {
        return Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$recipient")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
    }

    fun launch(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(Intent.createChooser(intent, null))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}
