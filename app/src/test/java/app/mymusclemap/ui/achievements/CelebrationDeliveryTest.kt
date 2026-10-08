package app.mymusclemap.ui.achievements

import app.mymusclemap.domain.achievements.AchievementId
import app.mymusclemap.domain.achievements.CelebrationAcknowledgement
import app.mymusclemap.domain.achievements.PendingCelebration
import app.mymusclemap.domain.achievements.deliveryKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CelebrationDeliveryTest {
    @Test
    fun heldWorkoutCelebrationsStayOffTheGlobalDialog() {
        val firstStep = journey(AchievementId.FIRST_WORKOUT, "cw-1")
        val thirty = PendingCelebration.WorkoutCountUnlocked(
            achievementId = AchievementId.WORKOUTS_30,
            threshold = 30,
            triggerClientWorkoutId = "cw-30",
            acknowledgement = CelebrationAcknowledgement(achievementId = AchievementId.WORKOUTS_30.name)
        )
        val planner = journey(AchievementId.FIRST_CUSTOM_WORKOUT_PLAN, null)
        val pending = listOf(firstStep, thirty, planner)
        val held = setOf(firstStep.deliveryKey(), thirty.deliveryKey())

        val visible = globalCelebrationCandidates(pending, held, suppressForRoute = false)
        assertEquals(listOf(planner), visible)

        assertTrue(
            globalCelebrationCandidates(pending, held, suppressForRoute = true).isEmpty()
        )
        assertEquals(
            pending,
            globalCelebrationCandidates(pending, emptySet(), suppressForRoute = false)
        )
    }

    @Test
    fun leavingAcknowledgesBeforeNavigation() = runTest {
        val order = mutableListOf<String>()
        val firstStep = journey(AchievementId.FIRST_WORKOUT, "cw-1")
        acknowledgeThenNavigate(
            celebrations = listOf(firstStep),
            acknowledge = {
                order += "ack"
                delay(10)
                order += "acked"
            },
            navigate = { order += "nav" }
        )
        assertEquals(listOf("ack", "acked", "nav"), order)
    }

    private fun journey(id: AchievementId, trigger: String?): PendingCelebration.JourneyUnlocked {
        return PendingCelebration.JourneyUnlocked(
            achievementId = id,
            triggerClientWorkoutId = trigger,
            acknowledgement = CelebrationAcknowledgement(achievementId = id.name)
        )
    }
}
