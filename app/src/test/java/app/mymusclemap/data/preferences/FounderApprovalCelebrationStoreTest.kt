package app.mymusclemap.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class FounderApprovalCelebrationStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var sessionUserId: String? = "account-a"

    @Test
    fun acknowledgementSurvivesRestartAndDoesNotLeakAcrossAccounts() = runBlocking {
        store().clear()
        val store = store()
        store.acknowledge("account-a")
        assertTrue(store.isAcknowledged("account-a"))
        assertFalse(store.isAcknowledged("account-b"))

        val restarted = store()
        assertFalse(restarted.isLoaded())
        restarted.load()
        assertTrue(restarted.isAcknowledged("account-a"))
        assertFalse(restarted.isAcknowledged("account-b"))
    }

    @Test
    fun acknowledgingTheCelebrationLeavesPermanentRecognitionEarned() = runBlocking {
        val celebrations = store()
        celebrations.clear()
        val recognition = FounderRecognitionStore(
            context = context,
            sessionUserId = { sessionUserId }
        )
        recognition.clear()
        val grantedAt = Instant.parse("2026-06-01T00:00:00Z")
        recognition.confirm("account-a", grantedAt)
        celebrations.acknowledge("account-a")

        assertTrue(celebrations.isAcknowledged("account-a"))
        assertTrue(recognition.current().recognized)
        assertTrue(recognition.current().grantedAt == grantedAt)

        sessionUserId = "account-b"
        assertFalse(recognition.current().recognized)
        assertFalse(celebrations.isAcknowledged("account-b"))
    }

    private fun store() = FounderApprovalCelebrationStore(context)
}
