package app.mymusclemap.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.units.UnitSystem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UnitSystemPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun missingPreferenceStaysMetricAndAChoiceSurvivesANewStore() = runBlocking {
        val first = UnitSystemPreferences(context)
        first.load()
        assertNull(first.storedOrNull())
        assertEquals(UnitSystem.METRIC, first.current())
        first.write(UnitSystem.IMPERIAL)
        val second = UnitSystemPreferences(context)
        second.load()
        assertEquals(UnitSystem.IMPERIAL, second.storedOrNull())
        assertEquals(UnitSystem.IMPERIAL, second.current())
        assertEquals(UnitSystem.IMPERIAL, DisplayedUnitSystem.current)
        second.write(UnitSystem.METRIC)
        assertEquals(UnitSystem.METRIC, UnitSystemPreferences(context).read())
    }
}
