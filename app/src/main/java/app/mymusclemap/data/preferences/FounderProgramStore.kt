package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.mymusclemap.domain.entitlement.FounderProgramState
import app.mymusclemap.domain.entitlement.FounderProgramStatus
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Local Founder-program record. It is not part of the portable backup.
 * Clearing app data or uninstalling may drop it. That is accepted for the pilot.
 *
 * Milestone acknowledgement lives in [FounderMilestoneAcknowledgementStore].
 * Those flags are not read here and are not part of this record.
 */
private val Context.founderProgramDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FounderProgramStore.PREFERENCES_NAME
)

class FounderProgramStore(context: Context) {
    private val dataStore = context.applicationContext.founderProgramDataStore

    suspend fun load(): FounderProgramState {
        val prefs = dataStore.data.first()
        val status = prefs[KEY_STATUS]?.let { raw ->
            FounderProgramStatus.entries.firstOrNull { it.name == raw }
        } ?: FounderProgramStatus.NotEnrolled
        if (status == FounderProgramStatus.NotEnrolled) {
            return FounderProgramState()
        }
        return FounderProgramState(
            status = status,
            enrolledOn = prefs[KEY_ENROLLED_EPOCH_DAY]?.let(LocalDate::ofEpochDay),
            deadline = prefs[KEY_DEADLINE_EPOCH_DAY]?.let(LocalDate::ofEpochDay),
            feedbackRecorded = prefs[KEY_FEEDBACK] == true,
            testerAnalyticsReportSubmitted = prefs[KEY_ANALYTICS_REPORT] == true,
            rejectionReason = prefs[KEY_REJECTION_REASON],
            backendOwned = prefs[KEY_BACKEND_OWNED] == true,
            serverQualifyingWorkouts = prefs[KEY_SERVER_WORKOUTS] ?: 0,
            serverDistinctDays = prefs[KEY_SERVER_DAYS] ?: 0,
            serverRequiredWorkouts = prefs[KEY_SERVER_REQUIRED_WORKOUTS] ?: 0,
            serverRequiredDistinctDays = prefs[KEY_SERVER_REQUIRED_DAYS] ?: 0,
            serverTemporaryProWorkouts = prefs[KEY_SERVER_TEMPORARY_PRO] ?: 0
        )
    }

    suspend fun loadFeedbackText(): String {
        return dataStore.data.first()[KEY_FEEDBACK_TEXT].orEmpty()
    }

    suspend fun saveFeedbackText(text: String) {
        dataStore.edit { prefs ->
            prefs[KEY_FEEDBACK_TEXT] = text
        }
    }

    suspend fun save(state: FounderProgramState) {
        dataStore.edit { prefs ->
            if (state.status == FounderProgramStatus.NotEnrolled) {
                prefs.clear()
                return@edit
            }
            prefs[KEY_STATUS] = state.status.name
            state.enrolledOn?.let { prefs[KEY_ENROLLED_EPOCH_DAY] = it.toEpochDay() }
                ?: prefs.remove(KEY_ENROLLED_EPOCH_DAY)
            state.deadline?.let { prefs[KEY_DEADLINE_EPOCH_DAY] = it.toEpochDay() }
                ?: prefs.remove(KEY_DEADLINE_EPOCH_DAY)
            prefs[KEY_FEEDBACK] = state.feedbackRecorded
            prefs[KEY_ANALYTICS_REPORT] = state.testerAnalyticsReportSubmitted
            prefs[KEY_BACKEND_OWNED] = state.backendOwned
            prefs[KEY_SERVER_WORKOUTS] = state.serverQualifyingWorkouts
            prefs[KEY_SERVER_DAYS] = state.serverDistinctDays
            prefs[KEY_SERVER_REQUIRED_WORKOUTS] = state.serverRequiredWorkouts
            prefs[KEY_SERVER_REQUIRED_DAYS] = state.serverRequiredDistinctDays
            prefs[KEY_SERVER_TEMPORARY_PRO] = state.serverTemporaryProWorkouts
            state.rejectionReason?.let { prefs[KEY_REJECTION_REASON] = it }
                ?: prefs.remove(KEY_REJECTION_REASON)
        }
    }

    companion object {
        const val PREFERENCES_NAME = "founder_program"
        private val KEY_STATUS = stringPreferencesKey("status")
        private val KEY_ENROLLED_EPOCH_DAY = longPreferencesKey("enrolled_epoch_day")
        private val KEY_DEADLINE_EPOCH_DAY = longPreferencesKey("deadline_epoch_day")
        private val KEY_FEEDBACK = booleanPreferencesKey("feedback_recorded")
        private val KEY_ANALYTICS_REPORT = booleanPreferencesKey("tester_analytics_report_submitted")
        private val KEY_REJECTION_REASON = stringPreferencesKey("rejection_reason")
        private val KEY_FEEDBACK_TEXT = stringPreferencesKey("feedback_text")
        private val KEY_BACKEND_OWNED = booleanPreferencesKey("backend_owned")
        private val KEY_SERVER_WORKOUTS = intPreferencesKey("server_qualifying_workouts")
        private val KEY_SERVER_DAYS = intPreferencesKey("server_distinct_days")
        private val KEY_SERVER_REQUIRED_WORKOUTS = intPreferencesKey("server_required_workouts")
        private val KEY_SERVER_REQUIRED_DAYS = intPreferencesKey("server_required_distinct_days")
        private val KEY_SERVER_TEMPORARY_PRO = intPreferencesKey("server_temporary_pro_workouts")
    }
}
