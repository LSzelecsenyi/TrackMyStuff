package app.mymusclemap.domain.locale

import java.text.Collator
import java.util.Locale

object LocalizedLabelOrder {
    val locale: Locale = AppLocale.UI

    fun collator(): Collator {
        return Collator.getInstance(locale).apply {
            strength = Collator.TERTIARY
            decomposition = Collator.CANONICAL_DECOMPOSITION
        }
    }

    fun <T> sorted(
        items: Iterable<T>,
        label: (T) -> String,
        key: (T) -> String
    ): List<T> {
        val collator = collator()
        return items.sortedWith { left, right ->
            val byLabel = collator.compare(label(left), label(right))
            if (byLabel != 0) {
                byLabel
            } else {
                key(left).compareTo(key(right))
            }
        }
    }

    fun compareLabels(left: String, right: String): Int {
        return collator().compare(left, right)
    }
}
