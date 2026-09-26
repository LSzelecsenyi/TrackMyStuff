package app.mymusclemap.data.appbackup

import app.mymusclemap.data.local.ExerciseMuscleEntity
import app.mymusclemap.data.local.WeightMeasurementEntity
import app.mymusclemap.data.local.WorkoutSessionEntity
import app.mymusclemap.domain.theme.AppearanceCodec
import app.mymusclemap.domain.theme.AppearanceSettings
import app.mymusclemap.domain.workout.BodyWeightSource
import app.mymusclemap.domain.workout.SessionStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class AppBackupJsonTest {
    @Test
    fun emptySnapshotRoundTripsWithFormatMetadata() {
        val snapshot = emptySnapshot()
        val json = AppBackupJson.encode(snapshot)
        val parsed = AppBackupJson.parse(json) as AppBackupParseResult.Success
        val root = JSONObject(json)
        assertEquals(AppBackupFormat.FORMAT, root.getString("format"))
        assertEquals(AppBackupFormat.FORMAT_VERSION, root.getInt("formatVersion"))
        assertEquals(AppBackupFormat.SCHEMA_VERSION, root.getInt("schemaVersion"))
        assertEquals(snapshot.formatVersion, parsed.snapshot.formatVersion)
        assertEquals(snapshot.schemaVersion, parsed.snapshot.schemaVersion)
        assertEquals(snapshot.exportedAt, parsed.snapshot.exportedAt)
        assertEquals(snapshot.source, parsed.snapshot.source)
        assertEquals(snapshot.tables, parsed.snapshot.tables)
        assertEquals(snapshot.settings, parsed.snapshot.settings)
    }

    @Test
    fun bomPrefixedJsonParses() {
        val json = "\uFEFF" + AppBackupJson.encode(emptySnapshot())
        assertTrue(AppBackupJson.parse(json) is AppBackupParseResult.Success)
    }

    @Test
    fun missingSourceIsAccepted() {
        val root = JSONObject(AppBackupJson.encode(emptySnapshot()))
        root.remove("source")
        val parsed = AppBackupJson.parse(root.toString()) as AppBackupParseResult.Success
        assertNull(parsed.snapshot.source)
    }

    @Test
    fun sourceApplicationIdIsNotARestoreGate() {
        val snapshot = emptySnapshot().copy(
            source = AppBackupSource("hu.laca.weighttracker.debug", "1.0-debug")
        )
        val parsed = AppBackupJson.parse(AppBackupJson.encode(snapshot)) as AppBackupParseResult.Success
        assertEquals("hu.laca.weighttracker.debug", parsed.snapshot.source?.applicationId)
    }

    @Test
    fun emptyFileIsRejected() {
        assertHasCode(AppBackupJson.parse(""), AppBackupErrorCode.EmptyFile)
        assertHasCode(AppBackupJson.parse("   \n"), AppBackupErrorCode.EmptyFile)
    }

    @Test
    fun corruptJsonIsRejected() {
        assertHasCode(AppBackupJson.parse("{"), AppBackupErrorCode.InvalidJson)
        assertHasCode(AppBackupJson.parse("not-json"), AppBackupErrorCode.InvalidJson)
    }

    @Test
    fun invalidUtf8BytesAreRejected() {
        assertHasCode(
            AppBackupJson.parse(byteArrayOf(0xC3.toByte(), 0x28)),
            AppBackupErrorCode.InvalidJson
        )
    }

    @Test
    fun oversizedFileIsRejected() {
        val bytes = ByteArray(AppBackupFormat.MAX_UTF8_BYTES + 1)
        assertHasCode(AppBackupJson.parse(bytes), AppBackupErrorCode.FileTooLarge)
    }

    @Test
    fun unsupportedFormatAndSchemaAreRejected() {
        val root = JSONObject(AppBackupJson.encode(emptySnapshot()))
        root.put("format", "other-backup")
        assertHasCode(AppBackupJson.parse(root.toString()), AppBackupErrorCode.InvalidFormat)
        root.put("format", AppBackupFormat.FORMAT)
        root.put("formatVersion", 2)
        assertHasCode(AppBackupJson.parse(root.toString()), AppBackupErrorCode.UnsupportedFormatVersion)
        root.put("formatVersion", AppBackupFormat.FORMAT_VERSION)
        root.put("schemaVersion", 5)
        assertHasCode(AppBackupJson.parse(root.toString()), AppBackupErrorCode.UnsupportedSchemaVersion)
        root.put("schemaVersion", 6)
        assertTrue(AppBackupJson.parse(root.toString()) is AppBackupParseResult.Success)
    }

    @Test
    fun missingTableAndInvalidTypesAreRejected() {
        val missing = JSONObject(AppBackupJson.encode(emptySnapshot()))
        missing.getJSONObject("tables").remove(AppBackupFormat.TABLE_EXERCISES)
        assertHasCode(AppBackupJson.parse(missing.toString()), AppBackupErrorCode.MissingField)
        val wrongType = JSONObject(AppBackupJson.encode(emptySnapshot()))
        wrongType.getJSONObject("tables").put(AppBackupFormat.TABLE_EXERCISES, "nope")
        assertHasCode(AppBackupJson.parse(wrongType.toString()), AppBackupErrorCode.InvalidType)
    }

    @Test
    fun duplicateKeysAndMissingRelationsAreRejected() {
        val duplicateDates = emptySnapshot().copy(
            tables = emptyTables().copy(
                weightMeasurements = listOf(
                    WeightMeasurementEntity(1, "2026-01-01", 80.0, 1, 1),
                    WeightMeasurementEntity(2, "2026-01-01", 81.0, 1, 1)
                )
            )
        )
        assertHasCode(
            AppBackupJson.parse(AppBackupJson.encode(duplicateDates)),
            AppBackupErrorCode.DuplicateKey
        )
        val missingRelation = emptySnapshot().copy(
            tables = emptyTables().copy(
                exerciseMuscles = listOf(
                    ExerciseMuscleEntity(exerciseId = 99, muscleGroup = "CHEST", role = "PRIMARY")
                )
            )
        )
        assertHasCode(
            AppBackupJson.parse(AppBackupJson.encode(missingRelation)),
            AppBackupErrorCode.MissingRelation
        )
    }

    @Test
    fun inProgressSessionMustHaveSingleActiveLock() {
        val unlocked = emptySnapshot().copy(
            tables = emptyTables().copy(
                workoutSessions = listOf(session(id = 1, status = SessionStatus.IN_PROGRESS, activeLock = null))
            )
        )
        assertHasCode(
            AppBackupJson.parse(AppBackupJson.encode(unlocked)),
            AppBackupErrorCode.InvalidValue
        )
        val twoLocks = emptySnapshot().copy(
            tables = emptyTables().copy(
                workoutSessions = listOf(
                    session(id = 1, status = SessionStatus.IN_PROGRESS, activeLock = 1),
                    session(id = 2, status = SessionStatus.IN_PROGRESS, activeLock = 1)
                )
            )
        )
        assertHasCode(
            AppBackupJson.parse(AppBackupJson.encode(twoLocks)),
            AppBackupErrorCode.DuplicateKey
        )
    }

    private fun assertHasCode(result: AppBackupParseResult, code: AppBackupErrorCode) {
        val failure = result as AppBackupParseResult.Failure
        assertTrue(
            "expected $code in ${failure.errors}",
            failure.errors.any { it.code == code }
        )
    }

    private fun session(
        id: Long,
        status: SessionStatus,
        activeLock: Int?
    ): WorkoutSessionEntity {
        return WorkoutSessionEntity(
            id = id,
            templateId = null,
            templateName = "Push",
            status = status.name,
            workoutDate = "2026-09-20",
            startedAt = 1L,
            finishedAt = null,
            abandonedAt = null,
            notes = null,
            bodyWeightKg = null,
            bodyWeightSource = BodyWeightSource.UNKNOWN.name,
            bodyWeightSourceDate = null,
            createdAt = 1L,
            updatedAt = 1L,
            activeLock = activeLock
        )
    }
}

internal fun emptySnapshot(): AppBackupSnapshot {
    return AppBackupSnapshot(
        formatVersion = AppBackupFormat.FORMAT_VERSION,
        schemaVersion = AppBackupFormat.SCHEMA_VERSION,
        exportedAt = Instant.parse("2026-09-25T09:00:00Z"),
        source = AppBackupSource("hu.laca.weighttracker.debug", "1.0-debug"),
        tables = emptyTables(),
        settings = AppearanceCodec.encode(AppearanceSettings.Default)
    )
}

internal fun emptyTables(): AppBackupTables {
    return AppBackupTables(
        weightMeasurements = emptyList(),
        exercises = emptyList(),
        exerciseMuscles = emptyList(),
        workoutTemplates = emptyList(),
        workoutTemplateExercises = emptyList(),
        workoutTemplateSets = emptyList(),
        scheduledWorkouts = emptyList(),
        workoutSessions = emptyList(),
        workoutSessionExercises = emptyList(),
        workoutSessionExerciseMuscles = emptyList(),
        workoutSessionSets = emptyList()
    )
}
