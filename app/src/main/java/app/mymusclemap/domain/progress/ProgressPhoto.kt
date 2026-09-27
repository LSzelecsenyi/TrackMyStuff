package app.mymusclemap.domain.progress

import java.time.LocalDate

data class ProgressPhoto(
    val id: Long,
    val date: LocalDate,
    val fileName: String,
    val createdAt: Long,
    val updatedAt: Long
)

sealed class ProgressPhotoImportResult {
    data class Saved(val photo: ProgressPhoto) : ProgressPhotoImportResult()
    data object Unreadable : ProgressPhotoImportResult()
    data object FutureDate : ProgressPhotoImportResult()
}
