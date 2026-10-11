package app.mymusclemap

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import app.mymusclemap.data.account.AccountDirectory
import app.mymusclemap.data.account.AccountLocalDataEraser
import app.mymusclemap.data.auth.SignInCoordinator
import app.mymusclemap.data.auth.StoredStrictSession
import app.mymusclemap.data.founder.FounderWorkoutOutbox
import app.mymusclemap.data.founder.FounderWorkoutSyncScheduler
import app.mymusclemap.data.local.AccountDatabaseMigrator
import app.mymusclemap.data.local.DatabaseOpenProblem
import app.mymusclemap.data.local.databaseOpenProblemFor
import app.mymusclemap.data.preferences.LanguagePreferences
import app.mymusclemap.domain.locale.AppLanguage
import app.mymusclemap.domain.locale.AppLanguageApplicator
import app.mymusclemap.domain.locale.AppLanguagePolicy
import app.mymusclemap.domain.locale.SystemLanguage
import kotlinx.coroutines.runBlocking
import app.mymusclemap.domain.account.AppAuthState
import app.mymusclemap.domain.account.accountDatabaseName
import app.mymusclemap.domain.account.resolveAppAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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

    val databaseOpenProblem = MutableStateFlow<DatabaseOpenProblem?>(null)
    val inactiveMigrationFailures = MutableStateFlow<List<String>>(emptyList())

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
        } else {
            inactiveMigrationFailures.value = AccountDatabaseMigrator(this).migrateAll().map { it.fileName }
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
        val fileName = accountDatabaseName(userId)
        val failures = AccountDatabaseMigrator(this).migrateAll()
        inactiveMigrationFailures.value = failures.filter { it.fileName != fileName }.map { it.fileName }
        if (failures.any { it.fileName == fileName }) {
            databaseOpenProblem.value = DatabaseOpenProblem.MigrationFailed
            return
        }
        val opened = try {
            AppContainer(this, userId)
        } catch (error: Throwable) {
            val problem = databaseOpenProblemFor(error)
            if (problem == null) {
                throw error
            }
            databaseOpenProblem.value = problem
            return
        }
        databaseOpenProblem.value = null
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

    fun retryDatabaseOpen() {
        databaseOpenProblem.value = null
        openSignedInContainer()
        if (container != null) {
            restartProcess()
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
        val eraser = AccountLocalDataEraser(this)
        val pending = eraser.pendingUserId() ?: return true
        dropFounderQueue(pending)
        val erased = eraser.erase(pending)
        if (erased) {
            eraser.clearPending()
        }
        return erased
    }

    fun pendingLocalDeletion(): Boolean {
        return AccountLocalDataEraser(this).pendingUserId() != null
    }

    private fun resumePendingLocalDeletion() {
        val eraser = AccountLocalDataEraser(this)
        val pending = eraser.pendingUserId() ?: return
        signIn.clearLocalSession()
        accounts.detach(pending)
        dropFounderQueue(pending)
        if (eraser.erase(pending)) {
            eraser.clearPending()
        }
    }

    private suspend fun finishLocalDeletion(userId: String): Boolean {
        val eraser = AccountLocalDataEraser(this)
        if (!eraser.markPending(userId)) {
            return false
        }
        container?.founderRecognition?.forget(userId)
        container?.founderApprovalCelebrations?.forget(userId)
        signIn.clearLocalSession()
        accounts.detach(userId)
        dropFounderQueue(userId)
        return eraser.erase(userId)
    }

    private fun dropFounderQueue(userId: String) {
        runBlocking { FounderWorkoutOutbox(this@WeightTrackerApplication).removeForUser(userId) }
        FounderWorkoutSyncScheduler.cancel(this)
    }

    private fun session(): StoredStrictSession? = signIn.auth.storedSession()

    private fun online(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
