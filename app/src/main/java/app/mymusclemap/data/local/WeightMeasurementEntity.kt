package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weight_measurements",
    indices = [Index(value = ["date"], unique = true)]
)
data class WeightMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val createdAt: Long,
    val updatedAt: Long
)
