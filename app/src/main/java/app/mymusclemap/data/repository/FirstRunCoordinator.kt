package app.mymusclemap.data.repository

import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.OnboardingProgress

/**
 * Opens an account by reconciling the official exercise catalog, then decides
 * whether this account still needs onboarding. Catalog availability does not
 * depend on onboarding, Founder enrollment, or PRO.
 * An account that already contains training data is treated as an existing
 * user so onboarding does not start again. A completed or started account
 * still receives any catalog entries it does not yet have.
 */
class FirstRunCoordinator(
    private val database: WeightDatabase,
    private val exerciseRepository: ExerciseRepository,
    private val onboarding: OnboardingProgress,
    private val catalog: SystemCatalogReconciler = SystemCatalogReconciler(database, exerciseRepository),
    private val beforePrepare: suspend (hasPreexistingContent: Boolean) -> Unit = {}
) {
    suspend fun prepare(): FirstRunDecision {
        val preexisting = hasPreexistingContent()
        beforePrepare(preexisting)
        if (preexisting && !onboarding.isOnboardingCompleted() && !onboarding.isOnboardingStarted() && !onboarding.isWelcomePending()) {
            onboarding.markOnboardingCompleted()
        }
        catalog.reconcile()
        if (onboarding.isOnboardingCompleted() || onboarding.isOnboardingStarted()) {
            return FirstRunDecision.Ready
        }
        if (!onboarding.isWelcomePending()) {
            onboarding.setWelcomePending(true)
        }
        return FirstRunDecision.ShowOnboarding
    }

    suspend fun markOnboardingStarted() {
        onboarding.markOnboardingStarted()
    }

    suspend fun completeOnboarding() {
        onboarding.markOnboardingCompleted()
    }

    private suspend fun hasPreexistingContent(): Boolean {
        return database.exerciseDao().countAll() > 0 ||
            database.workoutTemplateDao().countAll() > 0 ||
            database.workoutSessionDao().countAll() > 0 ||
            database.weightMeasurementDao().getAllAscending().isNotEmpty()
    }
}

sealed class FirstRunDecision {
    data object ShowOnboarding : FirstRunDecision()
    data object Ready : FirstRunDecision()
}
