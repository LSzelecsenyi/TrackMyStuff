package app.mymusclemap.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.assertHasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import app.mymusclemap.MainActivity
import app.mymusclemap.domain.exercise.MuscleGroup
import app.mymusclemap.domain.musclemap.MuscleHeatmapAssembler
import app.mymusclemap.domain.musclemap.MuscleTrainingExercise
import app.mymusclemap.domain.workout.SessionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MuscleHeatmapWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.parse("2026-10-02")

    @After
    fun tearDown() {
        HeatmapWidgetUpdater.refreshListener = null
    }

    @Test
    fun widgetStateIsTheSharedAssemblerResult() {
        val exercises = listOf(
            MuscleTrainingExercise(
                SessionStatus.COMPLETED,
                today.minusDays(3),
                MuscleGroup.CHEST,
                listOf(MuscleGroup.TRICEPS),
                completedSetCount = 2
            ),
            MuscleTrainingExercise(
                SessionStatus.IN_PROGRESS,
                today,
                MuscleGroup.BICEPS,
                emptyList(),
                completedSetCount = 1
            )
        )
        assertEquals(
            MuscleHeatmapAssembler.assemble(exercises, today),
            HeatmapWidgetState.from(exercises, today)
        )
    }

    @Test
    fun placedWidgetIsOnlyTheTwoBodies() = runGlanceAppWidgetUnitTest {
        val state = MuscleHeatmapAssembler.assemble(emptyList(), today)
        val chrome = HeatmapWidgetChromeFactory.forSystemDark(isDark = false)
        setContext(context)
        setAppWidgetSize(DpSize(180.dp, 250.dp))
        provideComposable {
            MuscleHeatmapWidgetContent(
                state = state,
                chrome = chrome,
                openOverview = androidx.glance.appwidget.action.actionStartActivity(
                    HeatmapWidgetIntents.openOverview(context)
                )
            )
        }
        onNode(hasText("Muscle heatmap")).assertDoesNotExist()
        onNode(hasText("Based on recent completed workouts")).assertDoesNotExist()
        listOf("Today", "1–2d", "3–4d", "5–6d", "7–13d", "14+d", "Not trained yet", "Last 7 days")
            .forEach { label ->
                onNode(hasText(label)).assertDoesNotExist()
            }
        onNode(hasTestTag(HEATMAP_WIDGET_FRONT_TAG)).assertExists()
        onNode(hasTestTag(HEATMAP_WIDGET_BACK_TAG)).assertExists()
        onNode(hasContentDescription("Muscle heatmap. Tap to open Strict."))
            .assertHasStartActivityClickAction(HeatmapWidgetIntents.openOverview(context))
    }

    @Test
    fun missingDataStaysTappableWithoutExplanatoryText() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(110.dp, 180.dp))
        provideComposable {
            MuscleHeatmapWidgetContent(
                state = null,
                chrome = HeatmapWidgetChromeFactory.forSystemDark(isDark = true),
                openOverview = androidx.glance.appwidget.action.actionStartActivity(
                    HeatmapWidgetIntents.openOverview(context)
                )
            )
        }
        onNode(hasText("Couldn't refresh the heatmap")).assertDoesNotExist()
        onNode(hasText("No workouts yet")).assertDoesNotExist()
        onNode(hasTestTag(HEATMAP_WIDGET_FRONT_TAG)).assertDoesNotExist()
        onNode(hasTestTag(HEATMAP_WIDGET_TAG)).assertHasStartActivityClickAction(
            HeatmapWidgetIntents.openOverview(context)
        )
    }

    @Test
    fun overviewIntentReusesTheExistingTaskAndRequestsOverview() {
        val intent = HeatmapWidgetIntents.openOverview(context)
        assertEquals(MainActivity::class.java.name, intent.component!!.className)
        assertTrue(intent.getBooleanExtra(MainActivity.EXTRA_OPEN_OVERVIEW, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun dateTimeAndZoneChangesRequestOneRefresh() {
        var requests = 0
        HeatmapWidgetUpdater.refreshListener = { requests++ }
        val receiver = HeatmapDateChangeReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_DATE_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(3, requests)
    }

    @Test
    fun receiverPublishesTheHeatmapWidget() {
        assertTrue(MuscleHeatmapWidgetReceiver().glanceAppWidget is MuscleHeatmapWidget)
    }
}
