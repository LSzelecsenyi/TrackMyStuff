package app.mymusclemap.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class ProDiscoveryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun activationSurvivesANewStoreAndDoesNotResetOnReload() = runBlocking {
        val first = real()
        first.clear()
        val activatedAt = Instant.parse("2026-08-08T12:00:00Z")
        val expiresAt = Instant.parse("2026-08-22T12:00:00Z")
        first.dismissPopup()
        first.activate(activatedAt, expiresAt)
        first.dismissWarning()

        val restarted = real()
        assertNull(restarted.current().activatedAt)
        restarted.load()
        assertEquals(activatedAt, restarted.current().activatedAt)
        assertEquals(expiresAt, restarted.current().expiresAt)
        assertTrue(restarted.current().popupDismissed)
        assertTrue(restarted.current().warningDismissed)
        restarted.activate(Instant.EPOCH, Instant.EPOCH)
        assertEquals(expiresAt, restarted.current().expiresAt)
    }

    @Test
    fun simulationStateDoesNotOverwriteTheRealTrial() = runBlocking {
        val real = real()
        real.clear()
        val simulation = simulation()
        simulation.clear()
        val realExpiry = Instant.parse("2026-08-22T12:00:00Z")
        real.activate(Instant.parse("2026-08-08T12:00:00Z"), realExpiry)
        simulation.activate(Instant.parse("2026-08-08T12:00:01Z"), Instant.parse("2026-08-08T12:02:01Z"))

        val reloadedReal = real()
        val reloadedSimulation = simulation()
        reloadedReal.load()
        reloadedSimulation.load()
        assertEquals(realExpiry, reloadedReal.current().expiresAt)
        assertEquals(Instant.parse("2026-08-08T12:02:01Z"), reloadedSimulation.current().expiresAt)
        assertFalse(reloadedReal.current().expiresAt == reloadedSimulation.current().expiresAt)
    }

    @Test
    fun availabilityStaysDisabledUntilASuccessfulRead() = runBlocking {
        val store = PromotionAvailabilityStore(context)
        store.clear()
        val restarted = PromotionAvailabilityStore(context)
        assertFalse(restarted.promotionsEnabled())
        restarted.load()
        assertFalse(restarted.promotionsEnabled())
        restarted.setPromotionsEnabled(true)
        val later = PromotionAvailabilityStore(context)
        later.load()
        assertTrue(later.promotionsEnabled())
    }

    private fun real() = ProDiscoveryStore(context, ProDiscoveryStoreKind.REAL)

    private fun simulation() = ProDiscoveryStore(context, ProDiscoveryStoreKind.SIMULATION)
}
