package app.mymusclemap.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.repository.AppBackupRepository
import app.mymusclemap.data.repository.WeeklyGoalRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.workout.PendingWeeklyGoal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WeeklyGoalSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 3, 12)
    private val clock = Clock.fixed(Instant.parse("2026-03-12T08:00:00Z"), ZoneOffset.UTC)
    private val viewModelStore = ViewModelStore()
    private lateinit var database: WeightDatabase
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val themePreferences = ThemePreferences(context)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    themePreferences = themePreferences,
                    dateProvider = FixedDateProvider(today, LocalTime.of(8, 0)),
                    appBackupRepository = AppBackupRepository(database, themePreferences),
                    weeklyGoalRepository = WeeklyGoalRepository(database.weeklyWorkoutGoalDao(), clock)
                ) as T
            }
        }
        viewModel = ViewModelProvider(viewModelStore, factory)[SettingsViewModel::class.java]
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        database.close()
    }

    @Test
    fun settingChangingAndDisablingAGoalUpdatesTheDisplayedStatus() = runTest {
        viewModel.uiState.first { it.weeklyGoal.current.goal == null }
        viewModel.setWeeklyGoal(4)
        viewModel.uiState.first { it.weeklyGoal.current.goal == 4 }
        assertNull(viewModel.uiState.value.weeklyGoal.pending)

        viewModel.setWeeklyGoal(6)
        val pending = viewModel.uiState.first { it.weeklyGoal.pending is PendingWeeklyGoal.Update }.weeklyGoal.pending
        assertEquals(6, (pending as PendingWeeklyGoal.Update).workoutsPerWeek)
        assertEquals(4, viewModel.uiState.value.weeklyGoal.current.goal)

        viewModel.disableWeeklyGoal()
        viewModel.uiState.first { it.weeklyGoal.pending is PendingWeeklyGoal.Disable }
        assertEquals(4, viewModel.uiState.value.weeklyGoal.current.goal)
        assertTrue(database.weeklyWorkoutGoalDao().getAll().any { it.workoutsPerWeek == null })
    }
}
