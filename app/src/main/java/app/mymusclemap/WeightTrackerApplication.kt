package app.mymusclemap

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.mymusclemap.data.account.AccountDirectory
import app.mymusclemap.data.auth.SignInCoordinator
import app.mymusclemap.data.auth.StoredStrictSession
import app.mymusclemap.data.preferences.LanguagePreferences
import app.mymusclemap.domain.locale.AppLanguage
import app.mymusclemap.domain.locale.AppLanguageApplicator
import app.mymusclemap.domain.locale.AppLanguagePolicy
import app.mymusclemap.domain.locale.SystemLanguage
import kotlinx.coroutines.runBlocking
import app.mymusclemap.domain.account.AppAuthState
import app.mymusclemap.domain.account.resolveAppAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.system.exitProcess

class WeightTrackerApplication : Application() {
    var container: AppContainer? = null
        private set

    lateinit var accounts: AccountDirectory
        private set

    lateinit var signIn: SignInCoordinator
        private set

    lateinit var languages: LanguagePreferences
        private set

    var resolvedLanguage: AppLanguage = AppLanguage.EN
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        languages = LanguagePreferences(this)
        applyLanguagePolicy()
        accounts = AccountDirectory(this)
        signIn = SignInCoordinator(this)
        resumePendingLocalDeletion()
        if (startupDecision().state == AppAuthState.AUTHENTICATED) {
            openSignedInContainer()
        }
    }

    fun rememberLanguage(language: AppLanguage) {
        resolvedLanguage = language
    }

    fun applyLanguagePolicy() {
        val stored = runBlocking { languages.read() }
        val resolved = AppLanguagePolicy.resolve(SystemLanguage.primary(this), stored)
        resolvedLanguage = resolved
        AppLanguageApplicator.apply(resolved)
    }

    fun startupDecision() = resolveAppAuth(
        activeUserId = accounts.activeUserId(),
        sessionUserId = session()?.userId,
        sessionExpiresAt = session()?.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
        now = Instant.now(),
        online = online()
    )

    fun openSignedInContainer() {
        val userId = accounts.activeUserId() ?: return
        if (container != null) {
            return
        }
        val opened = AppContainer(this, userId)
        container = opened
        opened.activeWorkoutNotifications.start()
        applicationScope.launch {
            opened.refreshFounderProgramFromStore()
            opened.restoreFounderEntitlementCache()
            opened.migrateLegacyTrial()
            opened.refreshPromotionAvailability()
            opened.refreshBilling()
            opened.refreshFounderAuthority()
            opened.appBackupRepository.recoverInterruptedPhotoRestore()
            opened.achievementRepository.reconcile()
            opened.entitlementRevisions.drop(1).collect {
                opened.achievementRepository.reconcile()
            }
        }
    }

    fun restartProcess() {
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(launch)
        exitProcess(0)
    }

    suspend fun deleteCurrentAccount(): app.mymusclemap.data.account.AccountDeletionOutcome {
        val userId = accounts.activeUserId()
            ?: return app.mymusclemap.data.account.AccountDeletionOutcome.NoAccount
        val coordinator = app.mymusclemap.data.account.AccountDeletionCoordinator(
            requestIdToken = { signIn.googleIdentity.requestIdToken() },
            deleteAuthenticated = { signIn.deleteAuthenticated(it) },
            deletePublic = { signIn.deletePublic(it) },
            online = { online() },
            afterServerDeletion = { id -> finishLocalDeletion(id) }
        )
        return coordinator.delete(userId)
    }

    fun retryPendingLocalDeletion(): Boolean {
        val eraser = app.mymusclemap.data.account.AccountLocalDataEraser(this)
        val pending = eraser.pendingUserId() ?: return true
        val erased = eraser.erase(pending)
        if (erased) {
            eraser.clearPending()
        }
        return erased
    }

    fun pendingLocalDeletion(): Boolean {
        return app.mymusclemap.data.account.AccountLocalDataEraser(this).pendingUserId() != null
    }

    private fun resumePendingLocalDeletion() {
        val eraser = app.mymusclemap.data.account.AccountLocalDataEraser(this)
        val pending = eraser.pendingUserId() ?: return
        signIn.clearLocalSession()
        accounts.detach(pending)
        if (eraser.erase(pending)) {
            eraser.clearPending()
        }
    }

    private suspend fun finishLocalDeletion(userId: String): Boolean {
        val eraser = app.mymusclemap.data.account.AccountLocalDataEraser(this)
        if (!eraser.markPending(userId)) {
            return false
        }
        container?.founderRecognition?.forget(userId)
        container?.founderApprovalCelebrations?.forget(userId)
        signIn.clearLocalSession()
        accounts.detach(userId)
        return eraser.erase(userId)
    }

    private fun session(): StoredStrictSession? = signIn.auth.storedSession()

    private fun online(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
