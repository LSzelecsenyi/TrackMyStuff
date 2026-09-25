package app.mymusclemap.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MutableDateProvider(
    initialDate: LocalDate,
    initialTime: LocalTime = LocalTime.NOON
) : DateProvider {
    private val dates = MutableStateFlow(initialDate)

    @Volatile
    private var time: LocalTime = initialTime

    override fun today(): LocalDate = dates.value
    override fun now(): LocalDateTime = dates.value.atTime(time)
    override fun observeToday(): Flow<LocalDate> = dates.asStateFlow()

    fun setDate(date: LocalDate) {
        dates.value = date
    }

    fun setNow(date: LocalDate, time: LocalTime) {
        this.time = time
        dates.value = date
    }
}
