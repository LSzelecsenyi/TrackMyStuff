package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.mymusclemap.domain.onboarding.OnboardingFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Onboarding and progressive-discovery flags for one signed-in Strict user.
 * The file is not part of the portable backup.
 */
class AccountOnboardingStore(
    context: Context,
    userId: String
) : OnboardingProgress {
    private val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = {
            File(context.applicationContext.filesDir, "datastore/onboarding_$userId.preferences_pb")
        }
    )

    override val onboardingFlags: Flow<OnboardingFlags> = dataStore.data.map { preferences ->
        OnboardingFlags(
            completed = preferences[KEY_ONBOARDING_COMPLETED] == true,
            started = preferences[KEY_ONBOARDING_STARTED] == true,
            heatmapSeen = preferences[KEY_ONBOARDING_HEATMAP_SEEN] == true,
            weightIntroduced = preferences[KEY_ONBOARDING_WEIGHT_INTRODUCED] == true,
            weightChartSeen = preferences[KEY_ONBOARDING_WEIGHT_CHART_SEEN] == true,
            calendarSeen = preferences[KEY_ONBOARDING_CALENDAR_SEEN] == true,
            reminderDismissed = preferences[KEY_ONBOARDING_REMINDER_DISMISSED] == true
        )
    }

    override suspend fun currentOnboardingFlags(): OnboardingFlags = onboardingFlags.first()

    override suspend fun isOnboardingCompleted(): Boolean = currentOnboardingFlags().completed

    override suspend fun isOnboardingStarted(): Boolean = currentOnboardingFlags().started

    override suspend fun isWelcomePending(): Boolean {
        return dataStore.data.first()[KEY_ONBOARDING_WELCOME_PENDING] == true
    }

    override suspend fun setWelcomePending(pending: Boolean) {
        dataStore.edit { prefs ->
            if (pending) {
                prefs[KEY_ONBOARDING_WELCOME_PENDING] = true
            } else {
                prefs.remove(KEY_ONBOARDING_WELCOME_PENDING)
            }
        }
    }

    override suspend fun markOnboardingStarted() {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_STARTED] = true
            prefs.remove(KEY_ONBOARDING_WELCOME_PENDING)
        }
    }

    override suspend fun setHeatmapSeen() {
        dataStore.edit { it[KEY_ONBOARDING_HEATMAP_SEEN] = true }
    }

    override suspend fun setWeightIntroduced() {
        dataStore.edit { it[KEY_ONBOARDING_WEIGHT_INTRODUCED] = true }
    }

    override suspend fun setWeightChartSeen() {
        dataStore.edit { it[KEY_ONBOARDING_WEIGHT_CHART_SEEN] = true }
    }

    override suspend fun setCalendarSeen() {
        dataStore.edit { it[KEY_ONBOARDING_CALENDAR_SEEN] = true }
    }

    override suspend fun setReminderDismissed() {
        dataStore.edit { it[KEY_ONBOARDING_REMINDER_DISMISSED] = true }
    }

    override suspend fun markOnboardingCompleted() {
        dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
            prefs.remove(KEY_ONBOARDING_WELCOME_PENDING)
        }
    }

    suspend fun hasAnyFlag(): Boolean {
        val flags = currentOnboardingFlags()
        return flags.completed ||
            flags.started ||
            flags.heatmapSeen ||
            flags.weightIntroduced ||
            flags.weightChartSeen ||
            flags.calendarSeen ||
            flags.reminderDismissed ||
            isWelcomePending()
    }

    suspend fun adoptIfEmpty(flags: OnboardingFlags, welcomePending: Boolean) {
        if (hasAnyFlag()) {
            return
        }
        dataStore.edit { prefs ->
            if (flags.completed) prefs[KEY_ONBOARDING_COMPLETED] = true
            if (flags.started) prefs[KEY_ONBOARDING_STARTED] = true
            if (flags.heatmapSeen) prefs[KEY_ONBOARDING_HEATMAP_SEEN] = true
            if (flags.weightIntroduced) prefs[KEY_ONBOARDING_WEIGHT_INTRODUCED] = true
            if (flags.weightChartSeen) prefs[KEY_ONBOARDING_WEIGHT_CHART_SEEN] = true
            if (flags.calendarSeen) prefs[KEY_ONBOARDING_CALENDAR_SEEN] = true
            if (flags.reminderDismissed) prefs[KEY_ONBOARDING_REMINDER_DISMISSED] = true
            if (welcomePending) prefs[KEY_ONBOARDING_WELCOME_PENDING] = true
        }
    }

    private companion object {
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_ONBOARDING_STARTED = booleanPreferencesKey("onboarding_started")
        val KEY_ONBOARDING_WELCOME_PENDING = booleanPreferencesKey("onboarding_welcome_pending")
        val KEY_ONBOARDING_HEATMAP_SEEN = booleanPreferencesKey("onboarding_heatmap_seen")
        val KEY_ONBOARDING_WEIGHT_INTRODUCED = booleanPreferencesKey("onboarding_weight_introduced")
        val KEY_ONBOARDING_WEIGHT_CHART_SEEN = booleanPreferencesKey("onboarding_weight_chart_seen")
        val KEY_ONBOARDING_CALENDAR_SEEN = booleanPreferencesKey("onboarding_calendar_seen")
        val KEY_ONBOARDING_REMINDER_DISMISSED = booleanPreferencesKey("onboarding_reminder_dismissed")
    }
}
