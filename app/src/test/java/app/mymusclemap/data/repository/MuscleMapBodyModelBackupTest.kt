package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupParseResult
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.MuscleMapBodyModelBackupStore
import app.mymusclemap.data.preferences.MuscleMapBodyModelPreferences
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.domain.musclemap.MuscleMapBodyModel
import app.mymusclemap.domain.theme.AppearanceSettings
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MuscleMapBodyModelBackupTest {
    private lateinit var sourceDb: WeightDatabase
    private lateinit var targetDb: WeightDatabase
    private lateinit var themePreferences: ThemePreferences

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDb = openDb(context)
        targetDb = openDb(context)
        themePreferences = ThemePreferences(context)
        themePreferences.replaceAppearance(AppearanceSettings.Default)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun olderBackupWithoutTheFieldRestoresMaleAndAFemaleChoiceRoundTrips() = runTest {
        val source = AppBackupSource("hu.laca.weighttracker.debug", "1.0-debug")
        val older = AppBackupRepository(sourceDb, themePreferences).exportJson(source)
        val olderSnapshot = (AppBackupJson.parse(older) as AppBackupParseResult.Success).snapshot
        assertFalse(olderSnapshot.settings.containsKey(MuscleMapBodyModelPreferences.SETTINGS_KEY))

        val restoredFromOlder = MemoryBodyModel(MuscleMapBodyModel.FEMALE)
        assertEquals(
            AppBackupRestoreResult.Success,
            AppBackupRepository(targetDb, themePreferences, bodyModels = restoredFromOlder).restoreJson(older)
        )
        assertEquals(MuscleMapBodyModel.MALE, restoredFromOlder.current())

        val chosen = MemoryBodyModel(MuscleMapBodyModel.FEMALE)
        val json = AppBackupRepository(sourceDb, themePreferences, bodyModels = chosen).exportJson(source)
        val snapshot = (AppBackupJson.parse(json) as AppBackupParseResult.Success).snapshot
        assertEquals("FEMALE", snapshot.settings[MuscleMapBodyModelPreferences.SETTINGS_KEY])
        val restored = MemoryBodyModel(MuscleMapBodyModel.MALE)
        assertEquals(
            AppBackupRestoreResult.Success,
            AppBackupRepository(targetDb, themePreferences, bodyModels = restored).restoreJson(json)
        )
        assertEquals(MuscleMapBodyModel.FEMALE, restored.current())
    }

    private fun openDb(context: Context): WeightDatabase {
        return Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private class MemoryBodyModel(initial: MuscleMapBodyModel) : MuscleMapBodyModelBackupStore {
        private var value = initial

        override fun current(): MuscleMapBodyModel = value

        override suspend fun replace(model: MuscleMapBodyModel) {
            value = model
        }
    }
}
