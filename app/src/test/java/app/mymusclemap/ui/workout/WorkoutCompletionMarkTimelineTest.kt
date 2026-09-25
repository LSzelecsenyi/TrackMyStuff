package app.mymusclemap.ui.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutCompletionMarkTimelineTest {
    @Test
    fun checkDrawsBeforeCircle() {
        val early = WorkoutCompletionMarkTimeline.at(150L, reducedMotion = false)
        assertTrue(early.checkFraction > 0f)
        assertEquals(0f, early.circleFraction, 0.0f)
        val afterCheck = WorkoutCompletionMarkTimeline.at(
            WorkoutCompletionMarkTimeline.CheckDurationMs.toLong(),
            reducedMotion = false
        )
        assertEquals(1f, afterCheck.checkFraction, 0.0f)
        assertEquals(0f, afterCheck.circleFraction, 0.0f)
        val duringCircle = WorkoutCompletionMarkTimeline.at(
            WorkoutCompletionMarkTimeline.CheckDurationMs + 100L,
            reducedMotion = false
        )
        assertEquals(1f, duringCircle.checkFraction, 0.0f)
        assertTrue(duringCircle.circleFraction > 0f)
        assertTrue(duringCircle.circleFraction < 1f)
    }

    @Test
    fun scaleRisesDuringCircleThenSettlesToOne() {
        val duringCircle = WorkoutCompletionMarkTimeline.at(
            WorkoutCompletionMarkTimeline.CheckDurationMs +
                WorkoutCompletionMarkTimeline.CircleDurationMs / 2L,
            reducedMotion = false
        )
        assertTrue(duringCircle.scale > 1f)
        assertTrue(duringCircle.scale <= WorkoutCompletionMarkTimeline.PeakScale)
        val end = WorkoutCompletionMarkTimeline.at(
            WorkoutCompletionMarkTimeline.TotalDurationMs.toLong(),
            reducedMotion = false
        )
        assertEquals(1f, end.checkFraction, 0.0f)
        assertEquals(1f, end.circleFraction, 0.0f)
        assertEquals(1f, end.scale, 0.0f)
    }

    @Test
    fun reducedMotionJumpsToStableEndState() {
        val reduced = WorkoutCompletionMarkTimeline.at(0L, reducedMotion = true)
        assertEquals(1f, reduced.checkFraction, 0.0f)
        assertEquals(1f, reduced.circleFraction, 0.0f)
        assertEquals(1f, reduced.scale, 0.0f)
    }
}
