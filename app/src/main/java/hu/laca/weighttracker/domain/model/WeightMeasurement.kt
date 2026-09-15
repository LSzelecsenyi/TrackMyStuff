package hu.laca.weighttracker.domain.model

import java.time.LocalDate

data class WeightMeasurement(
    val id: Long,
    val date: LocalDate,
    val weightKg: Double,
    val createdAt: Long,
    val updatedAt: Long
)
