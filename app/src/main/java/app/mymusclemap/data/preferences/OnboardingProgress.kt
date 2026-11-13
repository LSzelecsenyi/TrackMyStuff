package app.mymusclemap.data.preferences

import app.mymusclemap.domain.onboarding.OnboardingFlags
import kotlinx.coroutines.flow.Flow

/**
 * Onboarding progress for one Strict account. Device appearance settings stay in
 * [ThemePreferences] and are not part of this contract.
 */
interface OnboardingProgress {
    val onboardingFlags: Flow<OnboardingFlags>
    suspend fun currentOnboardingFlags(): OnboardingFlags
    suspend fun isOnboardingCompleted(): Boolean
    suspend fun isOnboardingStarted(): Boolean
    suspend fun isWelcomePending(): Boolean
    suspend fun setWelcomePending(pending: Boolean)
    suspend fun markOnboardingStarted()
    suspend fun markOnboardingCompleted()
    suspend fun setHeatmapSeen()
    suspend fun setWeightIntroduced()
    suspend fun setWeightChartSeen()
    suspend fun setCalendarSeen()
    suspend fun setReminderDismissed()
}
