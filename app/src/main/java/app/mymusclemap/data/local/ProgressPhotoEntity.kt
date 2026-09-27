package app.mymusclemap.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "progress_photos",
    indices = [
        Index(value = ["fileName"], unique = true),
        Index(value = ["date", "id"])
    ]
)
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val fileName: String,
    val createdAt: Long,
    val updatedAt: Long
)
