package app.mymusclemap.ui.pro

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
class ProBenefitsCopyTest {
    @Test
    fun englishAndHungarianBenefitsCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hungarian = localized(context, Locale("hu"))
        assertEquals("Unlock your full training potential", context.getString(R.string.pro_benefits_intro_title))
        assertEquals(
            "Build without limits, track your progress over time, and make every workout count with STRICT PRO.",
            context.getString(R.string.pro_benefits_intro_body)
        )
        assertEquals("Your PRO benefits", context.getString(R.string.pro_benefits_yours_title))
        assertEquals(
            "These benefits are included with your current STRICT PRO access.",
            context.getString(R.string.pro_benefits_yours_body)
        )
        assertEquals("STRICT FREE", context.getString(R.string.pro_benefits_access_free))
        assertEquals("STRICT PRO", context.getString(R.string.pro_benefits_access_pro))
        assertEquals("Subscription", context.getString(R.string.pro_benefits_subscription))
        assertEquals("No active subscription.", context.getString(R.string.pro_benefits_subscription_none))
        assertEquals("Timed sets & timers", context.getString(R.string.pro_benefits_timers))
        assertEquals("Included", context.getString(R.string.pro_benefits_cell_included))
        assertEquals("Weight + waist", context.getString(R.string.pro_benefits_cell_measurements_free))
        assertEquals("View saved", context.getString(R.string.pro_benefits_cell_photos_free))
        assertEquals("Add & compare", context.getString(R.string.pro_benefits_cell_photos_pro))
        assertEquals("Extended", context.getString(R.string.pro_benefits_cell_reports_pro))
        assertEquals("Pro badges", context.getString(R.string.pro_benefits_cell_achievements_pro))
        assertEquals("Explore PRO benefits", context.getString(R.string.timed_set_explore_pro))
        assertEquals(
            "Active subscription. Ends %1\$s. Auto-renew is off.",
            context.getString(R.string.pro_plan_paid_ends)
        )

        assertEquals("Hozd ki a legtöbbet az edzéseidből", hungarian.getString(R.string.pro_benefits_intro_title))
        assertEquals(
            "Tervezd korlátok nélkül az edzéseidet, kövesd hosszú távon a fejlődésedet, és használd ki a STRICT PRO lehetőségeit.",
            hungarian.getString(R.string.pro_benefits_intro_body)
        )
        assertEquals("A PRO előnyeid", hungarian.getString(R.string.pro_benefits_yours_title))
        assertEquals("STRICT FREE", hungarian.getString(R.string.pro_benefits_access_free))
        assertEquals("Előfizetés", hungarian.getString(R.string.pro_benefits_subscription))
        assertEquals("Nincs aktív előfizetés.", hungarian.getString(R.string.pro_benefits_subscription_none))
        assertEquals("Időzített sorozatok és időzítők", hungarian.getString(R.string.pro_benefits_timers))
        assertEquals("Benne van", hungarian.getString(R.string.pro_benefits_cell_included))
        assertEquals("Testsúly + derék", hungarian.getString(R.string.pro_benefits_cell_measurements_free))
        assertEquals("Mentett fotók", hungarian.getString(R.string.pro_benefits_cell_photos_free))
        assertEquals("PRO előnyök megtekintése", hungarian.getString(R.string.timed_set_explore_pro))
        assertEquals(
            "Aktív előfizetés. Lejár: %1\$s. Az automatikus megújulás ki van kapcsolva.",
            hungarian.getString(R.string.pro_plan_paid_ends)
        )
    }

    private fun localized(context: Context, locale: Locale): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }
}
