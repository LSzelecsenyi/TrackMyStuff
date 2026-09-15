package hu.laca.weighttracker.domain

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface DateProvider {
    fun today(): LocalDate
    fun now(): LocalDateTime
}

class SystemDateProvider(
    private val clock: Clock = Clock.systemDefaultZone()
) : DateProvider {
    override fun today(): LocalDate = LocalDate.now(clock)
    override fun now(): LocalDateTime = LocalDateTime.now(clock)
}

class FixedDateProvider(
    private val date: LocalDate,
    private val time: LocalTime = LocalTime.NOON
) : DateProvider {
    override fun today(): LocalDate = date
    override fun now(): LocalDateTime = date.atTime(time)
}
