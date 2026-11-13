package app.mymusclemap.data.account

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.local.AccountDatabaseLocation
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.AccountOnboardingStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.domain.account.accountDatabaseName
import app.mymusclemap.domain.entitlement.FounderProgramAvailability
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.ui.founder.shouldOfferFounderOnboardingInvitation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class AccountOnboardingScopeTest {
    private val userA = "11111111-1111-1111-1111-111111111111"
    private val userB = "22222222-2222-2222-2222-222222222222"
    private val userC = "33333333-3333-3333-3333-333333333333"
    private val userD = "44444444-4444-4444-4444-444444444444"

    @After
    fun cleanup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.noBackupFilesDir, "account_scope_attribution.txt").delete()
        AccountDatabaseLocation.userDataDirectory(context).deleteRecursively()
        runBlocking {
            ThemePreferences(context).clearOnboardingProgress()
            FounderMilestoneAcknowledgementStore(context).save(FounderMilestoneAcknowledgements())
        }
    }

    @Test
    fun aSingleAccountKeepsCompletedOnboardingAndASecondAccountStartsItsOwn() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val device = ThemePreferences(context)
        device.clearOnboardingProgress()
        device.markOnboardingCompleted()
        val legacyMilestones = FounderMilestoneAcknowledgementStore(context)
        legacyMilestones.save(FounderMilestoneAcknowledgements(invitationHandled = true))
        accountFile(context, userA)
        val first = open(context, userA, device, legacyMilestones)
        assertEquals(FirstRunDecision.Ready, first.coordinator.prepare())
        assertEquals(22, first.database.exerciseDao().countAll())
        assertTrue(first.onboarding.isOnboardingCompleted())
        assertTrue(first.milestones.load().invitationHandled)
        first.database.close()

        accountFile(context, userB)
        val second = open(context, userB, device, legacyMilestones)
        assertEquals(FirstRunDecision.ShowOnboarding, second.coordinator.prepare())
        assertEquals(22, second.database.exerciseDao().countAll())
        assertFalse(second.onboarding.isOnboardingCompleted())
        assertFalse(second.milestones.load().invitationHandled)
        second.coordinator.completeOnboarding()
        second.database.close()

        val returningDatabase = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val returning = FirstRunCoordinator(
            returningDatabase,
            ExerciseRepository(
                dao = returningDatabase.exerciseDao(),
                clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
            ),
            first.onboarding,
            beforePrepare = { preexisting ->
                AccountScopedStateMigration(
                    context,
                    userA,
                    first.onboarding,
                    device,
                    first.milestones,
                    legacyMilestones
                ).run(preexisting)
            }
        )
        assertEquals(FirstRunDecision.Ready, returning.prepare())
        assertTrue(first.onboarding.isOnboardingCompleted())
        assertFalse(first.onboarding.isWelcomePending())
        returningDatabase.close()
        assertTrue(
            shouldOfferFounderOnboardingInvitation(
                FounderProgramAvailability.Open,
                FounderProgramStatus.NotEnrolled,
                FounderMilestoneAcknowledgements()
            )
        )
    }

    @Test
    fun multipleAccountsDoNotInheritDeviceOnboarding() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.noBackupFilesDir, "account_scope_attribution.txt").delete()
        val device = ThemePreferences(context)
        device.clearOnboardingProgress()
        device.markOnboardingCompleted()
        accountFile(context, userC)
        accountFile(context, userD)
        val first = open(context, userC, device, FounderMilestoneAcknowledgementStore(context))
        val second = open(context, userD, device, FounderMilestoneAcknowledgementStore(context))
        assertEquals(FirstRunDecision.ShowOnboarding, first.coordinator.prepare())
        assertEquals(FirstRunDecision.ShowOnboarding, second.coordinator.prepare())
        assertFalse(first.onboarding.isOnboardingCompleted())
        assertFalse(second.onboarding.isOnboardingCompleted())
        first.database.close()
        second.database.close()
    }

    private fun accountFile(context: Context, userId: String) {
        val file = AccountDatabaseLocation.accountFile(context, accountDatabaseName(userId))
        file.parentFile?.mkdirs()
        file.writeText("")
    }

    private fun open(
        context: Context,
        userId: String,
        device: ThemePreferences,
        legacyMilestones: FounderMilestoneAcknowledgementStore
    ): OpenedAccount {
        val database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = Clock.fixed(Instant.ofEpochMilli(1_000L), ZoneOffset.UTC)
        )
        val onboarding = AccountOnboardingStore(context, userId)
        val milestones = FounderMilestoneAcknowledgementStore(context, userId)
        val coordinator = FirstRunCoordinator(
            database,
            repository,
            onboarding,
            beforePrepare = { preexisting ->
                AccountScopedStateMigration(
                    context,
                    userId,
                    onboarding,
                    device,
                    milestones,
                    legacyMilestones
                ).run(preexisting)
            }
        )
        return OpenedAccount(database, onboarding, milestones, coordinator)
    }

    private class OpenedAccount(
        val database: WeightDatabase,
        val onboarding: AccountOnboardingStore,
        val milestones: FounderMilestoneAcknowledgementStore,
        val coordinator: FirstRunCoordinator
    )
}
