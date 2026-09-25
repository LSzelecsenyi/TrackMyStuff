package app.mymusclemap.data.repository

import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.onboarding.OnboardingFacts
import app.mymusclemap.domain.onboarding.OnboardingGuide
import app.mymusclemap.domain.onboarding.ProgressiveOnboardingLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class OnboardingRepository(
    private val themePreferences: ThemePreferences,
    private val sessionRepository: WorkoutSessionRepository,
    private val weightRepository: WeightRepository,
    private val templateRepository: WorkoutTemplateRepository
) {
    fun observe(): Flow<OnboardingGuide> {
        return combine(
            themePreferences.onboardingFlags,
            sessionRepository.observeHasCompletedWorkout(),
            weightRepository.observeAll().map { it.isNotEmpty() },
            templateRepository.observeActiveCount().map { it > 0 }
        ) { flags, hasCompletedWorkout, hasWeight, hasPlan ->
            val facts = OnboardingFacts(
                hasCompletedWorkout = hasCompletedWorkout,
                hasWeight = hasWeight,
                hasPlan = hasPlan
            )
            ProgressiveOnboardingLogic.guide(flags, facts)
        }
    }

    suspend fun markStarted() {
        themePreferences.markOnboardingStarted()
    }

    suspend fun markHeatmapSeen() {
        themePreferences.setHeatmapSeen()
        completeIfReady()
    }

    suspend fun markWeightIntroduced() {
        themePreferences.setWeightIntroduced()
        completeIfReady()
    }

    suspend fun markWeightChartSeen() {
        themePreferences.setWeightChartSeen()
        completeIfReady()
    }

    suspend fun markCalendarSeen() {
        themePreferences.setCalendarSeen()
        completeIfReady()
    }

    suspend fun dismissReminder() {
        themePreferences.setReminderDismissed()
    }

    suspend fun markCompleted() {
        themePreferences.markOnboardingCompleted()
    }

    private suspend fun completeIfReady() {
        val flags = themePreferences.currentOnboardingFlags()
        val facts = OnboardingFacts(
            hasCompletedWorkout = sessionRepository.observeHasCompletedWorkout().first(),
            hasWeight = weightRepository.observeAll().first().isNotEmpty(),
            hasPlan = templateRepository.observeActiveCount().first() > 0
        )
        if (ProgressiveOnboardingLogic.shouldComplete(flags, facts)) {
            themePreferences.markOnboardingCompleted()
        }
    }
}
