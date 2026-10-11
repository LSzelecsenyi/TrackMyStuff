package app.mymusclemap.data.account

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.AccountDatabaseLocation
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
        listOf(userA, userB).forEach { userId ->
            context.getDatabasePath(accountDatabaseName(userId)).delete()
            accountFile(userId).delete()
        }
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
        assertEquals("workouts", accountFile(userA).readText())
        assertFalse(context.getDatabasePath(accountDatabaseName(userA)).exists())
        assertFalse(accounts.legacyUnclaimed())

        assertEquals(LegacyClaim.ALREADY_MINE, accounts.claimLegacy(userA))
        assertEquals(LegacyClaim.OWNED_BY_OTHER, accounts.claimLegacy(userB))
        assertEquals("workouts", accountFile(userA).readText())
        assertFalse(accountFile(userB).exists())
    }

    @Test
    fun anInterruptedMoveStillBelongsToTheSameAccount() {
        val legacy = context.getDatabasePath("weight_tracker.db")
        val target = accountFile(userA)
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
        val database = accountFile(userA)
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
        assertFalse(accountFile(userA).exists())
    }

    @Test
    fun claimDoesNotReplaceAnUnmigratedAccountDatabase() {
        val legacy = context.getDatabasePath("weight_tracker.db")
        legacy.parentFile?.mkdirs()
        legacy.writeText("legacy")
        val existing = context.getDatabasePath(accountDatabaseName(userA))
        existing.parentFile?.mkdirs()
        existing.writeText("real-account")

        assertEquals(LegacyClaim.OWNED_BY_OTHER, accounts.claimLegacy(userA))
        assertEquals("legacy", legacy.readText())
        assertEquals("real-account", existing.readText())
        assertFalse(accountFile(userA).exists())
    }

    private fun accountFile(userId: String): File {
        return AccountDatabaseLocation.accountFile(context, accountDatabaseName(userId))
    }
}
