package app.mymusclemap.domain.locale

import java.text.Collator
import java.text.ParseException
import java.text.RuleBasedCollator
import java.util.Locale

object LocalizedLabelOrder {
    val locale: Locale = Locale.forLanguageTag("hu-HU")

    private const val HUNGARIAN_PRIMARY_RULES = """
        < ' ' < '-' < '.'
        < 0 < 1 < 2 < 3 < 4 < 5 < 6 < 7 < 8 < 9
        < a,A < á,Á < b,B < c,C < d,D < e,E < é,É < f,F < g,G < h,H
        < i,I < í,Í < j,J < k,K < l,L < m,M < n,N
        < o,O < ó,Ó < ö,Ö < ő,Ő < p,P < q,Q < r,R < s,S < t,T
        < u,U < ú,Ú < ü,Ü < ű,Ű < v,V < w,W < x,X < y,Y < z,Z
    """

    fun collator(): Collator {
        return try {
            RuleBasedCollator(HUNGARIAN_PRIMARY_RULES).apply {
                strength = Collator.TERTIARY
                decomposition = Collator.CANONICAL_DECOMPOSITION
            }
        } catch (_: ParseException) {
            Collator.getInstance(locale).apply {
                strength = Collator.TERTIARY
                decomposition = Collator.CANONICAL_DECOMPOSITION
            }
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
