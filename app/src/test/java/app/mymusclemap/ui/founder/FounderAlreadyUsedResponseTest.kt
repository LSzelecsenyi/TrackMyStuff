package app.mymusclemap.ui.founder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.mymusclemap.data.auth.GoogleIdTokenRequest
import app.mymusclemap.data.auth.GoogleIdentityProvider
import app.mymusclemap.data.auth.OkHttpStrictBackendApi
import app.mymusclemap.data.auth.StoredStrictSession
import app.mymusclemap.data.auth.StrictAuthRepository
import app.mymusclemap.data.auth.StrictBearerToken
import app.mymusclemap.data.auth.StrictSessionStore
import app.mymusclemap.data.founder.BackendFounderSnapshot
import app.mymusclemap.data.founder.FounderJoinCoordinator
import app.mymusclemap.data.founder.FounderJoinResult
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import app.mymusclemap.domain.entitlement.FounderQualification
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * The production body is `{"errorCode":"FOUNDER_ALREADY_USED","message":"..."}` with HTTP 409.
 * The displayed Hungarian copy must come from that body, not from a pre-set notice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "hu-rHU-w360dp-h2000dp")
class FounderAlreadyUsedResponseTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val userId = "11111111-1111-4111-8111-111111111111"
    private val alreadyUsedBody =
        """{"errorCode":"FOUNDER_ALREADY_USED","message":"Founder enrollment was already used by a deleted account for this Google sign-in."}"""
    private val heading = "A Founder lehetőséget már felhasználtad."
    private val description =
        "Ezzel a Google-fiókkal korábban már csatlakoztál a Founder Programhoz. A csatlakozás egyszeri lehetőség, új regisztrációval sem használható fel ismét."
    private val action = "Rendben"
    private val genericRetry = "A Founding Tester jelentkezés nem fejeződött be. Újra próbálkozhatsz."

    @Test
    fun aProduction409ShowsTheHungarianAlreadyUsedOutcomeOnTheInvitation() {
        val outcome = runBlocking { enroll(409, alreadyUsedBody) }
        assertEquals(FounderJoinResult.AlreadyUsed, outcome.result)
        assertTrue(outcome.applied.isEmpty())
        var continued = 0
        showInvitation(outcome.result.toNotice()) { continued += 1 }
        assertAlreadyUsed()
        composeRule.onNodeWithText(action).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun aProduction409ShowsTheHungarianAlreadyUsedOutcomeInFounderSettings() {
        val outcome = runBlocking { enroll(409, alreadyUsedBody) }
        assertEquals(FounderJoinResult.AlreadyUsed, outcome.result)
        assertTrue(outcome.applied.isEmpty())
        var continued = 0
        showProgram(outcome.result.toNotice()) { continued += 1 }
        assertAlreadyUsed()
        composeRule.onNodeWithText(action).performClick()
        assertEquals(1, continued)
    }

    @Test
    fun aServerFailureStillShowsTheGenericRetryOnTheEnrollmentScreen() {
        val outcome = runBlocking { enroll(500, """{"errorCode":"INTERNAL_ERROR","message":"failed"}""") }
        assertEquals(FounderJoinResult.Rejected, outcome.result)
        assertTrue(outcome.applied.isEmpty())
        showInvitation(outcome.result.toNotice()) {}
        composeRule.onNodeWithText(genericRetry).assertIsDisplayed()
        composeRule.onNodeWithTag(FOUNDER_ENROLL).assertIsDisplayed()
        composeRule.onNodeWithText(heading).assertDoesNotExist()
    }

    private fun assertAlreadyUsed() {
        composeRule.onNodeWithText(heading).assertIsDisplayed()
        composeRule.onNodeWithText(description).assertIsDisplayed()
        composeRule.onNodeWithText(action).assertIsDisplayed()
        composeRule.onNodeWithText(genericRetry).assertDoesNotExist()
        composeRule.onNodeWithTag(FOUNDER_ENROLL).assertDoesNotExist()
        composeRule.onNodeWithTag(FOUNDER_BADGE).assertDoesNotExist()
    }

    private fun showInvitation(notice: FounderJoinNotice, onContinue: () -> Unit) {
        val rules = FounderProgramRules.Production
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 1f)) {
                WeightTrackerThemeForPreview {
                    Box(Modifier.width(360.dp).height(2000.dp).fillMaxSize()) {
                        FounderInvitationScreen(
                            rules = rules,
                            onJoin = {},
                            onNotNow = onContinue,
                            joinNotice = notice
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun showProgram(notice: FounderJoinNotice, onContinue: () -> Unit) {
        val rules = FounderProgramRules.Production
        val qualification = FounderQualification(
            nativeCompletedWorkouts = 0,
            distinctNativeWorkoutDays = 0,
            feedbackRecorded = false,
            testerAnalyticsReportSubmitted = false,
            rules = rules
        )
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = density.density, fontScale = 1f)) {
                WeightTrackerThemeForPreview {
                    Box(Modifier.width(360.dp).height(2000.dp).fillMaxSize()) {
                        FounderProgramScreen(
                            state = FounderProgramUiState(
                                loading = false,
                                status = FounderProgramStatus.NotEnrolled,
                                rules = rules,
                                joinNotice = notice,
                                journey = founderJourney(
                                    status = FounderProgramStatus.NotEnrolled,
                                    qualification = qualification,
                                    acknowledgements = FounderMilestoneAcknowledgements()
                                )
                            ),
                            onBack = onContinue,
                            onEnroll = {},
                            onFeedbackChange = {},
                            onSubmitReport = {},
                            onAcknowledgeMilestone = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private suspend fun enroll(status: Int, body: String): EnrollmentOutcome {
        val server = MockWebServer()
        server.start()
        val sessions = MemorySession()
        val http = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
        val api = OkHttpStrictBackendApi(server.url("/").toString(), http, sessions)
        val applied = mutableListOf<BackendFounderSnapshot>()
        val join = FounderJoinCoordinator(
            auth = StrictAuthRepository(IdleGoogle, api, sessions),
            api = api,
            applyEnrollment = { applied += it },
            zone = ZoneOffset.UTC
        )
        return try {
            sessions.write(StoredStrictSession(StrictBearerToken("existing-bearer"), "2026-11-03T00:00:00Z", userId))
            server.enqueue(MockResponse().setBody("""{"id":"$userId"}"""))
            server.enqueue(
                MockResponse()
                    .setResponseCode(status)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body)
            )
            EnrollmentOutcome(join.join(), applied)
        } finally {
            server.shutdown()
            http.dispatcher.executorService.shutdown()
            http.connectionPool.evictAll()
        }
    }

    private data class EnrollmentOutcome(
        val result: FounderJoinResult,
        val applied: List<BackendFounderSnapshot>
    )

    private object IdleGoogle : GoogleIdentityProvider {
        override suspend fun requestIdToken(): GoogleIdTokenRequest = GoogleIdTokenRequest.Cancelled
    }

    private class MemorySession : StrictSessionStore {
        private var current: StoredStrictSession? = null
        override fun read(): StoredStrictSession? = current
        override fun write(session: StoredStrictSession) {
            current = session
        }
        override fun clear() {
            current = null
        }
    }
}
