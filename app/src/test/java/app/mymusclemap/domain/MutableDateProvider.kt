package app.mymusclemap.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MutableDateProvider(
    initialDate: LocalDate,
    initialTime: LocalTime = LocalTime.NOON
) : DateProvider {
    private val current = MutableStateFlow(initialDate.atTime(initialTime))

    override fun today(): LocalDate = current.value.toLocalDate()
    override fun now(): LocalDateTime = current.value
    override fun observeToday(): Flow<LocalDate> =
        current.map { it.toLocalDate() }.distinctUntilChanged()
    override fun observeNow(): Flow<LocalDateTime> = current.asStateFlow()

    fun setDate(date: LocalDate) {
        current.value = date.atTime(current.value.toLocalTime())
    }

    fun setNow(date: LocalDate, time: LocalTime) {
        current.value = date.atTime(time)
    }
}
