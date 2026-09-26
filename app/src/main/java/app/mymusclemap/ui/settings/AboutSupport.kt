package app.mymusclemap.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object AboutConfig {
    /**
     * Public HTTPS privacy-policy URL for Play Store listing and in-app browser open.
     * Null uses the in-app Privacy Policy screen until a hosted page exists.
     */
    val privacyPolicyUrl: String? = null

    fun usesExternalPrivacyPolicy(): Boolean = !privacyPolicyUrl.isNullOrBlank()
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
