package app.mymusclemap.data.account

import app.mymusclemap.data.local.AccountDatabaseLocation
import app.mymusclemap.data.preferences.AccountOnboardingStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgements
import app.mymusclemap.data.preferences.ThemePreferences
import android.content.Context
import java.io.File

/**
 * Moves device-wide onboarding and Founder-invitation flags onto one account
 * only when that account is the only one on the device. Other accounts keep
 * their own empty state. An account that already has training data is not
 * sent through onboarding again; that decision stays in [app.mymusclemap.data.repository.FirstRunCoordinator].
 */
class AccountScopedStateMigration(
    context: Context,
    private val userId: String,
    private val onboarding: AccountOnboardingStore,
    private val deviceOnboarding: ThemePreferences,
    private val milestones: FounderMilestoneAcknowledgementStore,
    private val legacyMilestones: FounderMilestoneAcknowledgementStore
) {
    private val attributionFile = File(context.applicationContext.noBackupFilesDir, FILE_NAME)
    private val appContext = context.applicationContext

    suspend fun run(hasPreexistingContent: Boolean) {
        val target = attributedUserId()
        if (target == userId) {
            onboarding.adoptIfEmpty(
                deviceOnboarding.currentOnboardingFlags(),
                deviceOnboarding.isWelcomePending()
            )
            milestones.adoptIfEmpty(legacyMilestones.load())
            return
        }
        if (hasPreexistingContent && legacyMilestones.load().invitationHandled) {
            milestones.adoptIfEmpty(FounderMilestoneAcknowledgements(invitationHandled = true))
        }
    }

    private fun attributedUserId(): String? {
        if (attributionFile.isFile) {
            val stored = attributionFile.readText().trim()
            return stored.takeUnless { it.isEmpty() || it == NONE }
        }
        val ids = accountIds()
        val target = if (ids.size == 1) ids.single() else null
        attributionFile.parentFile?.mkdirs()
        attributionFile.writeText(target ?: NONE)
        return target
    }

    private fun accountIds(): Set<String> {
        val names = buildList {
            addAll(AccountDatabaseLocation.userDataDirectory(appContext).list().orEmpty())
            addAll(AccountDatabaseLocation.databasesDirectory(appContext).list().orEmpty())
        }
        return names.mapNotNull { name ->
            if (!AccountDatabaseLocation.isAccountDatabase(name)) {
                null
            } else {
                name.removePrefix("account_").removeSuffix(".db")
            }
        }.toSet()
    }

    private companion object {
        const val FILE_NAME = "account_scope_attribution.txt"
        const val NONE = "none"
    }
}
