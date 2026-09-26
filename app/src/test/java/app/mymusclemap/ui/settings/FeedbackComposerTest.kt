package app.mymusclemap.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeedbackComposerTest {
    @Test
    fun createIntentPrefillsMailtoExtrasWithoutSending() {
        val intent = FeedbackComposer.createIntent(
            recipient = "test@example.com",
            subject = "Subject line",
            body = "Body text"
        )
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals(Uri.parse("mailto:test@example.com"), intent.data)
        assertArrayEquals(arrayOf("test@example.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL))
        assertEquals("Subject line", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Body text", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun launchReturnsFalseWhenNoEmailApp() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException()
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                throw ActivityNotFoundException()
            }
        }
        val launched = FeedbackComposer.launch(
            context,
            FeedbackComposer.createIntent(subject = "Subject", body = "Body")
        )
        assertFalse(launched)
    }

    @Test
    fun launchReturnsTrueAndUsesChooserWhenStartActivitySucceeds() {
        var launched: Intent? = null
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext()) {
            override fun startActivity(intent: Intent) {
                launched = intent
            }

            override fun startActivity(intent: Intent, options: Bundle?) {
                launched = intent
            }
        }
        val started = FeedbackComposer.launch(
            context,
            FeedbackComposer.createIntent(subject = "Subject", body = "Body")
        )
        assertTrue(started)
        assertEquals(Intent.ACTION_CHOOSER, launched!!.action)
    }

    @Test
    fun stringResourcesFormatSubjectAndBody() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val subject = context.getString(R.string.feedback_email_subject, "9.9.9-debug")
        val body = context.getString(
            R.string.feedback_email_body,
            "9.9.9-debug",
            "14",
            "Google/Pixel 8"
        )
        assertEquals("My Muscle Map feedback – 9.9.9-debug", subject)
        assertEquals(
            "App version: 9.9.9-debug\nAndroid: 14\nDevice: Google/Pixel 8\n\nFeedback:\n",
            body
        )
    }

    @Test
    fun alphaUsesInAppPrivacyPolicyUntilPublicUrlIsConfigured() {
        assertEquals(null, AboutConfig.privacyPolicyUrl)
        assertFalse(AboutConfig.usesExternalPrivacyPolicy())
    }
}
