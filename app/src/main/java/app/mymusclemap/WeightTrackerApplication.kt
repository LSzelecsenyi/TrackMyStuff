package app.mymusclemap

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WeightTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.activeWorkoutNotifications.start()
        applicationScope.launch {
            container.refreshFounderProgramFromStore()
            container.restoreFounderEntitlementCache()
            container.refreshFounderAuthority()
            container.appBackupRepository.recoverInterruptedPhotoRestore()
            container.achievementRepository.reconcile()
        }
    }
}
