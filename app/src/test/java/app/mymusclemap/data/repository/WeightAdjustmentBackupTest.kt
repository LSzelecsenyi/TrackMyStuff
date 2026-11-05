package app.mymusclemap.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.data.appbackup.AppBackupJson
import app.mymusclemap.data.appbackup.AppBackupParseResult
import app.mymusclemap.data.appbackup.AppBackupRestoreResult
import app.mymusclemap.data.appbackup.AppBackupSource
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.preferences.ThemePreferences
import app.mymusclemap.data.preferences.WeightAdjustmentBackup
import app.mymusclemap.data.preferences.WeightAdjustmentBackupStore
import app.mymusclemap.data.preferences.WeightAdjustmentPreferences
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.units.WeightAdjustmentConfiguration
import app.mymusclemap.domain.units.WeightAdjustmentStep
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class WeightAdjustmentBackupTest {
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
    fun aBackupRoundTripsTheStepAndAnOlderBackupKeepsTheDefaults() = runTest {
        val source = AppBackupSource("app.mymusclemap", "1.0")
        val older = AppBackupRepository(sourceDb, themePreferences).exportJson(source)
        val olderSnapshot = (AppBackupJson.parse(older) as AppBackupParseResult.Success).snapshot
        assertFalse(olderSnapshot.settings.containsKey(WeightAdjustmentPreferences.METRIC_SETTINGS_KEY))
        assertFalse(olderSnapshot.settings.containsKey(WeightAdjustmentPreferences.IMPERIAL_SETTINGS_KEY))

        val restoredFromOlder = MemorySteps(
            WeightAdjustmentConfiguration(metric = BigDecimal("1.25"), imperial = BigDecimal("10"))
        )
        assertEquals(
            AppBackupRestoreResult.Success,
            AppBackupRepository(targetDb, themePreferences, weightAdjustment = restoredFromOlder).restoreJson(older)
        )
        assertEquals("2.5", WeightAdjustmentStep.storedText(restoredFromOlder.current().metric))
        assertEquals("5", WeightAdjustmentStep.storedText(restoredFromOlder.current().imperial))

        val chosen = MemorySteps(
            WeightAdjustmentConfiguration(metric = BigDecimal("1.25"), imperial = BigDecimal("10"))
        )
        val json = AppBackupRepository(sourceDb, themePreferences, weightAdjustment = chosen).exportJson(source)
        val snapshot = (AppBackupJson.parse(json) as AppBackupParseResult.Success).snapshot
        assertEquals("1.25", snapshot.settings[WeightAdjustmentPreferences.METRIC_SETTINGS_KEY])
        assertEquals("10", snapshot.settings[WeightAdjustmentPreferences.IMPERIAL_SETTINGS_KEY])
        val restored = MemorySteps(WeightAdjustmentConfiguration())
        assertEquals(
            AppBackupRestoreResult.Success,
            AppBackupRepository(targetDb, themePreferences, weightAdjustment = restored).restoreJson(json)
        )
        assertEquals(0, restored.current().metric.compareTo(BigDecimal("1.25")))
        assertEquals(0, restored.current().imperial.compareTo(BigDecimal("10")))
        assertEquals(
            "1.25",
            WeightAdjustmentStep.storedText(WeightAdjustmentBackup.decode(snapshot.settings).metric)
        )
    }

    private fun openDb(context: Context): WeightDatabase {
        return Room.inMemoryDatabaseBuilder(context, WeightDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    private class MemorySteps(initial: WeightAdjustmentConfiguration) : WeightAdjustmentBackupStore {
        private var value = initial

        override fun current(): WeightAdjustmentConfiguration = value

        override suspend fun replace(configuration: WeightAdjustmentConfiguration) {
            value = configuration
        }
    }
}
