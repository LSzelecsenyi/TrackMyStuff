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
 * The invitation flags only record whether the post-onboarding offer is waiting or already handled.
 * They do not enroll the tester. An existing install never receives [invitationPending], so a missing
 * flag does not show the invitation on an ordinary launch.
 */
private val Context.founderMilestoneDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FounderMilestoneAcknowledgementStore.PREFERENCES_NAME
)

data class FounderMilestoneAcknowledgements(
    val temporaryProUnlocked: Boolean = false,
    val qualificationComplete: Boolean = false,
    val founderApproved: Boolean = false,
    val invitationPending: Boolean = false,
    val invitationHandled: Boolean = false
)

class FounderMilestoneAcknowledgementStore(context: Context) {
    private val dataStore = context.applicationContext.founderMilestoneDataStore

    suspend fun load(): FounderMilestoneAcknowledgements {
        val prefs = dataStore.data.first()
        return FounderMilestoneAcknowledgements(
            temporaryProUnlocked = prefs[KEY_TEMPORARY_PRO] == true,
            qualificationComplete = prefs[KEY_QUALIFICATION] == true,
            founderApproved = prefs[KEY_APPROVED] == true,
            invitationPending = prefs[KEY_INVITATION_PENDING] == true,
            invitationHandled = prefs[KEY_INVITATION_HANDLED] == true
        )
    }

    suspend fun save(acknowledgements: FounderMilestoneAcknowledgements) {
        dataStore.edit { prefs ->
            prefs[KEY_TEMPORARY_PRO] = acknowledgements.temporaryProUnlocked
            prefs[KEY_QUALIFICATION] = acknowledgements.qualificationComplete
            prefs[KEY_APPROVED] = acknowledgements.founderApproved
            prefs[KEY_INVITATION_PENDING] = acknowledgements.invitationPending
            prefs[KEY_INVITATION_HANDLED] = acknowledgements.invitationHandled
        }
    }

    /** Arms the post-onboarding invitation. A handled invitation is left alone. */
    suspend fun markInvitationPending() {
        dataStore.edit { prefs ->
            if (prefs[KEY_INVITATION_HANDLED] == true || prefs[KEY_INVITATION_PENDING] == true) {
                return@edit
            }
            prefs[KEY_INVITATION_PENDING] = true
        }
    }

    /** Join and Not now both count as handled. This does not enroll or grant Pro. */
    suspend fun markInvitationHandled() {
        dataStore.edit { prefs ->
            prefs[KEY_INVITATION_PENDING] = false
            prefs[KEY_INVITATION_HANDLED] = true
        }
    }

    companion object {
        const val PREFERENCES_NAME = "founder_milestone_acknowledgements"
        private val KEY_TEMPORARY_PRO = booleanPreferencesKey("temporary_pro_unlocked_seen")
        private val KEY_QUALIFICATION = booleanPreferencesKey("qualification_complete_seen")
        private val KEY_APPROVED = booleanPreferencesKey("founder_approved_seen")
        private val KEY_INVITATION_PENDING = booleanPreferencesKey("founder_invitation_pending")
        private val KEY_INVITATION_HANDLED = booleanPreferencesKey("founder_invitation_handled")
    }
}
