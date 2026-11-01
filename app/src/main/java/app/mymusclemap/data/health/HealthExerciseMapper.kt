package app.mymusclemap.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import app.mymusclemap.domain.health.HealthExerciseClassifier
import app.mymusclemap.domain.health.HealthExerciseKind
import app.mymusclemap.domain.health.HealthExerciseSession
import java.time.ZoneId

/**
 * Maps an exercise session without copying route points, laps, or notes.
 * Distance is not on this record. Reading it would require a separate distance permission.
 */
object HealthExerciseMapper {
    fun session(record: ExerciseSessionRecord): HealthExerciseSession? {
        val kind = HealthExerciseClassifier.classify(record.exerciseType)
        if (kind == HealthExerciseKind.OTHER) return null
        if (!record.endTime.isAfter(record.startTime)) return null
        val source = record.metadata.dataOrigin.packageName.takeIf { it.isNotBlank() }
        return HealthExerciseSession(
            start = record.startTime,
            end = record.endTime,
            zone = record.startZoneOffset ?: ZoneId.systemDefault(),
            kind = kind,
            id = record.metadata.id,
            sourcePackage = source
        )
    }
}
