package hu.laca.weighttracker.domain.journal

import hu.laca.weighttracker.domain.workout.WorkoutSession
import hu.laca.weighttracker.ui.history.WorkoutDetailViewModel
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class JournalFeatureAbsenceTest {
    @Test
    fun completedWorkoutDetailHasNoEditingApi() {
        val names = WorkoutDetailViewModel::class.java.declaredMethods.map { it.name.lowercase() }
        assertFalse(names.any { it.contains("complete") })
        assertFalse(names.any { it.contains("finish") })
        assertFalse(names.any { it.contains("reopen") })
        assertFalse(names.any { it.contains("editset") })
        assertFalse(names.any { it.contains("addset") })
        assertFalse(names.any { it.contains("deletesession") })
    }

    @Test
    fun sessionModelHasNoRirRpeHeatmapOrStatistics() {
        val names = WorkoutSession::class.java.declaredFields.map { it.name.lowercase() } +
            JournalEntry::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(names.any { it.contains("rir") || it.contains("rpe") })
        assertFalse(names.any { it.contains("heatmap") || it.contains("calorie") || it.contains("statistic") })
    }

    @Test
    fun sourceDoesNotIntroduceRirRpeHeatmapOrStatisticsUi() {
        val roots = listOf(
            File("src/main/java/hu/laca/weighttracker/ui/history"),
            File("src/main/java/hu/laca/weighttracker/domain/journal"),
            File("src/main/res/values/strings.xml"),
            File("app/src/main/java/hu/laca/weighttracker/ui/history"),
            File("app/src/main/java/hu/laca/weighttracker/domain/journal"),
            File("app/src/main/res/values/strings.xml")
        )
        val haystack = roots.filter { it.exists() }.flatMap { file ->
            if (file.isDirectory) file.walkTopDown().filter { it.isFile }.toList() else listOf(file)
        }.joinToString("\n") { it.readText() }.lowercase()
        assertFalse(haystack.contains("rir"))
        assertFalse(haystack.contains("rpe"))
        assertFalse(haystack.contains("heatmap"))
        assertFalse(haystack.contains("testtérkép"))
        assertFalse(haystack.contains("kalor"))
    }
}
