package app.mymusclemap.ui.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.FakeWeightMeasurementDao
import app.mymusclemap.MainDispatcherRule
import app.mymusclemap.data.local.WeightDatabase
import app.mymusclemap.data.repository.BodyMeasurementRepository
import app.mymusclemap.data.repository.WeightRepository
import app.mymusclemap.domain.FixedDateProvider
import app.mymusclemap.domain.body.BodyMeasurementType
import app.mymusclemap.domain.entitlement.AppFeature
import app.mymusclemap.domain.entitlement.SelectiveFeatureEntitlements
import app.mymusclemap.domain.model.ChartRange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class BodyProgressViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today = LocalDate.of(2026, 9, 26)
    private val dateProvider = FixedDateProvider(today, LocalTime.of(8, 0))
    private val clock = Clock.fixed(Instant.parse("2026-09-26T08:00:00Z"), ZoneOffset.UTC)

    @Test
    fun overviewStaysCompleteWhileDetailRangeControlsTheChart() = runTest {
        val database = database()
        try {
            val body = BodyMeasurementRepository(database.bodyMeasurementDao(), clock)
            body.save(BodyMeasurementType.WAIST.code, today, 84.0)
            body.save(BodyMeasurementType.CHEST.code, today.minusDays(40), 100.0)
            body.save(BodyMeasurementType.CHEST.code, today.minusDays(3), 101.5)
            body.save(BodyMeasurementType.UPPER_ARM.code, today.minusDays(1), 32.0)
            val viewModel = WeightDetailsViewModel(
                repository = WeightRepository(FakeWeightMeasurementDao(), clock),
                dateProvider = dateProvider,
                bodyRepository = body,
                entitlements = SelectiveFeatureEntitlements(emptySet())
            )
            val state = viewModel.uiState.first { current ->
                current.rows.single { it.type == BodyMeasurementType.CHEST }.latest?.value == 101.5
            }
            assertEquals(BodyMeasurementType.entries.map { it.code }, state.rows.map { it.typeCode })
            assertEquals(ChartRange.Days30, state.chartRange)
            assertEquals(ChartRange.Days30, state.bodyChartRange)

            val waist = state.rows.single { it.type == BodyMeasurementType.WAIST }
            assertFalse(waist.lockedEmpty)
            assertEquals(84.0, waist.latest!!.value, 0.0)
            assertNull(waist.change)
            assertFalse(state.details.getValue(BodyMeasurementType.WAIST.code).canAdd)

            val chest = state.rows.single { it.type == BodyMeasurementType.CHEST }
            assertFalse(chest.lockedEmpty)
            assertEquals(1.5, chest.change!!, 0.0)
            val chestDetail = state.details.getValue(BodyMeasurementType.CHEST.code)
            assertFalse(chestDetail.showChart)
            assertEquals(1, chestDetail.historyNewestFirst.size)
            assertNull(chestDetail.window.change)
            assertTrue(chestDetail.canAdd)

            val upperArm = state.details.getValue(BodyMeasurementType.UPPER_ARM.code)
            assertFalse(upperArm.showChart)
            assertEquals(1, upperArm.historyNewestFirst.size)
            assertFalse(state.rows.single { it.type == BodyMeasurementType.UPPER_ARM }.lockedEmpty)

            val bodyFat = state.rows.single { it.type == BodyMeasurementType.BODY_FAT }
            assertTrue(bodyFat.lockedEmpty)
            assertNull(bodyFat.latest)
            assertFalse(state.details.getValue(BodyMeasurementType.BODY_FAT.code).showChart)

            viewModel.onChartRangeSelected(ChartRange.All)
            val weightExpanded = viewModel.uiState.first { it.chartRange == ChartRange.All }
            assertEquals(ChartRange.Days30, weightExpanded.bodyChartRange)
            assertFalse(weightExpanded.details.getValue(BodyMeasurementType.CHEST.code).showChart)
            assertEquals(101.5, weightExpanded.rows.single { it.type == BodyMeasurementType.CHEST }.latest!!.value, 0.0)

            viewModel.onBodyChartRangeSelected(ChartRange.All)
            val detailed = viewModel.uiState.first {
                it.details.getValue(BodyMeasurementType.CHEST.code).showChart
            }
            val rangedChest = detailed.details.getValue(BodyMeasurementType.CHEST.code)
            assertEquals(ChartRange.All, rangedChest.range)
            assertEquals(2, rangedChest.historyNewestFirst.size)
            assertEquals(101.5, rangedChest.historyNewestFirst.first().value, 0.0)
            assertEquals(1.5, rangedChest.window.change!!, 0.0)
            assertEquals(ChartRange.All, detailed.chartRange)

            viewModel.openBodyEditor(BodyMeasurementType.WAIST)
            assertNotNull(viewModel.uiState.value.bodyEditor)
            viewModel.dismissBodyEditor()

            val existing = rangedChest.historyNewestFirst.first()
            viewModel.openBodyEditor(BodyMeasurementType.CHEST, existing)
            assertEquals(existing.id, viewModel.uiState.value.bodyEditor!!.existing!!.id)
            viewModel.onBodyEditorValueChange("102.0")
            viewModel.saveBodyEditor()
            viewModel.uiState.first { current ->
                current.rows.single { it.type == BodyMeasurementType.CHEST }.latest!!.value == 102.0
            }
            viewModel.openBodyEditor(BodyMeasurementType.CHEST, null)
            assertEquals(AppFeature.AdvancedBodyMeasurements, viewModel.uiState.value.lockedFeature)
            assertNull(viewModel.uiState.value.bodyEditor)

            viewModel.showBodyMeasurementLocked(BodyMeasurementType.BODY_FAT)
            assertEquals(AppFeature.AdvancedBodyMeasurements, viewModel.uiState.value.lockedFeature)
            viewModel.setMeasurementDetailVisible(true)
            viewModel.leaveMeasurementDetail()
            assertFalse(viewModel.uiState.value.measurementDetailVisible)
            assertNull(viewModel.uiState.value.lockedFeature)
            assertNull(viewModel.uiState.value.bodyEditor)
        } finally {
            database.close()
        }
    }

    @Test
    fun addIsEnabledOnlyWhenTodayHasNoMeasurement() = runTest {
        val database = database()
        try {
            val body = BodyMeasurementRepository(database.bodyMeasurementDao(), clock)
            body.save(BodyMeasurementType.THIGH.code, today.minusDays(40), 49.0)
            body.save(BodyMeasurementType.CHEST.code, today, 100.0)
            val viewModel = WeightDetailsViewModel(
                repository = WeightRepository(FakeWeightMeasurementDao(), clock),
                dateProvider = dateProvider,
                bodyRepository = body,
                entitlements = SelectiveFeatureEntitlements(emptySet())
            )
            val initial = viewModel.uiState.first { current ->
                current.details.getValue(BodyMeasurementType.THIGH.code).window.latest != null
            }
            assertTrue(initial.details.getValue(BodyMeasurementType.WAIST.code).canAdd)
            val initialThigh = initial.details.getValue(BodyMeasurementType.THIGH.code)
            assertTrue(initialThigh.canAdd)
            assertTrue(initialThigh.window.points.isEmpty())
            assertFalse(initial.details.getValue(BodyMeasurementType.CHEST.code).canAdd)
            assertTrue(initial.details.getValue(BodyMeasurementType.BODY_FAT.code).canAdd)

            viewModel.onBodyChartRangeSelected(ChartRange.Days90)
            val days90 = viewModel.uiState.first { it.bodyChartRange == ChartRange.Days90 }
            assertTrue(days90.details.getValue(BodyMeasurementType.THIGH.code).canAdd)
            assertEquals(1, days90.details.getValue(BodyMeasurementType.THIGH.code).window.points.size)
            assertFalse(days90.details.getValue(BodyMeasurementType.CHEST.code).canAdd)

            viewModel.onBodyChartRangeSelected(ChartRange.All)
            val all = viewModel.uiState.first { it.bodyChartRange == ChartRange.All }
            assertTrue(all.details.getValue(BodyMeasurementType.THIGH.code).canAdd)
            assertEquals(1, all.details.getValue(BodyMeasurementType.THIGH.code).historyNewestFirst.size)
            assertFalse(all.details.getValue(BodyMeasurementType.CHEST.code).canAdd)

            val todayChest = all.details.getValue(BodyMeasurementType.CHEST.code).historyNewestFirst.single()
            viewModel.openBodyEditor(BodyMeasurementType.CHEST, todayChest)
            val editor = viewModel.uiState.value.bodyEditor
            assertNotNull(editor)
            assertEquals(todayChest.id, editor!!.existing!!.id)
            viewModel.requestBodyDelete()
            assertTrue(viewModel.uiState.value.bodyEditor!!.showDeleteConfirm)
            viewModel.dismissBodyEditor()

            viewModel.openBodyEditor(BodyMeasurementType.THIGH, null)
            assertEquals(AppFeature.AdvancedBodyMeasurements, viewModel.uiState.value.lockedFeature)
            assertNull(viewModel.uiState.value.bodyEditor)

            body.save(BodyMeasurementType.WAIST.code, today, 84.0)
            val withToday = viewModel.uiState.first { current ->
                current.details.getValue(BodyMeasurementType.WAIST.code).window.latest?.value == 84.0
            }
            assertFalse(withToday.details.getValue(BodyMeasurementType.WAIST.code).canAdd)
            viewModel.onBodyChartRangeSelected(ChartRange.Days30)
            val ranged = viewModel.uiState.first { it.bodyChartRange == ChartRange.Days30 }
            assertFalse(ranged.details.getValue(BodyMeasurementType.WAIST.code).canAdd)
            val todayWaist = ranged.details.getValue(BodyMeasurementType.WAIST.code).historyNewestFirst.single()
            viewModel.openBodyEditor(BodyMeasurementType.WAIST, todayWaist)
            assertEquals(todayWaist.id, viewModel.uiState.value.bodyEditor!!.existing!!.id)
            viewModel.requestBodyDelete()
            assertTrue(viewModel.uiState.value.bodyEditor!!.showDeleteConfirm)
        } finally {
            database.close()
        }
    }

    private fun database(): WeightDatabase {
        return Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            WeightDatabase::class.java
        ).allowMainThreadQueries().build()
    }
}
