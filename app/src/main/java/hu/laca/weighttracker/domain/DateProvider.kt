package hu.laca.weighttracker.domain

import java.time.Clock
import java.time.LocalDate

fun interface DateProvider {
    fun today(): LocalDate
}

class SystemDateProvider(
    private val clock: Clock = Clock.systemDefaultZone()
) : DateProvider {
    override fun today(): LocalDate = LocalDate.now(clock)
}
