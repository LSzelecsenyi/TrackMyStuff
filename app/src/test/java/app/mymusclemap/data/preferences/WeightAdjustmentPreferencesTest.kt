package app.mymusclemap.data.preferences

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.units.UnitSystem
import app.mymusclemap.domain.units.WeightAdjustmentConfiguration
import app.mymusclemap.domain.units.WeightAdjustmentStep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class WeightAdjustmentPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun aCustomIncrementSurvivesANewStore() = runBlocking {
        val first = WeightAdjustmentPreferences(context)
        first.replace(WeightAdjustmentConfiguration())
        first.load()
        assertEquals("2.5", WeightAdjustmentStep.storedText(first.current().metric))
        assertEquals("5", WeightAdjustmentStep.storedText(first.current().imperial))

        // Given a configured custom increment of 1.25 kg
        assertTrue(first.write(UnitSystem.METRIC, "1.25"))

        // When the application restarts
        val second = WeightAdjustmentPreferences(context)
        second.load()

        // Then the configured increment remains 1.25 kg
        assertEquals(0, second.current().metric.compareTo(BigDecimal("1.25")))
        assertEquals("5", WeightAdjustmentStep.storedText(second.current().imperial))
    }

    @Test
    fun anInvalidCustomIncrementDoesNotReplaceTheSavedStep() = runBlocking {
        val store = WeightAdjustmentPreferences(context)
        store.replace(WeightAdjustmentConfiguration())
        store.load()
        assertTrue(store.write(UnitSystem.METRIC, "1.25"))

        // Given an invalid custom increment
        // When the user attempts to save it
        assertFalse(store.write(UnitSystem.METRIC, "0"))
        assertFalse(store.write(UnitSystem.METRIC, "NaN"))
        assertFalse(store.write(UnitSystem.IMPERIAL, "-2"))

        // Then the invalid value is rejected and the previous valid configuration remains effective
        assertEquals(0, store.current().metric.compareTo(BigDecimal("1.25")))
        assertEquals("5", WeightAdjustmentStep.storedText(store.current().imperial))
        val restarted = WeightAdjustmentPreferences(context)
        restarted.load()
        assertEquals(0, restarted.current().metric.compareTo(BigDecimal("1.25")))
    }
}
