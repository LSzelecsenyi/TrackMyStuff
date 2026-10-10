package app.mymusclemap.ui.onboarding

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.findViewTreeNavigationEventDispatcherOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainActivity
import app.mymusclemap.WeightViewModelFactory
import app.mymusclemap.data.account.AccountDirectory
import app.mymusclemap.data.account.VerifiedAccountProfile
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.local.WorkoutTemplateEntity
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.ExerciseRepository
import app.mymusclemap.data.repository.FirstRunCoordinator
import app.mymusclemap.data.repository.FirstRunDecision
import app.mymusclemap.data.repository.ScheduledWorkoutRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.data.repository.WorkoutSessionRepository
import app.mymusclemap.data.repository.WorkoutTemplateRepository
import app.mymusclemap.data.workoutimport.ContentWorkoutImportFileReader
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.account.AppAuthState
import app.mymusclemap.domain.account.accountDatabaseName
import app.mymusclemap.domain.account.resolveAppAuth
import app.mymusclemap.ui.auth.WELCOME_ROOT
import app.mymusclemap.ui.navigation.WeightTrackerNavHost
import app.mymusclemap.ui.theme.WeightTrackerThemeForPreview
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h800dp")
class AppCompatFirstPlanNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val userA = "11111111-1111-1111-1111-111111111111"
    private val userB = "22222222-2222-2222-2222-222222222222"
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private lateinit var database: WeightDatabase
    private lateinit var themePreferences: ThemePreferences
    private lateinit var coordinator: FirstRunCoordinator
    private lateinit var factory: WeightViewModelFactory
    private val dateProvider = FixedDateProvider(LocalDate.of(2026, 10, 9), LocalTime.of(8, 0))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        deleteAccountDatabase(context, userA)
        deleteAccountDatabase(context, userB)
        database = openAccountDatabase(context, userA)
        val clock = Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC)
        val exerciseRepository = ExerciseRepository(
            dao = database.exerciseDao(),
            clock = clock,
            templateDao = database.workoutTemplateDao(),
            sessionDao = database.workoutSessionDao()
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
        val weightRepository = WeightRepository(database.weightMeasurementDao(), clock)
        val workoutSessionRepository = WorkoutSessionRepository(
            sessionDao = database.workoutSessionDao(),
            templateDao = database.workoutTemplateDao(),
            exerciseDao = database.exerciseDao(),
            weightRepository = weightRepository,
            clock = clock,
            dateProvider = dateProvider
        )
        themePreferences = ThemePreferences(context)
        runBlocking { themePreferences.clearOnboardingProgress() }
        coordinator = FirstRunCoordinator(database, exerciseRepository, themePreferences)
        factory = WeightViewModelFactory(
            weightRepository = weightRepository,
            bodyMeasurementRepository = app.mymusclemap.data.repository.BodyMeasurementRepository(
                database.bodyMeasurementDao(),
                clock
            ),
            exerciseRepository = exerciseRepository,
            workoutTemplateRepository = workoutTemplateRepository,
            workoutSessionRepository = workoutSessionRepository,
            scheduledWorkoutRepository = scheduledWorkoutRepository,
            dateProvider = dateProvider,
            themePreferences = themePreferences,
            workoutImportFileReader = ContentWorkoutImportFileReader(context),
            appBackupRepository = AppBackupRepository(database, themePreferences),
            firstRunCoordinator = coordinator,
            progressPhotoRepository = app.mymusclemap.data.repository.ProgressPhotoRepository(
                dao = database.progressPhotoDao(),
                store = app.mymusclemap.data.progress.ProgressPhotoStore(
                    File(context.filesDir, "progress_photos_first_plan_test")
                ),
                clock = clock,
                dateProvider = dateProvider
            ),
            healthRepository = app.mymusclemap.data.health.HealthRepository(
                source = app.mymusclemap.domain.health.HealthSource.Unavailable,
                dateProvider = dateProvider
            ),
            onboardingRepository = app.mymusclemap.data.repository.OnboardingRepository(
                themePreferences = themePreferences,
                sessionRepository = workoutSessionRepository,
                weightRepository = weightRepository,
                templateRepository = workoutTemplateRepository
            ),
            achievementRepository = app.mymusclemap.data.repository.AchievementRepository(
                database = database,
                clock = clock,
                dateProvider = dateProvider
            )
        )
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { themePreferences.clearOnboardingProgress() }
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        deleteAccountDatabase(context, userA)
        deleteAccountDatabase(context, userB)
        AccountDirectory(context).clearActive()
    }

    @Test
    fun freshInstallShowsSignInAndInstallsTheNavigationDispatcher() {
        val activity = composeRule.activity
        assertSame(activity, activity.window.decorView.findViewTreeNavigationEventDispatcherOwner())
        composeRule.onNodeWithTag(WELCOME_ROOT).assertIsDisplayed()
        val signedOut = resolveAppAuth(null, null, null, now, online = true)
        assertEquals(AppAuthState.SIGNED_OUT, signedOut.state)
    }

    @Test
    fun firstAuthenticationUsesAnEmptyAccountDatabase() {
        val signedIn = resolveAppAuth(userA, userA, now.plusSeconds(60), now, online = true)
        assertEquals(AppAuthState.AUTHENTICATED, signedIn.state)
        assertEquals(0, runBlocking { database.exerciseDao().countAll() })
        assertEquals(0, runBlocking { database.workoutTemplateDao().countAll() })
        assertEquals(accountDatabaseName(userA), database.openHelper.databaseName)
        assertEquals(FirstRunDecision.ShowOnboarding, runBlocking { coordinator.prepare() })
        assertTrue(runBlocking { database.exerciseDao().countAll() } > 0)
        assertEquals(0, runBlocking { database.workoutTemplateDao().countAll() })
    }

    @Test
    fun creatingTheFirstPlanNavigatesToTheEditor() {
        assertEquals(FirstRunDecision.ShowOnboarding, runBlocking { coordinator.prepare() })
        val onboardingViewModel = OnboardingViewModel(coordinator)
        showOnActivity {
            val storeOwner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                WeightTrackerThemeForPreview {
                    var showOnboarding by remember { mutableStateOf(true) }
                    var openNewTemplate by remember { mutableStateOf(false) }
                    val step by onboardingViewModel.step.collectAsStateWithLifecycle()
                    val exit by onboardingViewModel.exit.collectAsStateWithLifecycle()
                    LaunchedEffect(exit) {
                        if (exit == OnboardingExit.OpenTemplateEditor) {
                            openNewTemplate = true
                            showOnboarding = false
                        }
                    }
                    if (showOnboarding) {
                        OnboardingScreen(
                            step = step,
                            onContinue = onboardingViewModel::onContinue,
                            onCreatePlan = onboardingViewModel::onCreatePlan,
                            onSkip = onboardingViewModel::onSkip,
                            onSetWeeklyGoal = onboardingViewModel::onWeeklyGoalSet,
                            onSkipWeeklyGoal = onboardingViewModel::onWeeklyGoalSkipped
                        )
                    } else {
                        WeightTrackerNavHost(
                            factory = factory,
                            dateProvider = dateProvider,
                            openNewTemplate = openNewTemplate,
                            onOpenedNewTemplate = { openNewTemplate = false }
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.onNodeWithTag(ONBOARDING_SKIP).performClick()
        composeRule.onNodeWithTag(ONBOARDING_PRIMARY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("New workout plan").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("New workout plan").assertIsDisplayed()
        composeRule.onNodeWithTag("template-save").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertDoesNotExist()
        assertSame(
            composeRule.activity,
            composeRule.activity.window.decorView.findViewTreeNavigationEventDispatcherOwner()
        )
    }

    @Test
    fun anExistingAccountWithAPlanOpensTheApp() {
        runBlocking {
            themePreferences.markOnboardingCompleted()
            database.workoutTemplateDao().insertTemplate(
                WorkoutTemplateEntity(
                    name = "Push A",
                    normalizedName = "push a",
                    notes = null,
                    archived = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        }
        assertEquals(FirstRunDecision.Ready, runBlocking { coordinator.prepare() })
        showOnActivity {
            val storeOwner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides storeOwner) {
                WeightTrackerThemeForPreview {
                    WeightTrackerNavHost(
                        factory = factory,
                        dateProvider = dateProvider,
                        openNewTemplate = false,
                        onOpenedNewTemplate = {}
                    )
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("dashboard_heatmap").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_heatmap").assertIsDisplayed()
        composeRule.onNodeWithTag(ONBOARDING_SCREEN).assertDoesNotExist()
        assertEquals("Push A", runBlocking { database.workoutTemplateDao().getById(1L) }?.name)
    }

    @Test
    fun signOutAndSignInAgainKeepsTheAccountPlan() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accounts = AccountDirectory(context)
        val profile = VerifiedAccountProfile(userA, "a@example.com", "Ada")
        accounts.activate(profile)
        val planId = runBlocking {
            database.workoutTemplateDao().insertTemplate(
                WorkoutTemplateEntity(
                    name = "Push A",
                    normalizedName = "push a",
                    notes = null,
                    archived = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        }
        database.close()
        accounts.clearActive()
        assertNull(accounts.activeUserId())
        accounts.activate(profile)
        assertEquals(userA, accounts.activeUserId())
        val reopened = openAccountDatabase(context, accounts.activeUserId()!!)
        try {
            assertEquals("Push A", runBlocking { reopened.workoutTemplateDao().getById(planId) }?.name)
        } finally {
            reopened.close()
        }
    }

    @Test
    fun switchingAccountsDoesNotLeakWorkoutPlans() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accounts = AccountDirectory(context)
        val planA = runBlocking {
            database.workoutTemplateDao().insertTemplate(
                WorkoutTemplateEntity(
                    name = "Push A",
                    normalizedName = "push a",
                    notes = null,
                    archived = false,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        }
        database.close()
        val databaseB = openAccountDatabase(context, userB)
        val planB = runBlocking {
            databaseB.workoutTemplateDao().insertTemplate(
                WorkoutTemplateEntity(
                    name = "Pull B",
                    normalizedName = "pull b",
                    notes = null,
                    archived = false,
                    createdAt = 2L,
                    updatedAt = 2L
                )
            )
        }
        databaseB.close()

        accounts.activate(VerifiedAccountProfile(userA, "a@example.com", "Ada"))
        val openedA = openAccountDatabase(context, accounts.activeUserId()!!)
        try {
            assertEquals(planA, runBlocking { openedA.workoutTemplateDao().findIdByNormalizedName("push a", -1L) })
            assertNull(runBlocking { openedA.workoutTemplateDao().findIdByNormalizedName("pull b", -1L) })
            assertEquals(1, runBlocking { openedA.workoutTemplateDao().countAll() })
        } finally {
            openedA.close()
        }

        accounts.clearActive()
        accounts.activate(VerifiedAccountProfile(userB, "b@example.com", "Bea"))
        val openedB = openAccountDatabase(context, accounts.activeUserId()!!)
        try {
            assertEquals(planB, runBlocking { openedB.workoutTemplateDao().findIdByNormalizedName("pull b", -1L) })
            assertNull(runBlocking { openedB.workoutTemplateDao().findIdByNormalizedName("push a", -1L) })
            assertEquals(1, runBlocking { openedB.workoutTemplateDao().countAll() })
        } finally {
            openedB.close()
        }
    }

    private fun showOnActivity(content: @Composable () -> Unit) {
        val composeView = composeRule.activity.window.decorView.findComposeView()
        composeRule.runOnIdle {
            composeView.setContent(content)
        }
        composeRule.waitForIdle()
    }

    private fun View.findComposeView(): ComposeView {
        if (this is ComposeView) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findComposeViewOrNull()?.let { return it }
            }
        }
        error("MainActivity has no ComposeView")
    }

    private fun View.findComposeViewOrNull(): ComposeView? {
        if (this is ComposeView) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findComposeViewOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun openAccountDatabase(context: Context, userId: String): WeightDatabase {
        return Room.databaseBuilder(
            context,
            WeightDatabase::class.java,
            accountDatabaseName(userId)
        )
            .allowMainThreadQueries()
            .build()
    }

    private fun deleteAccountDatabase(context: Context, userId: String) {
        val path = context.getDatabasePath(accountDatabaseName(userId))
        path.delete()
        File("${path.path}-wal").delete()
        File("${path.path}-shm").delete()
    }
}
