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
import app.mymusclemap.data.auth.FounderReportSubmission
import app.mymusclemap.data.auth.FounderSnapshotCall
import app.mymusclemap.data.founder.BackendFounderSnapshot
import app.mymusclemap.data.founder.FounderJoinCoordinator
import app.mymusclemap.data.founder.FounderProgramCoordinator
import app.mymusclemap.data.founder.FounderWorkoutFlush
import app.mymusclemap.data.founder.FounderWorkoutOutbox
import app.mymusclemap.data.founder.FounderWorkoutSync
import app.mymusclemap.data.founder.FounderWorkoutSyncScheduler
import app.mymusclemap.data.founder.FounderSetObservation
import app.mymusclemap.data.founder.FounderWorkoutObservations
import app.mymusclemap.data.founder.NativeFounderWorkout
import app.mymusclemap.data.preferences.FounderApprovalCelebrationStore
import app.mymusclemap.data.preferences.ProDiscoveryStore
import app.mymusclemap.data.preferences.ProDiscoveryStoreKind
import app.mymusclemap.data.preferences.PromotionAvailabilityStore
import app.mymusclemap.data.promotion.ProDiscoveryCoordinator
import app.mymusclemap.data.promotion.PromotionAvailabilityClient
import app.mymusclemap.domain.entitlement.EntitlementResolver
import app.mymusclemap.domain.entitlement.ProDiscoveryCoverage
import app.mymusclemap.domain.entitlement.ProDiscoveryPolicy
import app.mymusclemap.domain.entitlement.ProDiscoverySnapshot
import app.mymusclemap.domain.entitlement.PromotionalProEntitlement
import app.mymusclemap.data.preferences.FounderEntitlementCache
import app.mymusclemap.data.preferences.FounderMilestoneAcknowledgementStore
import app.mymusclemap.data.preferences.FounderRecognitionStore
import app.mymusclemap.data.preferences.FounderProgramStore
import app.mymusclemap.data.preferences.LockScreenSetCompletionPreferences
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.achievements.AccountAchievementAuthority
import app.mymusclemap.data.repository.AchievementRepository
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.BodyMeasurementRepository
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.progress.ProgressPhotoStore
import app.mymusclemap.data.repository.OnboardingRepository
import app.mymusclemap.data.repository.ProgressPhotoRepository
import java.io.File
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.TargetWeightGoalRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WeeklyGoalRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.data.workoutimport.ContentWorkoutImportFileReader
import app.mymusclemap.ui.widget.HeatmapWidgetUpdater
import app.mymusclemap.domain.DateProvider
import app.mymusclemap.domain.SystemDateProvider
import app.mymusclemap.domain.entitlement.EntitlementComposer
import app.mymusclemap.domain.entitlement.FounderApprovalCelebration
import app.mymusclemap.domain.entitlement.FeatureEntitlements
import app.mymusclemap.domain.entitlement.FounderProgramRules
import app.mymusclemap.domain.entitlement.InactiveFounderLifetimeProvider
import app.mymusclemap.domain.entitlement.InactiveSubscriptionProvider
import app.mymusclemap.domain.entitlement.PolicyBackedEntitlements
import app.mymusclemap.domain.entitlement.ProBenefitsStatus
import app.mymusclemap.ui.workout.ActiveWorkoutNotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        clock = clock,
        onWeightChanged = { weightChanged.get().invoke() }
    )
    val bodyMeasurementRepository = BodyMeasurementRepository(
        dao = database.bodyMeasurementDao(),
        clock = clock
    )
    private val customExerciseCreate = AtomicReference<(Int) -> Boolean> { true }
    private val nativeWorkoutCompleted = AtomicReference<suspend (String) -> Unit> { _ -> }
    private val weightChanged = AtomicReference<suspend () -> Unit> { }
    private val plansChanged = AtomicReference<suspend () -> Unit> { }
    private val grantsPro = AtomicReference<() -> Boolean> { false }
    private val founderLifetime = AtomicReference<() -> Boolean> { false }
    private val accountAuthorityState = AtomicReference(AccountAchievementAuthority())
    private val authenticatedUserId = AtomicReference<String?>(null)
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
        scheduledWorkoutDao = database.scheduledWorkoutDao(),
        onPlansChanged = { plansChanged.get().invoke() }
    )
    val scheduledWorkoutRepository = ScheduledWorkoutRepository(
        scheduledWorkoutDao = database.scheduledWorkoutDao(),
        templateDao = database.workoutTemplateDao(),
        sessionDao = database.workoutSessionDao(),
        clock = clock
    )
    val targetWeightGoalRepository = TargetWeightGoalRepository(
        dao = database.targetWeightGoalDao(),
        clock = clock,
        onChanged = { achievementRepository.reconcile() }
    )
    val achievementRepository = AchievementRepository(
        database = database,
        clock = clock,
        dateProvider = dateProvider,
        grantsPro = { grantsPro.get().invoke() },
        founderLifetime = { founderLifetime.get().invoke() },
        accountAuthority = { accountAuthorityState.get() }
    )
    val workoutSessionRepository = WorkoutSessionRepository(
        sessionDao = database.workoutSessionDao(),
        templateDao = database.workoutTemplateDao(),
        exerciseDao = database.exerciseDao(),
        weightRepository = weightRepository,
        clock = clock,
        dateProvider = dateProvider,
        onHeatmapDataChanged = { HeatmapWidgetUpdater.update(appContext) },
        onNativeWorkoutCompleted = { clientWorkoutId -> nativeWorkoutCompleted.get().invoke(clientWorkoutId) },
        onTrainingHistoryChanged = { clientWorkoutId -> achievementRepository.reconcile(clientWorkoutId) }
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
        onGoalChanged = { achievementRepository.reconcile() },
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
        onHeatmapDataChanged = { HeatmapWidgetUpdater.update(appContext) },
        onAfterRestore = { achievementRepository.reconcile() }
    )
    val firstRunCoordinator = FirstRunCoordinator(database, exerciseRepository, themePreferences)
    val founderProgramRules: FounderProgramRules = FounderProgramRuleSelection.rules
    val founderProgramAvailability = FounderProgramAvailabilitySelection.availability
    val founderProgramStore = FounderProgramStore(appContext)
    val founderMilestoneAcknowledgements = FounderMilestoneAcknowledgementStore(appContext)
    private val entitlementRevision = MutableStateFlow(0)
    val entitlementRevisions: kotlinx.coroutines.flow.StateFlow<Int> get() = entitlementRevision
    private val founderAuthorityRefresh = Mutex()
    private val founderCacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val strictSessionRevision = MutableStateFlow(0)
    val founderEntitlementCache = FounderEntitlementCache(
        context = appContext,
        scope = founderCacheScope,
        sessionUserId = { authenticatedUserId.get() },
        onChanged = {
            refreshAccountAuthority()
            entitlementRevision.value = entitlementRevision.value + 1
        }
    )
    val founderRecognition = FounderRecognitionStore(
        context = appContext,
        sessionUserId = { authenticatedUserId.get() },
        onChanged = {
            refreshAccountAuthority()
            entitlementRevision.value = entitlementRevision.value + 1
        }
    )
    val founderApprovalCelebrations = FounderApprovalCelebrationStore(
        context = appContext,
        onChanged = {
            entitlementRevision.value = entitlementRevision.value + 1
        }
    )
    private val proDiscoveryReal = ProDiscoveryStore(
        context = appContext,
        kind = ProDiscoveryStoreKind.REAL,
        onChanged = { entitlementRevision.value = entitlementRevision.value + 1 }
    )
    private val proDiscoverySimulation = ProDiscoveryStore(
        context = appContext,
        kind = ProDiscoveryStoreKind.SIMULATION,
        onChanged = { entitlementRevision.value = entitlementRevision.value + 1 }
    )
    private val promotionAvailability = PromotionAvailabilityStore(
        context = appContext,
        onChanged = { entitlementRevision.value = entitlementRevision.value + 1 }
    )
    private val promotionAvailabilityClient = PromotionAvailabilityClient(
        baseUrl = BuildConfig.STRICT_API_BASE_URL,
        http = strictOkHttpClient()
    )
    val proDiscovery = ProDiscoveryCoordinator(
        real = proDiscoveryReal,
        simulation = proDiscoverySimulation,
        now = { clock.instant() },
        zone = clock.zone,
        completions = {
            database.workoutSessionDao().nativeCompletionEpochMillis().map { Instant.ofEpochMilli(it) }
        },
        debugConfig = { ProDiscoveryDebugSelection.current() },
        promotionsEnabled = { promotionAvailability.promotionsEnabled() },
        onAvailability = { enabled -> promotionAvailability.setPromotionsEnabled(enabled) },
        onChanged = { entitlementRevision.value = entitlementRevision.value + 1 }
    )
    val subscriptionProvider = InactiveSubscriptionProvider
    val founderLifetimeProvider = InactiveFounderLifetimeProvider
    val founderProgram = FounderProgramCoordinator(
        store = founderProgramStore,
        sessions = database.workoutSessionDao(),
        dateProvider = dateProvider,
        rules = founderProgramRules,
        onEntitlementChanged = {
            refreshAccountAuthority()
            entitlementRevision.value = entitlementRevision.value + 1
        }
    )
    val entitlementComposer = EntitlementComposer(
        subscriptionProvider = subscriptionProvider,
        founderLifetimeProvider = founderLifetimeProvider,
        clock = clock,
        founderProgram = founderProgram::currentState,
        backendFounder = founderEntitlementCache::current,
        promotionalPro = { proDiscovery.currentGrant() },
        adjustSources = EntitlementOverrideSelection::adjust
    )
    init {
        proDiscovery.coverage = {
            val withoutTrial = EntitlementOverrideSelection.adjust(
                entitlementComposer.sources().copy(promotionalPro = PromotionalProEntitlement())
            )
            ProDiscoveryCoverage(
                currentlyFree = !EntitlementResolver.resolve(withoutTrial, clock.instant()).grantsPro,
                continuedBeyond = { expiresAt ->
                    ProDiscoveryPolicy.continuedProBeyond(withoutTrial, expiresAt)
                }
            )
        }
    }
    val featureEntitlements: FeatureEntitlements = PolicyBackedEntitlements(
        policySource = { entitlementComposer.policy() },
        revisions = entitlementRevision
    )

    init {
        customExerciseCreate.set { count ->
            entitlementComposer.policy().customExercises(count).canCreate
        }
        grantsPro.set { entitlementComposer.resolve().grantsPro }
        founderLifetime.set { entitlementComposer.resolve().founderRecognized }
        refreshAccountAuthority()
        weightChanged.set { achievementRepository.reconcile() }
        plansChanged.set { achievementRepository.reconcile() }
        nativeWorkoutCompleted.set { clientWorkoutId ->
            val state = founderProgram.currentState()
            if (FounderWorkoutSync.accepts(state.status, state.backendOwned)) {
                founderWorkoutSync.onNativeWorkoutCompleted(clientWorkoutId)
            } else {
                founderProgram.onNativeWorkoutCompleted(clientWorkoutId)
            }
            founderCacheScope.launch { proDiscovery.refreshHistory() }
        }
    }

    suspend fun refreshFounderProgramFromStore() {
        founderProgram.refresh()
    }

    suspend fun restoreFounderEntitlementCache() {
        authenticatedUserId.set(strictAccount.sessions.read()?.userId)
        founderEntitlementCache.load()
        founderRecognition.load()
        founderApprovalCelebrations.load()
        proDiscoveryReal.load()
        proDiscoverySimulation.load()
        promotionAvailability.load()
        proDiscovery.prepare()
        proDiscovery.refreshHistory()
    }

    fun promotionNow(): Instant = clock.instant()

    fun proDiscoverySnapshot(): ProDiscoverySnapshot = proDiscovery.snapshot()

    fun activateProDiscovery() {
        founderCacheScope.launch { proDiscovery.activate() }
    }

    fun dismissProDiscoveryOffer() {
        founderCacheScope.launch { proDiscovery.dismissOffer() }
    }

    fun dismissProDiscoveryWarning() {
        founderCacheScope.launch { proDiscovery.dismissWarning() }
    }

    fun notePromotionClock() {
        entitlementRevision.value = entitlementRevision.value + 1
    }

    suspend fun refreshPromotionAvailability() {
        proDiscovery.refreshAvailability { promotionAvailabilityClient.promotionsEnabled() }
    }

    /**
     * The celebration for the signed-in account, or null. Reads the backend program
     * snapshot and the unadjusted entitlement cache. A debug Founder override is ignored.
     */
    fun pendingFounderApprovalCelebration(): FounderApprovalCelebration? {
        if (!founderApprovalCelebrations.isLoaded()) {
            return null
        }
        val userId = authenticatedUserId.get()
        val program = founderProgram.currentState()
        val trusted = founderEntitlementCache.current().trusted(clock.instant())
        return app.mymusclemap.domain.entitlement.pendingFounderApprovalCelebration(
            userId = userId,
            backendOwned = program.backendOwned,
            status = program.status,
            founderLifetime = trusted.founderLifetime,
            founderProExpiresAt = trusted.founderProExpiresAt,
            now = clock.instant(),
            acknowledged = userId != null && founderApprovalCelebrations.isAcknowledged(userId)
        )
    }

    /**
     * Access copy for the Pro Benefits screen. An expiration is included only when the
     * trusted cache still has a Founder Pro instant. The screen does not calculate a date.
     */
    fun proBenefitsStatus(): ProBenefitsStatus {
        val now = clock.instant()
        val resolved = entitlementComposer.resolve()
        val trusted = founderEntitlementCache.current().trusted(now)
        return app.mymusclemap.domain.entitlement.proBenefitsStatus(
            grantsPro = resolved.grantsPro,
            founderProActive = resolved.founderProActive && trusted.founderProActive(now),
            founderLifetime = trusted.founderLifetime,
            founderProExpiresAt = trusted.founderProExpiresAt
        )
    }

    /** Records that this account has seen the celebration. Does not change Pro or recognition. */
    fun acknowledgeFounderApprovalCelebration() {
        val userId = authenticatedUserId.get() ?: return
        founderCacheScope.launch {
            founderApprovalCelebrations.acknowledge(userId)
        }
    }

    suspend fun refreshFounderAuthority() {
        if (!founderProgram.currentState().backendOwned) {
            return
        }
        founderAuthorityRefresh.withLock {
            reloadFounderAuthority()
        }
    }

    private suspend fun reloadFounderAuthority() {
        when (val current = strictAccount.api.currentFounder(founderZone)) {
            is FounderSnapshotCall.Loaded -> founderProgram.applyBackendEnrollment(current.snapshot)
            FounderSnapshotCall.Unauthenticated -> founderEntitlementCache.drop()
            else -> Unit
        }
        when (val entitlements = strictAccount.api.currentEntitlements()) {
            is FounderEntitlementCall.Loaded -> saveLoadedEntitlements(entitlements)
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
            authenticatedUserId.set(null)
            founderEntitlementCache.drop()
            strictSessionRevision.value = strictSessionRevision.value + 1
        }
        sessions.onWritten = {
            val userId = sessions.read()?.userId
            authenticatedUserId.set(userId)
            founderEntitlementCache.retainAccount(userId)
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
                    val exercises = database.workoutSessionDao().getExercises(session.id)
                    val sets = database.workoutSessionDao().getSetsForSession(session.id)
                        .map { set -> FounderSetObservation(set.status, set.actualLoadKind) }
                    NativeFounderWorkout(
                        clientWorkoutId = session.clientWorkoutId,
                        completedAtEpochMilli = finishedAt,
                        localDate = session.workoutDate,
                        imported = !session.importFingerprint.isNullOrBlank(),
                        observation = FounderWorkoutObservations.derive(
                            templateName = session.templateName,
                            templateId = session.templateId,
                            startedAt = session.startedAt,
                            finishedAt = finishedAt,
                            exerciseCount = exercises.size,
                            sets = sets
                        )
                    )
                }
            },
            submit = { event ->
                strictAccount.api.submitFounderWorkout(
                    clientWorkoutId = event.clientWorkoutId,
                    completedAt = Instant.ofEpochMilli(event.completedAtEpochMilli),
                    localDate = LocalDate.parse(event.localDate),
                    zone = founderZone,
                    observation = event.observation
                )
            },
            onAccepted = { snapshot -> publishFounderSnapshot(snapshot) },
            onUnauthenticated = { founderEntitlementCache.drop() },
            schedule = { FounderWorkoutSyncScheduler.enqueue(appContext) }
        )
    }

    private suspend fun submitFounderTesterReport(
        submissionId: String,
        feedback: String,
        appVersion: String
    ): FounderReportSubmission {
        val result = strictAccount.api.submitFounderReport(
            submissionId = submissionId,
            feedback = feedback,
            appVersion = appVersion,
            zone = founderZone
        )
        if (result is FounderReportSubmission.Accepted) {
            publishFounderSnapshot(result.snapshot)
        }
        return result
    }

    private suspend fun publishFounderSnapshot(snapshot: BackendFounderSnapshot) {
        founderProgram.applyBackendEnrollment(snapshot)
        when (val entitlements = strictAccount.api.currentEntitlements()) {
            is FounderEntitlementCall.Loaded -> saveLoadedEntitlements(entitlements)
            FounderEntitlementCall.Unauthenticated -> founderEntitlementCache.drop()
            else -> {
                val trusted = founderEntitlementCache.current().trusted(clock.instant())
                val userId = authenticatedUserId.get() ?: return
                founderEntitlementCache.save(
                    temporaryFounderPro = snapshot.temporaryProActive,
                    founderLifetime = trusted.founderLifetime,
                    validUntil = clock.instant().plus(FounderEntitlementCache.TRUST),
                    userId = userId,
                    founderGrantedAt = trusted.founderGrantedAt,
                    specialGrants = trusted.specialGrants,
                    founderRecognized = trusted.founderRecognized,
                    founderProExpiresAt = trusted.founderProExpiresAt
                )
            }
        }
    }

    private suspend fun saveLoadedEntitlements(entitlements: FounderEntitlementCall.Loaded) {
        val userId = authenticatedUserId.get() ?: strictAccount.sessions.read()?.userId ?: return
        authenticatedUserId.set(userId)
        if (entitlements.founderRecognized || entitlements.founderLifetime) {
            founderRecognition.confirm(userId, entitlements.founderGrantedAt)
        }
        founderEntitlementCache.save(
            temporaryFounderPro = entitlements.temporaryFounderPro,
            founderLifetime = entitlements.founderLifetime,
            validUntil = clock.instant().plus(FounderEntitlementCache.TRUST),
            userId = userId,
            founderGrantedAt = entitlements.founderGrantedAt,
            specialGrants = entitlements.specialAchievements,
            founderRecognized = entitlements.founderRecognized,
            founderProExpiresAt = entitlements.founderProExpiresAt
        )
    }

    private fun refreshAccountAuthority() {
        val resolved = entitlementComposer.resolve()
        val trusted = founderEntitlementCache.current().trusted(clock.instant())
        val recognition = founderRecognition.current()
        val founderRecognized = recognition.recognized || resolved.founderRecognized
        accountAuthorityState.set(
            AccountAchievementAuthority(
                founderRecognized = founderRecognized,
                founderGrantedAtMillis = when {
                    recognition.recognized -> recognition.grantedAt?.toEpochMilli()
                        ?: trusted.founderGrantedAt?.toEpochMilli()
                    resolved.founderRecognized -> trusted.founderGrantedAt?.toEpochMilli()
                    else -> null
                },
                earlyAdopterGrantedAtMillis = trusted.specialGrantedAtMillis("EARLY_ADOPTER"),
                developerGrantedAtMillis = trusted.specialGrantedAtMillis("DEVELOPER")
            )
        )
        achievementRepository.notifyEntitlementChanged()
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
        founderSessionPresent = { strictSessionStore.read() != null },
        founderSubmitReport = { submissionId, feedback, appVersion ->
            submitFounderTesterReport(submissionId, feedback, appVersion)
        },
        founderRefreshAuthority = { refreshFounderAuthority() },
        achievementRepository = achievementRepository,
        targetWeightGoalRepository = targetWeightGoalRepository
    )
}

private class StrictAccount(
    val sessions: StrictSessionStore,
    val google: ActivityBoundGoogleIdentityProvider,
    val api: OkHttpStrictBackendApi,
    val repository: StrictAuthRepository
)
