package app.mymusclemap.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class WelcomeBackStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun accountFilesDoNotShareAGrant() = runBlocking {
        val accountA = WelcomeBackStore(context, WelcomeBackStoreKind.REAL, userId = "account-a")
        val accountB = WelcomeBackStore(context, WelcomeBackStoreKind.REAL, userId = "account-b")
        val start = Instant.parse("2026-10-09T08:00:00Z")
        accountA.activate(start, start.plusSeconds(60), start.plusSeconds(120), "workout-a")
        accountB.load()
        assertEquals(start, accountA.current().activatedAt)
        assertEquals("workout-a", accountA.current().activatedWorkoutId)
        assertNull(accountB.current().activatedAt)
        assertNull(accountB.current().activatedWorkoutId)
    }
}
