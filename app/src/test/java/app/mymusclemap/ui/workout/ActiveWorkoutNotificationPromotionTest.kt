package app.mymusclemap.ui.workout

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveWorkoutNotificationPromotionTest {
    @Test
    fun olderAndroidVersionsDoNotRequestPromotion() {
        assertFalse(shouldRequestPromotedOngoing(sdkInt = 26, sdkIntFull = 2600000, canPostPromoted = true))
        assertFalse(shouldRequestPromotedOngoing(sdkInt = 33, sdkIntFull = 3300000, canPostPromoted = true))
        assertFalse(shouldRequestPromotedOngoing(sdkInt = 35, sdkIntFull = 3500000, canPostPromoted = true))
    }

    @Test
    fun android16WithoutThePromotionApiDoesNotRequestIt() {
        assertFalse(
            shouldRequestPromotedOngoing(
                sdkInt = 36,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA,
                canPostPromoted = true
            )
        )
    }

    @Test
    fun promotionIsRequestedOnlyWhenThePlatformAllowsIt() {
        assertTrue(
            shouldRequestPromotedOngoing(
                sdkInt = 36,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
                canPostPromoted = true
            )
        )
        assertFalse(
            shouldRequestPromotedOngoing(
                sdkInt = 36,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
                canPostPromoted = false
            )
        )
        assertTrue(
            shouldRequestPromotedOngoing(
                sdkInt = 37,
                sdkIntFull = 3_700_000,
                canPostPromoted = true
            )
        )
    }

    @Test
    fun android14UsesTheCompactPresentation() {
        assertEquals(
            LockScreenNotificationStrategy.CompactCustom,
            lockScreenNotificationStrategy(sdkInt = 34, sdkIntFull = 34_00000, canPostPromoted = true)
        )
    }

    @Test
    fun eligibleAndroid16UsesThePromotedPresentation() {
        assertEquals(
            LockScreenNotificationStrategy.PromotedStandard,
            lockScreenNotificationStrategy(
                sdkInt = 36,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
                canPostPromoted = true
            )
        )
        assertEquals(
            LockScreenNotificationStrategy.PromotedStandard,
            lockScreenNotificationStrategy(sdkInt = 37, sdkIntFull = 3_700_000, canPostPromoted = true)
        )
    }

    @Test
    fun promotionUnavailableKeepsTheCompactPresentation() {
        assertEquals(
            LockScreenNotificationStrategy.CompactCustom,
            lockScreenNotificationStrategy(
                sdkInt = 36,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA,
                canPostPromoted = true
            )
        )
        assertEquals(
            LockScreenNotificationStrategy.CompactCustom,
            lockScreenNotificationStrategy(
                sdkInt = 36,
                sdkIntFull = Build.VERSION_CODES_FULL.BAKLAVA_1,
                canPostPromoted = false
            )
        )
    }
}
