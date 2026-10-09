package app.mymusclemap.data.account

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.account.LegacyClaim
import app.mymusclemap.domain.account.accountDatabaseName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AccountDirectoryTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val accounts = AccountDirectory(context)
    private val userA = "11111111-1111-1111-1111-111111111111"
    private val userB = "22222222-2222-2222-2222-222222222222"

    @Before
    fun clean() {
        context.getDatabasePath("weight_tracker.db").delete()
        context.getDatabasePath(accountDatabaseName(userA)).delete()
        context.getDatabasePath(accountDatabaseName(userB)).delete()
        File(context.noBackupFilesDir, "legacy_claim.txt").delete()
        File(context.noBackupFilesDir, "active_account.txt").delete()
        File(context.noBackupFilesDir, "verified_account.json").delete()
    }

    @Test
    fun theFirstAccountClaimsLegacyDataAndARetryDoesNotMoveItAgain() {
        val legacy = context.getDatabasePath("weight_tracker.db")
        legacy.parentFile?.mkdirs()
        legacy.writeText("workouts")
        assertTrue(accounts.legacyUnclaimed())

        assertEquals(LegacyClaim.MOVE, accounts.claimLegacy(userA))
        assertFalse(legacy.exists())
        assertEquals("workouts", context.getDatabasePath(accountDatabaseName(userA)).readText())
        assertFalse(accounts.legacyUnclaimed())

        assertEquals(LegacyClaim.ALREADY_MINE, accounts.claimLegacy(userA))
        assertEquals(LegacyClaim.OWNED_BY_OTHER, accounts.claimLegacy(userB))
        assertEquals("workouts", context.getDatabasePath(accountDatabaseName(userA)).readText())
        assertFalse(context.getDatabasePath(accountDatabaseName(userB)).exists())
    }

    @Test
    fun anInterruptedMoveStillBelongsToTheSameAccount() {
        val legacy = context.getDatabasePath("weight_tracker.db")
        val target = context.getDatabasePath(accountDatabaseName(userA))
        target.parentFile?.mkdirs()
        target.writeText("kept")
        assertFalse(legacy.exists())

        assertEquals(LegacyClaim.ALREADY_MINE, accounts.claimLegacy(userA))
        assertEquals("kept", target.readText())
        assertEquals(LegacyClaim.OWNED_BY_OTHER, accounts.claimLegacy(userB))
    }

    @Test
    fun signOutClearsTheActiveAccountAndLeavesItsDatabase() {
        val profile = VerifiedAccountProfile(userA, "a@example.com", "Ada")
        accounts.activate(profile)
        val database = context.getDatabasePath(accountDatabaseName(userA))
        database.parentFile?.mkdirs()
        database.writeText("kept")

        accounts.clearActive()
        assertEquals(null, accounts.activeUserId())
        assertEquals("kept", database.readText())

        accounts.activate(profile)
        assertEquals(userA, accounts.activeUserId())
        assertEquals("Ada", accounts.profile()?.displayName)
        assertEquals("kept", database.readText())
    }

    @Test
    fun nothingToClaimLeavesBothAccountsEmpty() {
        assertEquals(LegacyClaim.NOTHING_TO_CLAIM, accounts.claimLegacy(userA))
        assertFalse(context.getDatabasePath(accountDatabaseName(userA)).exists())
    }
}
