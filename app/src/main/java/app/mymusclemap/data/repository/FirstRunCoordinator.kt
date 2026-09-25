package app.mymusclemap.data.repository

import androidx.room.withTransaction
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.exercise.ExerciseSaveResult
import app.mymusclemap.domain.exercise.StarterCatalog

/**
 * First-run catalog seed and onboarding gate.
 *
 * Onboarding completion and progressive-discovery flags live in DataStore, not in
 * the v1 backup file. Appearance restore preserves those flags so restore cannot
 * reopen first-run or reset discoveries. Starter exercises are inserted only when
 * the catalog is empty and onboarding has not started or completed. A restored,
 * upgraded, or already-used catalog is left untouched, and existing users are
 * migrated to completed onboarding instead of the progressive journey.
 */
class FirstRunCoordinator(
    private val database: WeightDatabase,
    private val exerciseRepository: ExerciseRepository,
    private val themePreferences: ThemePreferences
) {
    suspend fun prepare(): FirstRunDecision {
        if (themePreferences.isOnboardingCompleted()) {
            return FirstRunDecision.Ready
        }
        if (themePreferences.isOnboardingStarted()) {
            return FirstRunDecision.Ready
        }
        val seeded = seedStarterCatalogIfEmpty()
        if (!seeded) {
            themePreferences.markOnboardingCompleted()
            return FirstRunDecision.Ready
        }
        return FirstRunDecision.ShowOnboarding
    }

    suspend fun markOnboardingStarted() {
        themePreferences.markOnboardingStarted()
    }

    suspend fun completeOnboarding() {
        themePreferences.markOnboardingCompleted()
    }

    private suspend fun seedStarterCatalogIfEmpty(): Boolean {
        return database.withTransaction {
            if (database.exerciseDao().countAll() > 0) {
                return@withTransaction false
            }
            StarterCatalog.drafts.forEach { draft ->
                val result = exerciseRepository.save(draft)
                check(result is ExerciseSaveResult.Created) {
                    "Starter catalog seed failed for ${draft.name}: $result"
                }
            }
            true
        }
    }
}

sealed class FirstRunDecision {
    data object ShowOnboarding : FirstRunDecision()
    data object Ready : FirstRunDecision()
}
