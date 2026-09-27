package app.mymusclemap.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "body_measurements",
    indices = [
        Index(value = ["type", "date"], unique = true),
        Index(value = ["source", "externalId"], unique = true)
    ]
)
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val date: String,
    val value: Double,
    @ColumnInfo(defaultValue = "'MANUAL'") val source: String,
    val externalId: String?,
    val createdAt: Long,
    val updatedAt: Long
)
