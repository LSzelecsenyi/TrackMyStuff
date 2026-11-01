package app.mymusclemap.data.health

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import app.mymusclemap.domain.health.HealthExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)

class HealthExerciseMapperTest {
    @Test
    fun runningAndCyclingVariantsKeepSourceAndAShortDuration() {
        val start = Instant.parse("2026-10-27T14:37:10Z")
        val session = HealthExerciseMapper.session(
            record(
                start = start,
                end = start.plusSeconds(30),
                type = ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
                id = "treadmill",
                source = "com.strava"
            )
        )
        assertEquals(HealthExerciseKind.RUNNING, session!!.kind)
        assertEquals("treadmill", session.id)
        assertEquals("com.strava", session.sourcePackage)
        assertEquals(start.plusSeconds(30), session.end)
        assertEquals(ZoneOffset.UTC, session.zone)
    }

    @Test
    fun stationaryCyclingIsCyclingAndOtherTypesAreDropped() {
        val start = Instant.parse("2026-10-02T05:50:00Z")
        val cycling = HealthExerciseMapper.session(
            record(
                start = start,
                end = start.plusSeconds(60),
                type = ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
                id = "bike",
                source = "com.strava"
            )
        )
        assertEquals(HealthExerciseKind.CYCLING, cycling!!.kind)
        assertNull(
            HealthExerciseMapper.session(
                record(
                    start = start,
                    end = start.plusSeconds(60),
                    type = ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
                    id = "walk",
                    source = "com.strava"
                )
            )
        )
    }

    @Test
    fun blankOriginIsOmitted() {
        val start = Instant.parse("2026-10-17T06:47:00Z")
        val session = HealthExerciseMapper.session(
            record(
                start = start,
                end = start.plusSeconds(120),
                type = ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
                id = "ride",
                source = ""
            )
        )
        assertNull(session!!.sourcePackage)
    }

    private fun record(
        start: Instant,
        end: Instant,
        type: Int,
        id: String,
        source: String
    ): ExerciseSessionRecord {
        val metadata = metadata(id, source)
        val constructor = ExerciseSessionRecord::class.java.declaredConstructors
            .filter { ctor ->
                ctor.parameterTypes.none { it.name.endsWith("DefaultConstructorMarker") }
            }
            .maxBy { it.parameterCount }
        constructor.isAccessible = true
        val args = Array<Any?>(constructor.parameterCount) { index ->
            when (index) {
                0 -> start
                1 -> ZoneOffset.UTC
                2 -> end
                3 -> ZoneOffset.UTC
                4 -> metadata
                5 -> type
                else -> {
                    val parameter = constructor.parameterTypes[index]
                    when {
                        parameter == Int::class.javaPrimitiveType -> 0
                        List::class.java.isAssignableFrom(parameter) -> emptyList<Any>()
                        else -> null
                    }
                }
            }
        }
        return constructor.newInstance(*args) as ExerciseSessionRecord
    }

    private fun metadata(id: String, source: String): Metadata {
        val constructor = Metadata::class.java.declaredConstructors.first { it.parameterCount == 7 }
        constructor.isAccessible = true
        return constructor.newInstance(
            Metadata.RECORDING_METHOD_MANUAL_ENTRY,
            id,
            DataOrigin(source),
            Instant.EPOCH,
            null,
            0L,
            null
        ) as Metadata
    }
}
