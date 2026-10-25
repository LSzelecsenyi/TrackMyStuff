package app.mymusclemap.ui.exercises

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class FreeReleaseCopyTest {
    @Test
    fun englishAndHungarianReleaseCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hungarian = localized(context, Locale("hu"))
        assertEquals("Remove from FREE access", context.getString(R.string.free_release_access))
        assertEquals("Remove from FREE access?", context.getString(R.string.free_release_access_title))
        assertEquals(
            "Ring Pushup will become locked in STRICT FREE.",
            context.getString(R.string.free_release_becomes_locked, "Ring Pushup")
        )
        assertEquals(
            "This exercise is used in Push Day.",
            context.getString(R.string.free_release_used_in, "Push Day")
        )
        assertEquals(
            "This exercise is used in Push Day, Pull Day and 2 more.",
            context.getString(R.string.free_release_used_in_more, "Push Day, Pull Day", 2)
        )
        assertEquals(
            "It remains saved in locked workout plans.",
            context.getString(R.string.free_release_locked_plans)
        )
        assertEquals("Cancel", context.getString(R.string.action_cancel))
        assertEquals("Eltávolítás a FREE keretből", hungarian.getString(R.string.free_release_access))
        assertEquals("Eltávolítod a FREE keretből?", hungarian.getString(R.string.free_release_access_title))
        assertEquals(
            "A(z) Ring Pushup gyakorlat zárolttá válik a STRICT FREE verziójában.",
            hungarian.getString(R.string.free_release_becomes_locked, "Ring Pushup")
        )
        assertEquals(
            "Ez a gyakorlat a(z) Push Day edzéstervben is szerepel.",
            hungarian.getString(R.string.free_release_used_in, "Push Day")
        )
        assertEquals(
            "Az edzésterveid és az előzményeid megmaradnak. A felszabaduló helyre új egyéni gyakorlatot hozhatsz létre, de a(z) Ring Pushup gyakorlatot ebben a FREE időszakban már nem aktiválhatod újra.",
            hungarian.getString(R.string.free_release_irreversible, "Ring Pushup")
        )
        assertEquals("Mégse", hungarian.getString(R.string.action_cancel))
        assertEquals(
            "Fejezd be a folyamatban lévő edzést, mielőtt kiveszed ezt a gyakorlatot a FREE keretből.",
            hungarian.getString(R.string.free_release_active_workout)
        )
    }

    private fun localized(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
