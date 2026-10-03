package app.mymusclemap.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class FounderProgramStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun founderProgramStateSurvivesANewStoreInstance() = runTest {
        val enrolled = FounderProgramState(
            status = FounderProgramStatus.ActivePro,
            enrolledOn = LocalDate.of(2026, 1, 1),
            deadline = LocalDate.of(2026, 2, 15),
            feedbackRecorded = true,
            testerAnalyticsReportSubmitted = false
        )
        FounderProgramStore(context).save(enrolled)
        val restored = FounderProgramStore(context).load()
        assertEquals(enrolled, restored)
        assertNotEquals("weight_tracker_settings", FounderProgramStore.PREFERENCES_NAME)
    }

    @Test
    fun clearingTheProgramReturnsToNotEnrolled() = runTest {
        FounderProgramStore(context).save(
            FounderProgramState(
                status = FounderProgramStatus.Rejected,
                enrolledOn = LocalDate.of(2026, 1, 1),
                deadline = LocalDate.of(2026, 2, 15),
                rejectionReason = "invalid report"
            )
        )
        FounderProgramStore(context).save(FounderProgramState())
        assertEquals(FounderProgramState(), FounderProgramStore(context).load())
    }
}
