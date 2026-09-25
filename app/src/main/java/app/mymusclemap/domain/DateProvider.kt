package app.mymusclemap.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

interface DateProvider {
    fun today(): LocalDate
    fun now(): LocalDateTime
    fun observeToday(): Flow<LocalDate>
}

class SystemDateProvider(
    private val clock: Clock = Clock.systemDefaultZone()
) : DateProvider {
    override fun today(): LocalDate = LocalDate.now(clock)
    override fun now(): LocalDateTime = LocalDateTime.now(clock)
    override fun observeToday(): Flow<LocalDate> = flow {
        while (true) {
            val current = today()
            emit(current)
            val waitMillis = Duration.between(now(), current.plusDays(1).atStartOfDay()).toMillis()
            delay(if (waitMillis <= 0L) 1_000L else waitMillis)
        }
    }.distinctUntilChanged()
}

class FixedDateProvider(
    private val date: LocalDate,
    private val time: LocalTime = LocalTime.NOON
) : DateProvider {
    override fun today(): LocalDate = date
    override fun now(): LocalDateTime = date.atTime(time)
    override fun observeToday(): Flow<LocalDate> = flowOf(date)
}
