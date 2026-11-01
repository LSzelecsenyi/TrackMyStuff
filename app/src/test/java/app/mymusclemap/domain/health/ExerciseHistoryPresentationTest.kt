package app.mymusclemap.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class ExerciseHistoryPresentationTest {
    @Test
    fun completeSingleSourceUsesThatSourceAndIncludesStrengthInTheTotal() {
        val entries = listOf(
            entry("run", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), "com.strava"),
            entry("ride", HealthExerciseKind.CYCLING, LocalDate.of(2026, 10, 17), "com.strava"),
            entry("lift", HealthExerciseKind.STRENGTH, LocalDate.of(2026, 10, 4), "com.strava")
        )
        val counts = ExerciseHistoryPresentation.counts(entries, canLoadMore = false)
        assertEquals(ExerciseHistoryCountScope.Complete, counts.scope)
        assertEquals(3, counts.total)
        assertEquals(1, counts.running)
        assertEquals(1, counts.cycling)
        assertEquals(1, counts.strength)
        val preview = ExerciseHistoryPresentation.preview(entries, canLoadMore = false)
        assertEquals(ExerciseHistoryPreviewPhrase.NamedSource, preview.phrase)
        assertEquals("com.strava", preview.sourcePackage)
        assertEquals(3, preview.count)
    }

    @Test
    fun mixedSourcesUseANeutralDescription() {
        val entries = listOf(
            entry("run", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), "com.strava"),
            entry("ride", HealthExerciseKind.CYCLING, LocalDate.of(2026, 10, 17), "com.other")
        )
        val preview = ExerciseHistoryPresentation.preview(entries, canLoadMore = false)
        assertEquals(ExerciseHistoryPreviewPhrase.ConnectedApps, preview.phrase)
        assertNull(preview.sourcePackage)
    }

    @Test
    fun aPartialPageIsNotDescribedAsAFinishedTotalOrASingleSource() {
        val entries = List(40) { index ->
            entry("id-$index", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), "com.strava")
        }
        val preview = ExerciseHistoryPresentation.preview(entries, canLoadMore = true)
        assertEquals(40, preview.count)
        assertEquals(ExerciseHistoryPreviewPhrase.Loaded, preview.phrase)
        assertNull(preview.sourcePackage)
        assertEquals(
            ExerciseHistoryCountScope.Loaded,
            ExerciseHistoryPresentation.counts(entries, canLoadMore = true).scope
        )
    }

    @Test
    fun missingSourcesAreNotGivenAnApplicationName() {
        val entries = listOf(entry("run", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), null))
        val preview = ExerciseHistoryPresentation.preview(entries, canLoadMore = false)
        assertEquals(ExerciseHistoryPreviewPhrase.CountOnly, preview.phrase)
    }

    @Test
    fun filtersKeepChronologicalOrderAndDropARepeatedId() {
        val newer = entry("new", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), "com.strava")
        val older = entry("old", HealthExerciseKind.CYCLING, LocalDate.of(2026, 9, 30), "com.strava")
        val copy = newer.copy()
        val strength = entry("lift", HealthExerciseKind.STRENGTH, LocalDate.of(2026, 10, 11), "com.strava")
        val visible = ExerciseHistoryPresentation.visibleEntries(
            listOf(newer, copy, strength, older),
            ExerciseHistoryFilter.All
        )
        assertEquals(listOf("new", "lift", "old"), visible.map { it.id })
        assertEquals(
            listOf("new"),
            ExerciseHistoryPresentation.visibleEntries(visible, ExerciseHistoryFilter.Running).map { it.id }
        )
        assertTrue(
            ExerciseHistoryPresentation.visibleEntries(visible, ExerciseHistoryFilter.Cycling)
                .single().id == "old"
        )
        assertEquals(
            listOf("lift"),
            ExerciseHistoryPresentation.visibleEntries(visible, ExerciseHistoryFilter.Strength).map { it.id }
        )
    }

    @Test
    fun monthsFollowTheLoadedOrder() {
        val entries = listOf(
            entry("oct", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), "com.strava"),
            entry("sep", HealthExerciseKind.CYCLING, LocalDate.of(2026, 9, 30), "com.strava")
        )
        val months = ExerciseHistoryPresentation.months(entries)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), months.map { it.month })
        assertEquals(listOf("oct"), months[0].entries.map { it.id })
        assertEquals(listOf("sep"), months[1].entries.map { it.id })
    }

    @Test
    fun anEmptyFilterStillNeedsOlderPagesWhenMoreHistoryExists() {
        val running = entry("run", HealthExerciseKind.RUNNING, LocalDate.of(2026, 10, 31), "com.strava")
        assertTrue(
            ExerciseHistoryPresentation.needsOlderPage(
                entries = listOf(running),
                filter = ExerciseHistoryFilter.Cycling,
                canLoadMore = true,
                failed = false,
                loading = false
            )
        )
        assertFalse(
            ExerciseHistoryPresentation.needsOlderPage(
                entries = listOf(running),
                filter = ExerciseHistoryFilter.Cycling,
                canLoadMore = false,
                failed = false,
                loading = false
            )
        )
        assertFalse(
            ExerciseHistoryPresentation.needsOlderPage(
                entries = listOf(running),
                filter = ExerciseHistoryFilter.Cycling,
                canLoadMore = true,
                failed = true,
                loading = false
            )
        )
        assertFalse(
            ExerciseHistoryPresentation.needsOlderPage(
                entries = listOf(running),
                filter = ExerciseHistoryFilter.All,
                canLoadMore = true,
                failed = false,
                loading = false
            )
        )
    }

    private fun entry(
        id: String,
        kind: HealthExerciseKind,
        date: LocalDate,
        source: String?
    ): ExerciseHistoryEntry {
        return ExerciseHistoryEntry(
            id = id,
            kind = kind,
            start = Instant.parse("2026-10-31T13:22:00Z"),
            date = date,
            startTime = LocalTime.of(15, 22),
            duration = Duration.ofMinutes(39),
            sourcePackage = source
        )
    }
}
