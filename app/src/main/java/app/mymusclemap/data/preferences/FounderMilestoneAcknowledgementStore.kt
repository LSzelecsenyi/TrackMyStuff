package app.mymusclemap.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * Local "already shown" flags for Founder milestone messages.
 *
 * This is UI metadata only. It does not grant Pro, change [app.mymusclemap.domain.entitlement.FounderProgramStatus],
 * or participate in entitlement resolution. It is not part of the portable backup.
 * If these flags are lost, the only effect is that a milestone message can appear again.
 */
private val Context.founderMilestoneDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FounderMilestoneAcknowledgementStore.PREFERENCES_NAME
)

data class FounderMilestoneAcknowledgements(
    val temporaryProUnlocked: Boolean = false,
    val qualificationComplete: Boolean = false,
    val founderApproved: Boolean = false
)

class FounderMilestoneAcknowledgementStore(context: Context) {
    private val dataStore = context.applicationContext.founderMilestoneDataStore

    suspend fun load(): FounderMilestoneAcknowledgements {
        val prefs = dataStore.data.first()
        return FounderMilestoneAcknowledgements(
            temporaryProUnlocked = prefs[KEY_TEMPORARY_PRO] == true,
            qualificationComplete = prefs[KEY_QUALIFICATION] == true,
            founderApproved = prefs[KEY_APPROVED] == true
        )
    }

    suspend fun save(acknowledgements: FounderMilestoneAcknowledgements) {
        dataStore.edit { prefs ->
            prefs[KEY_TEMPORARY_PRO] = acknowledgements.temporaryProUnlocked
            prefs[KEY_QUALIFICATION] = acknowledgements.qualificationComplete
            prefs[KEY_APPROVED] = acknowledgements.founderApproved
        }
    }

    companion object {
        const val PREFERENCES_NAME = "founder_milestone_acknowledgements"
        private val KEY_TEMPORARY_PRO = booleanPreferencesKey("temporary_pro_unlocked_seen")
        private val KEY_QUALIFICATION = booleanPreferencesKey("qualification_complete_seen")
        private val KEY_APPROVED = booleanPreferencesKey("founder_approved_seen")
    }
}
