package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.locale.LocalizedLabelOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateCatalogLogicTest {
    @Test
    fun givenUnsortedTemplatesWhenFilteredThenHungarianDisplayedNamesAreAbcOrdered() {
        val result = TemplateCatalogLogic.filter(
            items = listOf(
                item(10, "Zárógyakorlat"),
                item(11, "Álló evezés"),
                item(12, "Alma")
            ),
            query = "",
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf("Álló evezés", "Alma", "Zárógyakorlat"), result.map { it.template.name })
    }

    @Test
    fun givenAccentedHungarianNamesWhenSortedThenHuHuCollatorRulesApply() {
        val result = TemplateCatalogLogic.filter(
            items = listOf(
                item(1, "őszibarack"),
                item(2, "alma"),
                item(3, "Áron"),
                item(4, "béka"),
                item(5, "ékezet")
            ),
            query = "",
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(
            listOf("alma", "Áron", "béka", "ékezet", "őszibarack"),
            result.map { it.template.name }
        )
        assertTrue(LocalizedLabelOrder.compareLabels("alma", "Áron") < 0)
        assertTrue(LocalizedLabelOrder.compareLabels("béka", "ékezet") < 0)
    }

    @Test
    fun givenQueryWhenTypedThenListFiltersImmediatelyCaseInsensitively() {
        val push = item(1, "Push – Kondipark")
        val pull = item(2, "Pull A")
        val result = TemplateCatalogLogic.filter(
            items = listOf(push, pull),
            query = "  pUsH  ",
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(push), result)
        val blank = TemplateCatalogLogic.filter(
            items = listOf(push, pull),
            query = "   ",
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf("Pull A", "Push – Kondipark"), blank.map { it.template.name })
    }

    @Test
    fun archivedFilterHidesActiveTemplates() {
        val active = item(1, "Push")
        val archived = item(2, "Alma", archived = true)
        val result = TemplateCatalogLogic.filter(
            items = listOf(active, archived),
            query = "",
            archiveFilter = ArchiveFilter.ARCHIVED
        )
        assertEquals(listOf(archived), result)
        val all = TemplateCatalogLogic.filter(
            items = listOf(active, archived),
            query = "",
            archiveFilter = ArchiveFilter.ALL
        )
        assertEquals(listOf("Alma", "Push"), all.map { it.template.name })
    }

    @Test
    fun identicalDisplayedNamesUseStableSecondaryKey() {
        val later = item(20, "Mellnyomás")
        val earlier = item(4, "Mellnyomás")
        val result = TemplateCatalogLogic.filter(
            items = listOf(later, earlier),
            query = "",
            archiveFilter = ArchiveFilter.ACTIVE
        )
        assertEquals(listOf(4L, 20L), result.map { it.template.id })
    }

    private fun item(
        id: Long,
        name: String,
        archived: Boolean = false
    ): TemplateListItem {
        return TemplateListItem(
            template = WorkoutTemplate(
                id = id,
                name = name,
                normalizedName = TemplateNaming.normalize(name),
                notes = null,
                archived = archived,
                createdAt = 1L,
                updatedAt = 1L
            ),
            exerciseCount = 1,
            setCount = 2,
            primaryMuscles = emptyList(),
            muscleSummary = TemplateMuscleSummary(emptyList(), emptyList())
        )
    }
}
