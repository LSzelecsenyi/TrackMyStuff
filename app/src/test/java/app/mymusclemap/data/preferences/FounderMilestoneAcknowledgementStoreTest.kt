package app.mymusclemap.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupFormat
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.EntitlementSources
import app.mymusclemap.domain.entitlement.EntitlementTier
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FounderMilestoneAcknowledgementStoreTest {
    private val now = Instant.parse("2026-10-03T12:00:00Z")
    private lateinit var context: Context

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        FounderProgramStore(context).save(FounderProgramState())
        FounderMilestoneAcknowledgementStore(context).save(FounderMilestoneAcknowledgements())
    }

    @Test
    fun acknowledgementSurvivesANewStoreAndIsSeparateFromProgramState() = runTest {
        val program = FounderProgramStore(context)
        val acknowledgements = FounderMilestoneAcknowledgementStore(context)
        val approved = FounderProgramState(
            status = FounderProgramStatus.Approved,
            enrolledOn = LocalDate.of(2026, 8, 1),
            deadline = LocalDate.of(2026, 9, 15)
        )
        program.save(approved)
        acknowledgements.save(
            FounderMilestoneAcknowledgements(
                temporaryProUnlocked = true,
                qualificationComplete = true,
                founderApproved = true
            )
        )
        assertEquals(approved, FounderProgramStore(context).load())
        assertEquals(
            FounderMilestoneAcknowledgements(
                temporaryProUnlocked = true,
                qualificationComplete = true,
                founderApproved = true
            ),
            FounderMilestoneAcknowledgementStore(context).load()
        )
        assertNotEquals(FounderProgramStore.PREFERENCES_NAME, FounderMilestoneAcknowledgementStore.PREFERENCES_NAME)
        assertFalse(AppBackupFormat.TABLE_NAMES.contains(FounderMilestoneAcknowledgementStore.PREFERENCES_NAME))
    }

    @Test
    fun losingAcknowledgementMetadataCannotRemoveFounderEntitlement() = runTest {
        val approved = FounderProgramState(
            status = FounderProgramStatus.Approved,
            enrolledOn = LocalDate.of(2026, 8, 1),
            deadline = LocalDate.of(2026, 9, 15)
        )
        FounderProgramStore(context).save(approved)
        val acknowledgements = FounderMilestoneAcknowledgementStore(context)
        acknowledgements.save(
            FounderMilestoneAcknowledgements(
                temporaryProUnlocked = true,
                qualificationComplete = true,
                founderApproved = true
            )
        )
        acknowledgements.save(FounderMilestoneAcknowledgements())
        val restored = FounderProgramStore(context).load()
        assertEquals(FounderProgramStatus.Approved, restored.status)
        assertEquals(FounderMilestoneAcknowledgements(), acknowledgements.load())
        val resolved = EntitlementResolver.resolve(EntitlementSources.of(program = restored), now)
        assertTrue(resolved.founderLifetime)
        assertEquals(EntitlementTier.Pro, resolved.tier)
    }

    @Test
    fun acknowledgementFlagsCannotGrantPro() = runTest {
        val enrolled = FounderProgramState(
            status = FounderProgramStatus.ActiveFree,
            enrolledOn = LocalDate.of(2026, 10, 1),
            deadline = LocalDate.of(2026, 11, 15)
        )
        FounderProgramStore(context).save(enrolled)
        FounderMilestoneAcknowledgementStore(context).save(
            FounderMilestoneAcknowledgements(
                temporaryProUnlocked = true,
                qualificationComplete = true,
                founderApproved = true
            )
        )
        val restored = FounderProgramStore(context).load()
        assertEquals(FounderProgramStatus.ActiveFree, restored.status)
        val resolved = EntitlementResolver.resolve(EntitlementSources.of(program = restored), now)
        assertEquals(EntitlementTier.Free, resolved.tier)
        assertFalse(resolved.temporaryTesterPro)
        assertFalse(resolved.founderLifetime)
        assertTrue(FounderMilestoneAcknowledgementStore(context).load().founderApproved)
    }

    @Test
    fun clearingTheProgramLeavesAcknowledgementBehindAndDropsEntitlement() = runTest {
        FounderProgramStore(context).save(
            FounderProgramState(
                status = FounderProgramStatus.ActivePro,
                enrolledOn = LocalDate.of(2026, 10, 1),
                deadline = LocalDate.of(2026, 11, 15)
            )
        )
        FounderMilestoneAcknowledgementStore(context).save(
            FounderMilestoneAcknowledgements(temporaryProUnlocked = true)
        )
        FounderProgramStore(context).save(FounderProgramState())
        assertEquals(FounderProgramState(), FounderProgramStore(context).load())
        assertTrue(FounderMilestoneAcknowledgementStore(context).load().temporaryProUnlocked)
        val resolved = EntitlementResolver.resolve(
            EntitlementSources.of(program = FounderProgramStore(context).load()),
            now
        )
        assertEquals(EntitlementTier.Free, resolved.tier)
        assertFalse(resolved.temporaryTesterPro)
    }
}
