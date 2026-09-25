package app.mymusclemap.domain.workout

import app.mymusclemap.domain.exercise.ArchiveFilter
import app.mymusclemap.domain.locale.LocalizedLabelOrder

object TemplateCatalogLogic {
    fun filter(
        items: List<TemplateListItem>,
        query: String,
        archiveFilter: ArchiveFilter
    ): List<TemplateListItem> {
        val needle = TemplateNaming.normalize(query)
        val visible = items.filter { item ->
            val archiveMatch = when (archiveFilter) {
                ArchiveFilter.ACTIVE -> !item.template.archived
                ArchiveFilter.ARCHIVED -> item.template.archived
                ArchiveFilter.ALL -> true
            }
            val searchMatch = needle.isEmpty() ||
                item.template.normalizedName.contains(needle) ||
                TemplateNaming.normalize(item.template.name).contains(needle)
            archiveMatch && searchMatch
        }
        return LocalizedLabelOrder.sorted(
            items = visible,
            label = { it.template.name },
            key = { it.template.id.toString().padStart(20, '0') }
        )
    }

    fun hasSearchQuery(query: String): Boolean {
        return query.isNotBlank()
    }
}
