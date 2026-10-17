package app.mymusclemap

import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

/**
 * Pins [Locale.getDefault] to English for tests that assert English number and
 * date patterns. Production formatters follow the display locale, so these
 * tests must not depend on the machine locale. The previous locale is restored
 * afterwards.
 */
class EnglishDisplayLocaleRule : TestWatcher() {
    private var previous: Locale = Locale.getDefault()

    override fun starting(description: Description) {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    override fun finished(description: Description) {
        Locale.setDefault(previous)
    }
}
