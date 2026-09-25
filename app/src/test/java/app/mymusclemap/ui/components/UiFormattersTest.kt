package app.mymusclemap.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class UiFormattersTest {
    @Test
    fun inclusiveDateRangeKeepsSameMonthCompact() {
        assertEquals(
            "Mar 5–11",
            UiFormatters.inclusiveDateRange(
                LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 3, 11)
            )
        )
        assertEquals(
            "Sep 11–17",
            UiFormatters.inclusiveDateRange(
                LocalDate.of(2026, 9, 11),
                LocalDate.of(2026, 9, 17)
            )
        )
    }

    @Test
    fun inclusiveDateRangeShowsBothMonthsWhenWindowCrosses() {
        assertEquals(
            "Aug 29–Sep 4",
            UiFormatters.inclusiveDateRange(
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 9, 4)
            )
        )
    }
}
