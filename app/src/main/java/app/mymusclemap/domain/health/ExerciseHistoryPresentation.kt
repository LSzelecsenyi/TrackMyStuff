package app.mymusclemap.domain.health

import java.time.YearMonth

enum class ExerciseHistoryFilter {
    All,
    Running,
    Cycling,
    Strength
}

enum class ExerciseHistoryCountScope {
    /** Every page Health Connect returned for this query has been loaded. */
    Complete,

    /** More pages exist. Counts describe only the sessions loaded so far. */
    Loaded
}

data class ExerciseHistoryCounts(
    val scope: ExerciseHistoryCountScope,
    val total: Int,
    val running: Int,
    val cycling: Int,
    val strength: Int
)

enum class ExerciseHistoryPreviewPhrase {
    /** [ExerciseHistoryPreview.sourcePackage] is the only source in a complete result. */
    NamedSource,

    /** A complete result includes more than one application. */
    ConnectedApps,

    /** A complete result has no source metadata. */
    CountOnly,

    /** More pages exist, so the number is not a lifetime total and the source mix is unknown. */
    Loaded
}

data class ExerciseHistoryPreview(
    val count: Int,
    val phrase: ExerciseHistoryPreviewPhrase,
    val sourcePackage: String?
)

data class ExerciseHistoryMonth(
    val month: YearMonth,
    val entries: List<ExerciseHistoryEntry>
)

object ExerciseHistoryPresentation {
    fun counts(entries: List<ExerciseHistoryEntry>, canLoadMore: Boolean): ExerciseHistoryCounts {
        return ExerciseHistoryCounts(
            scope = if (canLoadMore) {
                ExerciseHistoryCountScope.Loaded
            } else {
                ExerciseHistoryCountScope.Complete
            },
            total = entries.size,
            running = entries.count { it.kind == HealthExerciseKind.RUNNING },
            cycling = entries.count { it.kind == HealthExerciseKind.CYCLING },
            strength = entries.count { it.kind == HealthExerciseKind.STRENGTH }
        )
    }

    fun preview(entries: List<ExerciseHistoryEntry>, canLoadMore: Boolean): ExerciseHistoryPreview {
        if (canLoadMore) {
            return ExerciseHistoryPreview(entries.size, ExerciseHistoryPreviewPhrase.Loaded, null)
        }
        val packages = entries.map { it.sourcePackage?.takeIf { name -> name.isNotBlank() } }.distinct()
        val named = packages.filterNotNull()
        return when {
            named.size == 1 && packages.size == 1 ->
                ExerciseHistoryPreview(entries.size, ExerciseHistoryPreviewPhrase.NamedSource, named.single())
            named.isNotEmpty() ->
                ExerciseHistoryPreview(entries.size, ExerciseHistoryPreviewPhrase.ConnectedApps, null)
            else -> ExerciseHistoryPreview(entries.size, ExerciseHistoryPreviewPhrase.CountOnly, null)
        }
    }

    fun matches(entry: ExerciseHistoryEntry, filter: ExerciseHistoryFilter): Boolean {
        return when (filter) {
            ExerciseHistoryFilter.All -> entry.kind != HealthExerciseKind.OTHER
            ExerciseHistoryFilter.Running -> entry.kind == HealthExerciseKind.RUNNING
            ExerciseHistoryFilter.Cycling -> entry.kind == HealthExerciseKind.CYCLING
            ExerciseHistoryFilter.Strength -> entry.kind == HealthExerciseKind.STRENGTH
        }
    }

    fun visibleEntries(
        entries: List<ExerciseHistoryEntry>,
        filter: ExerciseHistoryFilter
    ): List<ExerciseHistoryEntry> {
        val seen = HashSet<String>()
        return entries.filter { entry ->
            matches(entry, filter) && seen.add(entry.id)
        }
    }

    fun months(entries: List<ExerciseHistoryEntry>): List<ExerciseHistoryMonth> {
        val groups = ArrayList<ExerciseHistoryMonth>()
        entries.forEach { entry ->
            val month = YearMonth.from(entry.date)
            val current = groups.lastOrNull()
            if (current != null && current.month == month) {
                groups[groups.lastIndex] = current.copy(entries = current.entries + entry)
            } else {
                groups += ExerciseHistoryMonth(month, listOf(entry))
            }
        }
        return groups
    }

    /**
     * True when the selected filter has no match in the sessions loaded so far and Health Connect
     * still has another page. An empty filter result is not final until this is false.
     */
    fun needsOlderPage(
        entries: List<ExerciseHistoryEntry>,
        filter: ExerciseHistoryFilter,
        canLoadMore: Boolean,
        failed: Boolean,
        loading: Boolean
    ): Boolean {
        if (!canLoadMore || failed || loading) return false
        return visibleEntries(entries, filter).isEmpty()
    }
}
