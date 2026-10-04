package app.mymusclemap

import android.content.Context
import app.mymusclemap.BuildConfig
import app.mymusclemap.data.auth.ActivityBoundGoogleIdentityProvider
import app.mymusclemap.data.auth.OkHttpStrictBackendApi
import app.mymusclemap.data.auth.StrictAuthRepository
import app.mymusclemap.data.auth.StrictSessionStore
import app.mymusclemap.data.auth.EncryptedFileStrictSessionStore
import app.mymusclemap.data.auth.strictOkHttpClient
import app.mymusclemap.data.health.HealthConnectGateway
import app.mymusclemap.data.health.HealthRepository
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.auth.FounderEntitlementCall
import app.mymusclemap.data.auth.FounderSnapshotCall
import app.mymusclemap.data.founder.BackendFounderSnapshot
import app.mymusclemap.data.founder.FounderJoinCoordinator
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.founder.FounderWorkoutFlush
import app.mymusclemap.data.founder.FounderWorkoutOutbox
import app.mymusclemap.data.founder.FounderWorkoutSync
import app.mymusclemap.data.founder.FounderWorkoutSyncScheduler
import app.mymusclemap.data.founder.NativeFounderWorkout
import app.mymusclemap.data.preferences.FounderEntitlementCache
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.data.preferences.LockScreenSetCompletionPreferences
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.BodyMeasurementRepository
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.data.repository.ProgressPhotoRepository
import java.io.File
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WeeklyGoalRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.data.workoutimport.ContentWorkoutImportFileReader
import app.mymusclemap.ui.widget.HeatmapWidgetUpdater
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.SystemDateProvider
import app.mymusclemap.domain.entitlement.EntitlementComposer
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.InactiveFounderLifetimeProvider
import app.mymusclemap.domain.entitlement.InactiveSubscriptionProvider
import app.mymusclemap.domain.entitlement.PolicyBackedEntitlements
import app.mymusclemap.ui.workout.ActiveWorkoutNotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val clock: Clock = Clock.systemDefaultZone()
    val dateProvider: DateProvider = SystemDateProvider(clock)
    private val database = WeightDatabase.create(appContext)
    val weightRepository = WeightRepository(
        dao = database.weightMeasurementDao(),
        clock = clock
    )
    val bodyMeasurementRepository = BodyMeasurementRepository(
        dao = database.bodyMeasurementDao(),
        clock = clock
    )
    private val customExerciseCreate = AtomicReference<(Int) -> Boolean> { true }
    private val nativeWorkoutCompleted = AtomicReference<suspend (String) -> Unit> { _ -> }
    val exerciseRepository = ExerciseRepository(
        dao = database.exerciseDao(),
        clock = clock,
        templateDao = database.workoutTemplateDao(),
        sessionDao = database.workoutSessionDao(),
        allowCustomCreate = { count -> customExerciseCreate.get().invoke(count) }
    )
    val workoutTemplateRepository = WorkoutTemplateRepository(
        templateDao = database.workoutTemplateDao(),
        exerciseDao = database.exerciseDao(),
        clock = clock,
        sessionDao = database.workoutSessionDao(),
        scheduledWorkoutDao = database.scheduledWorkoutDao()
    )
    val scheduledWorkoutRepository = ScheduledWorkoutRepository(
        scheduledWorkoutDao = database.scheduledWorkoutDao(),
        templateDao = database.workoutTemplateDao(),
        sessionDao = database.workoutSessionDao(),
        clock = clock
    )
    val workoutSessionRepository = WorkoutSessionRepository(
        sessionDao = database.workoutSessionDao(),
        templateDao = database.workoutTemplateDao(),
        exerciseDao = database.exerciseDao(),
        weightRepository = weightRepository,
        clock = clock,
        dateProvider = dateProvider,
        onHeatmapDataChanged = { HeatmapWidgetUpdater.update(appContext) },
        onNativeWorkoutCompleted = { clientWorkoutId -> nativeWorkoutCompleted.get().invoke(clientWorkoutId) }
    )
    val lockScreenSetCompletion = LockScreenSetCompletionPreferences(appContext)
    val activeWorkoutNotifications = ActiveWorkoutNotificationCoordinator(
        context = appContext,
        repository = workoutSessionRepository,
        preferences = lockScreenSetCompletion,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )
    val weeklyGoalRepository = WeeklyGoalRepository(
        dao = database.weeklyWorkoutGoalDao(),
        clock = clock,
        earliestCompletedDate = {
            database.workoutSessionDao().earliestCompletedWorkoutDate()?.let(LocalDate::parse)
        }
    )
    val themePreferences = ThemePreferences(appContext)
    val progressPhotoStore = ProgressPhotoStore(
        File(appContext.filesDir, ProgressPhotoStore.DIRECTORY_NAME)
    )
    val appBackupRepository = AppBackupRepository(
        database,
        themePreferences,
        progressPhotoStore = progressPhotoStore,
        onHeatmapDataChanged = { HeatmapWidgetUpdater.update(appContext) }
    )
    val firstRunCoordinator = FirstRunCoordinator(database, exerciseRepository, themePreferences)
    val founderProgramRules: FounderProgramRules = FounderProgramRuleSelection.rules
    val founderProgramAvailability = FounderProgramAvailabilitySelection.availability
    val founderProgramStore = FounderProgramStore(appContext)
    val founderMilestoneAcknowledgements = FounderMilestoneAcknowledgementStore(appContext)
    private val entitlementRevision = MutableStateFlow(0)
    private val founderCacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val strictSessionRevision = MutableStateFlow(0)
    val founderEntitlementCache = FounderEntitlementCache(
        context = appContext,
        scope = founderCacheScope,
        onChanged = { entitlementRevision.value = entitlementRevision.value + 1 }
    )
    val subscriptionProvider = InactiveSubscriptionProvider
    val founderLifetimeProvider = InactiveFounderLifetimeProvider
    val founderProgram = FounderProgramCoordinator(
        store = founderProgramStore,
        sessions = database.workoutSessionDao(),
        dateProvider = dateProvider,
        rules = founderProgramRules,
        onEntitlementChanged = { entitlementRevision.value = entitlementRevision.value + 1 }
    )
    val entitlementComposer = EntitlementComposer(
        subscriptionProvider = subscriptionProvider,
        founderLifetimeProvider = founderLifetimeProvider,
        clock = clock,
        founderProgram = founderProgram::currentState,
        backendFounder = founderEntitlementCache::current
    )
    val featureEntitlements: FeatureEntitlements = PolicyBackedEntitlements(
        policySource = { entitlementComposer.policy() },
        revisions = entitlementRevision
    )

    init {
        customExerciseCreate.set { count ->
            entitlementComposer.policy().customExercises(count).canCreate
        }
        nativeWorkoutCompleted.set { clientWorkoutId ->
            val state = founderProgram.currentState()
            if (FounderWorkoutSync.accepts(state.status, state.backendOwned)) {
                founderWorkoutSync.onNativeWorkoutCompleted(clientWorkoutId)
            } else {
                founderProgram.onNativeWorkoutCompleted(clientWorkoutId)
            }
        }
    }

    suspend fun refreshFounderProgramFromStore() {
        founderProgram.refresh()
    }

    suspend fun restoreFounderEntitlementCache() {
        founderEntitlementCache.load()
    }

    suspend fun refreshFounderAuthority() {
        if (!founderProgram.currentState().backendOwned) {
            return
        }
        when (val current = strictAccount.api.currentFounder(founderZone)) {
            is FounderSnapshotCall.Loaded -> founderProgram.applyBackendEnrollment(current.snapshot)
            FounderSnapshotCall.Unauthenticated -> founderEntitlementCache.drop()
            else -> Unit
        }
        when (val entitlements = strictAccount.api.currentEntitlements()) {
            is FounderEntitlementCall.Loaded -> founderEntitlementCache.save(
                temporaryFounderPro = entitlements.temporaryFounderPro,
                founderLifetime = entitlements.founderLifetime,
                validUntil = clock.instant().plus(FounderEntitlementCache.TRUST)
            )
            FounderEntitlementCall.Unauthenticated -> founderEntitlementCache.drop()
            else -> Unit
        }
        if (founderWorkoutOutbox.pending().isNotEmpty()) {
            FounderWorkoutSyncScheduler.enqueue(appContext)
        }
    }

    suspend fun flushFounderWorkoutOutbox(): FounderWorkoutFlush {
        return founderWorkoutSync.flush()
    }
    val progressPhotoRepository = ProgressPhotoRepository(
        dao = database.progressPhotoDao(),
        store = progressPhotoStore,
        clock = clock,
        dateProvider = dateProvider
    )
    val healthRepository = HealthRepository(
        source = HealthConnectGateway(appContext),
        dateProvider = dateProvider,
        canImportExternalWorkouts = {
            entitlementComposer.policy().healthConnect().canImportExternalWorkouts
        }
    )
    private val strictAccount by lazy {
        val sessions = EncryptedFileStrictSessionStore.create(appContext)
        sessions.onCleared = {
            founderEntitlementCache.drop()
            strictSessionRevision.value = strictSessionRevision.value + 1
        }
        sessions.onWritten = {
            strictSessionRevision.value = strictSessionRevision.value + 1
        }
        val google = ActivityBoundGoogleIdentityProvider(
            serverClientId = BuildConfig.STRICT_GOOGLE_SERVER_CLIENT_ID
        )
        val api = OkHttpStrictBackendApi(
            baseUrl = BuildConfig.STRICT_API_BASE_URL,
            http = strictOkHttpClient(),
            sessions = sessions
        )
        StrictAccount(
            sessions = sessions,
            google = google,
            api = api,
            repository = StrictAuthRepository(
                google = google,
                api = api,
                sessions = sessions
            )
        )
    }

    private val founderZone = ZoneId.systemDefault()
    private val founderWorkoutOutbox = FounderWorkoutOutbox(appContext)
    private val founderWorkoutSync by lazy {
        FounderWorkoutSync(
            outbox = founderWorkoutOutbox,
            accepting = {
                val state = founderProgram.currentState()
                FounderWorkoutSync.accepts(state.status, state.backendOwned)
            },
            lookup = { clientWorkoutId ->
                val session = database.workoutSessionDao().getByClientWorkoutId(clientWorkoutId)
                val finishedAt = session?.finishedAt
                if (session == null || finishedAt == null) {
                    null
                } else {
                    NativeFounderWorkout(
                        clientWorkoutId = session.clientWorkoutId,
                        completedAtEpochMilli = finishedAt,
                        localDate = session.workoutDate,
                        imported = !session.importFingerprint.isNullOrBlank()
                    )
                }
            },
            submit = { event ->
                strictAccount.api.submitFounderWorkout(
                    clientWorkoutId = event.clientWorkoutId,
                    completedAt = Instant.ofEpochMilli(event.completedAtEpochMilli),
                    localDate = LocalDate.parse(event.localDate),
                    zone = founderZone
                )
            },
            onAccepted = { snapshot -> publishFounderSnapshot(snapshot) },
            onUnauthenticated = { founderEntitlementCache.drop() },
            schedule = { FounderWorkoutSyncScheduler.enqueue(appContext) }
        )
    }

    private suspend fun publishFounderSnapshot(snapshot: BackendFounderSnapshot) {
        founderProgram.applyBackendEnrollment(snapshot)
        when (val entitlements = strictAccount.api.currentEntitlements()) {
            is FounderEntitlementCall.Loaded -> founderEntitlementCache.save(
                temporaryFounderPro = entitlements.temporaryFounderPro,
                founderLifetime = entitlements.founderLifetime,
                validUntil = clock.instant().plus(FounderEntitlementCache.TRUST)
            )
            FounderEntitlementCall.Unauthenticated -> founderEntitlementCache.drop()
            else -> {
                val trusted = founderEntitlementCache.current().trusted(clock.instant())
                founderEntitlementCache.save(
                    temporaryFounderPro = snapshot.temporaryProActive,
                    founderLifetime = trusted.founderLifetime,
                    validUntil = clock.instant().plus(FounderEntitlementCache.TRUST)
                )
            }
        }
    }

    val strictSessionStore: StrictSessionStore
        get() = strictAccount.sessions
    val strictAuthRepository: StrictAuthRepository
        get() = strictAccount.repository

    fun bindStrictSignIn(uiContext: Context) {
        strictAccount.google.bind(uiContext)
    }

    fun unbindStrictSignIn(uiContext: Context) {
        strictAccount.google.unbind(uiContext)
    }

    private val founderJoin by lazy {
        FounderJoinCoordinator(
            auth = strictAuthRepository,
            api = strictAccount.api,
            applyEnrollment = { snapshot ->
                publishFounderSnapshot(snapshot)
                if (founderWorkoutOutbox.pending().isNotEmpty()) {
                    if (founderWorkoutSync.flush() == FounderWorkoutFlush.Retry) {
                        FounderWorkoutSyncScheduler.enqueue(appContext)
                    }
                }
            },
            zone = founderZone
        )
    }

    val onboardingRepository = OnboardingRepository(
        themePreferences = themePreferences,
        sessionRepository = workoutSessionRepository,
        weightRepository = weightRepository,
        templateRepository = workoutTemplateRepository
    )
    val viewModelFactory = WeightViewModelFactory(
        weightRepository = weightRepository,
        bodyMeasurementRepository = bodyMeasurementRepository,
        exerciseRepository = exerciseRepository,
        workoutTemplateRepository = workoutTemplateRepository,
        workoutSessionRepository = workoutSessionRepository,
        scheduledWorkoutRepository = scheduledWorkoutRepository,
        dateProvider = dateProvider,
        themePreferences = themePreferences,
        workoutImportFileReader = ContentWorkoutImportFileReader(appContext),
        appBackupRepository = appBackupRepository,
        firstRunCoordinator = firstRunCoordinator,
        onboardingRepository = onboardingRepository,
        progressPhotoRepository = progressPhotoRepository,
        healthRepository = healthRepository,
        featureEntitlements = featureEntitlements,
        weeklyGoalRepository = weeklyGoalRepository,
        founderProgram = founderProgram,
        founderRules = founderProgramRules,
        founderMilestoneAcknowledgements = founderMilestoneAcknowledgements,
        founderAvailability = founderProgramAvailability,
        lockScreenSetCompletion = lockScreenSetCompletion,
        founderJoin = { founderJoin.join() },
        founderSessionRevision = strictSessionRevision,
        founderSessionPresent = { strictSessionStore.read() != null }
    )
}

private class StrictAccount(
    val sessions: StrictSessionStore,
    val google: ActivityBoundGoogleIdentityProvider,
    val api: OkHttpStrictBackendApi,
    val repository: StrictAuthRepository
)
