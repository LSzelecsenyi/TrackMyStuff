package hu.laca.weighttracker.domain.workoutimport

import hu.laca.weighttracker.domain.exercise.ExerciseNaming
import hu.laca.weighttracker.domain.workout.PlannedLoadKind
import hu.laca.weighttracker.domain.workout.SessionSetStatus
import hu.laca.weighttracker.domain.workout.TemplateNaming
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

object WorkoutImportCsv {
    const val HEADER =
        "format_version,workout_id,workout_name,workout_date,started_at,finished_at,notes," +
            "body_weight_kg,exercise_index,exercise_name,set_index,set_status,reps," +
            "duration_seconds,distance,distance_unit,load_kind,weight_kg"

    val COLUMNS: List<String> = HEADER.split(',')

    private val isoDate = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val isoDateTime = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
        .withResolverStyle(ResolverStyle.STRICT)

    fun parse(bytes: ByteArray, today: LocalDate): WorkoutImportParseResult {
        if (bytes.size > WorkoutImportLimits.MAX_UTF8_BYTES) {
            return failure(WorkoutImportErrorCode.FileTooLarge, detail = bytes.size.toString())
        }
        val decoded = decodeUtf8(bytes) ?: return failure(WorkoutImportErrorCode.InvalidUtf8)
        return parse(decoded, today)
    }

    fun parse(content: String, today: LocalDate): WorkoutImportParseResult {
        val text = content.removePrefix("\uFEFF")
        if (text.isEmpty() || text.isBlank()) {
            return failure(WorkoutImportErrorCode.EmptyFile, rowNumber = 1)
        }
        val utf8Size = text.toByteArray(StandardCharsets.UTF_8).size
        if (utf8Size > WorkoutImportLimits.MAX_UTF8_BYTES) {
            return failure(WorkoutImportErrorCode.FileTooLarge, detail = utf8Size.toString())
        }
        val records = when (val parsed = Rfc4180Csv.parse(text)) {
            is Rfc4180Csv.Result.Failure -> return WorkoutImportParseResult.Failure(listOf(parsed.error))
            is Rfc4180Csv.Result.Success -> parsed.records
        }
        if (records.isEmpty()) {
            return failure(WorkoutImportErrorCode.EmptyFile, rowNumber = 1)
        }
        val headerErrors = headerErrors(records.first())
        if (headerErrors.isNotEmpty()) {
            return WorkoutImportParseResult.Failure(headerErrors)
        }
        val data = records.drop(1)
        if (data.isEmpty()) {
            return failure(WorkoutImportErrorCode.HeaderOnly, rowNumber = 1)
        }
        if (data.size > WorkoutImportLimits.MAX_ROWS) {
            return failure(
                WorkoutImportErrorCode.TooManyRows,
                rowNumber = data.first().recordNumber,
                detail = data.size.toString()
            )
        }
        val errors = ArrayList<WorkoutImportError>()
        val rows = ArrayList<WorkoutImportRow>()
        val seenRaw = HashMap<List<String>, Int>()
        data.forEach { record ->
            if (record.fields.size > WorkoutImportLimits.COLUMN_COUNT) {
                errors += error(
                    record.recordNumber,
                    null,
                    WorkoutImportErrorCode.ExtraColumns,
                    record.fields.size.toString()
                )
                return@forEach
            }
            if (record.fields.size < COLUMNS.indexOf("set_index") + 1) {
                errors += error(
                    record.recordNumber,
                    null,
                    WorkoutImportErrorCode.MissingColumns,
                    record.fields.size.toString()
                )
                return@forEach
            }
            val fields = padded(record.fields)
            val previous = seenRaw.put(fields, record.recordNumber)
            if (previous != null) {
                errors += error(
                    record.recordNumber,
                    null,
                    WorkoutImportErrorCode.DuplicateRecord,
                    "duplicate of row $previous"
                )
            }
            val parsedRow = parseRow(record.recordNumber, fields, today, errors)
            if (parsedRow != null) {
                rows += parsedRow
            }
        }
        if (rows.isNotEmpty()) {
            when (val grouped = WorkoutImportValidator.group(rows)) {
                is WorkoutImportParseResult.Failure -> errors += grouped.errors
                is WorkoutImportParseResult.Success -> {
                    if (errors.isEmpty()) {
                        return grouped
                    }
                }
            }
        }
        if (errors.isEmpty()) {
            return failure(WorkoutImportErrorCode.EmptyWorkout)
        }
        return WorkoutImportParseResult.Failure(errors.distinct())
    }

    private fun padded(fields: List<String>): List<String> {
        if (fields.size >= WorkoutImportLimits.COLUMN_COUNT) {
            return fields
        }
        return fields + List(WorkoutImportLimits.COLUMN_COUNT - fields.size) { "" }
    }

    private fun headerErrors(record: Rfc4180Csv.Record): List<WorkoutImportError> {
        val fields = record.fields.map { it.trim() }
        if (fields.isEmpty() || fields.all { it.isEmpty() }) {
            return listOf(error(record.recordNumber, null, WorkoutImportErrorCode.MissingHeader))
        }
        if (fields.size != COLUMNS.size) {
            val code = if (fields.size > COLUMNS.size) {
                WorkoutImportErrorCode.ExtraColumns
            } else {
                WorkoutImportErrorCode.MissingColumns
            }
            return listOf(error(record.recordNumber, null, code, fields.joinToString(",")))
        }
        if (fields.toSet().size != fields.size) {
            return listOf(error(record.recordNumber, null, WorkoutImportErrorCode.DuplicateHeader))
        }
        if (fields != COLUMNS) {
            return listOf(
                error(record.recordNumber, null, WorkoutImportErrorCode.InvalidHeader, fields.joinToString(","))
            )
        }
        return emptyList()
    }

    private fun parseRow(
        rowNumber: Int,
        fields: List<String>,
        today: LocalDate,
        errors: MutableList<WorkoutImportError>
    ): WorkoutImportRow? {
        val before = errors.size
        fun field(index: Int): String = fields[index]
        fun err(column: String, code: WorkoutImportErrorCode, detail: String? = null) {
            errors += error(rowNumber, column, code, detail)
        }

        val versionRaw = field(0).trim()
        if (versionRaw.isEmpty()) {
            err("format_version", WorkoutImportErrorCode.MissingRequired)
        } else if (versionRaw != WorkoutImportLimits.FORMAT_VERSION.toString()) {
            err("format_version", WorkoutImportErrorCode.UnsupportedVersion, versionRaw)
        }

        val workoutId = field(1).trim()
        when {
            workoutId.isEmpty() -> err("workout_id", WorkoutImportErrorCode.MissingRequired)
            workoutId.length > WorkoutImportLimits.MAX_WORKOUT_ID_LENGTH ->
                err("workout_id", WorkoutImportErrorCode.FieldTooLong)
        }

        val workoutNameRaw = field(2)
        val workoutName = TemplateNaming.displayName(workoutNameRaw)
        when {
            workoutName.isEmpty() -> err("workout_name", WorkoutImportErrorCode.MissingRequired)
            workoutName.length > WorkoutImportLimits.MAX_WORKOUT_NAME_LENGTH ->
                err("workout_name", WorkoutImportErrorCode.FieldTooLong)
        }

        val date = parseDate(field(3), today, rowNumber, errors)
        val startedAt = parseTimestamp(field(4), "started_at", rowNumber, errors)
        val finishedAt = parseTimestamp(field(5), "finished_at", rowNumber, errors)
        if (date != null && startedAt != null && startedAt.toLocalDate() != date) {
            err("started_at", WorkoutImportErrorCode.TimestampDateMismatch)
        }
        if (date != null && finishedAt != null && finishedAt.toLocalDate() != date) {
            err("finished_at", WorkoutImportErrorCode.TimestampDateMismatch)
        }
        if (startedAt != null && finishedAt != null && finishedAt.isBefore(startedAt)) {
            err("finished_at", WorkoutImportErrorCode.FinishBeforeStart)
        }

        val notes = normalizeNotes(field(6), rowNumber, errors)
        val bodyWeight = parseBodyWeight(field(7), rowNumber, errors)

        val exerciseIndex = parseRequiredIndex(
            field(8),
            "exercise_index",
            WorkoutImportLimits.MAX_ROWS,
            rowNumber,
            errors
        )
        val exerciseName = ExerciseNaming.displayName(field(9))
        when {
            exerciseName.isEmpty() -> err("exercise_name", WorkoutImportErrorCode.MissingRequired)
            exerciseName.length > WorkoutImportLimits.MAX_EXERCISE_NAME_LENGTH ->
                err("exercise_name", WorkoutImportErrorCode.FieldTooLong)
        }
        val setIndex = parseRequiredIndex(
            field(10),
            "set_index",
            WorkoutImportLimits.MAX_ROWS,
            rowNumber,
            errors
        )
        val status = parseStatus(field(11), rowNumber, errors)
        val reps = parseOptionalPositiveInt(field(12), "reps", WorkoutImportLimits.MAX_REPS, rowNumber, errors)
        val duration = parseOptionalPositiveInt(
            field(13),
            "duration_seconds",
            WorkoutImportLimits.MAX_DURATION_SECONDS,
            rowNumber,
            errors
        )
        val distanceInput = parseOptionalDecimal(
            field(14),
            "distance",
            WorkoutImportLimits.DISTANCE_DECIMALS,
            WorkoutImportLimits.MAX_DISTANCE_METERS,
            rowNumber,
            errors
        )
        val unitRaw = field(15).trim()
        val distanceMeters = normalizeDistance(distanceInput, unitRaw, rowNumber, errors)
        val loadKind = parseLoadKind(field(16), rowNumber, errors)
        val weightKg = parseOptionalDecimal(
            field(17),
            "weight_kg",
            WorkoutImportLimits.LOAD_WEIGHT_DECIMALS,
            WorkoutImportLimits.MAX_LOAD_WEIGHT_KG,
            rowNumber,
            errors
        )
        validateSkippedAndLoad(status, reps, duration, distanceMeters, loadKind, weightKg, rowNumber, errors)

        if (errors.size != before) {
            return null
        }
        return WorkoutImportRow(
            sourceRowNumber = rowNumber,
            workoutId = workoutId,
            workoutName = workoutName,
            normalizedWorkoutName = TemplateNaming.normalize(workoutName),
            workoutDate = date!!,
            startedAt = startedAt!!,
            finishedAt = finishedAt!!,
            notes = notes,
            bodyWeightKg = bodyWeight,
            exerciseIndex = exerciseIndex!!,
            exerciseName = exerciseName,
            normalizedExerciseName = ExerciseNaming.normalize(exerciseName),
            setIndex = setIndex!!,
            status = status ?: SessionSetStatus.COMPLETED,
            reps = reps,
            durationSeconds = duration,
            distanceMeters = distanceMeters,
            loadKind = loadKind,
            weightKg = weightKg,
            rawFields = fields
        )
    }

    private fun parseDate(
        raw: String,
        today: LocalDate,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): LocalDate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            errors += error(rowNumber, "workout_date", WorkoutImportErrorCode.MissingRequired)
            return null
        }
        val date = try {
            LocalDate.parse(trimmed, isoDate)
        } catch (_: DateTimeParseException) {
            errors += error(rowNumber, "workout_date", WorkoutImportErrorCode.InvalidDate, trimmed)
            return null
        } catch (_: DateTimeException) {
            errors += error(rowNumber, "workout_date", WorkoutImportErrorCode.InvalidDate, trimmed)
            return null
        }
        if (date.isAfter(today)) {
            errors += error(rowNumber, "workout_date", WorkoutImportErrorCode.FutureDate, trimmed)
            return null
        }
        return date
    }

    private fun parseTimestamp(
        raw: String,
        field: String,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): LocalDateTime? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            errors += error(rowNumber, field, WorkoutImportErrorCode.MissingRequired)
            return null
        }
        return try {
            LocalDateTime.parse(trimmed, isoDateTime)
        } catch (_: DateTimeParseException) {
            errors += error(rowNumber, field, WorkoutImportErrorCode.InvalidTimestamp, trimmed)
            null
        } catch (_: DateTimeException) {
            errors += error(rowNumber, field, WorkoutImportErrorCode.InvalidTimestamp, trimmed)
            null
        }
    }

    private fun normalizeNotes(
        raw: String,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): String? {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (normalized.length > WorkoutImportLimits.MAX_NOTES_LENGTH) {
            errors += error(rowNumber, "notes", WorkoutImportErrorCode.FieldTooLong)
            return null
        }
        return normalized.ifEmpty { null }
    }

    private fun parseBodyWeight(
        raw: String,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): BigDecimal? {
        return when (
            val parsed = WorkoutImportNumbers.parseDecimal(
                raw = raw,
                maxDecimals = WorkoutImportLimits.BODY_WEIGHT_DECIMALS,
                minExclusiveZero = false,
                minInclusive = WorkoutImportLimits.MIN_BODY_WEIGHT_KG,
                maxInclusive = WorkoutImportLimits.MAX_BODY_WEIGHT_KG
            )
        ) {
            WorkoutImportNumbers.DecimalParse.Empty -> null
            is WorkoutImportNumbers.DecimalParse.Valid -> parsed.value
            is WorkoutImportNumbers.DecimalParse.Invalid -> {
                errors += error(rowNumber, "body_weight_kg", parsed.code, raw.trim())
                null
            }
        }
    }

    private fun parseRequiredIndex(
        raw: String,
        field: String,
        max: Int,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): Int? {
        return when (
            val parsed = WorkoutImportNumbers.parsePositiveInt(raw, max)
        ) {
            WorkoutImportNumbers.IntParse.Empty -> {
                errors += error(rowNumber, field, WorkoutImportErrorCode.MissingRequired)
                null
            }
            is WorkoutImportNumbers.IntParse.Valid -> parsed.value
            is WorkoutImportNumbers.IntParse.Invalid -> {
                errors += error(rowNumber, field, parsed.code, raw.trim())
                null
            }
        }
    }

    private fun parseOptionalPositiveInt(
        raw: String,
        field: String,
        max: Int,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): Int? {
        return when (val parsed = WorkoutImportNumbers.parsePositiveInt(raw, max)) {
            WorkoutImportNumbers.IntParse.Empty -> null
            is WorkoutImportNumbers.IntParse.Valid -> parsed.value
            is WorkoutImportNumbers.IntParse.Invalid -> {
                errors += error(rowNumber, field, parsed.code, raw.trim())
                null
            }
        }
    }

    private fun parseOptionalDecimal(
        raw: String,
        field: String,
        maxDecimals: Int,
        maxInclusive: BigDecimal,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): BigDecimal? {
        return when (
            val parsed = WorkoutImportNumbers.parseDecimal(
                raw = raw,
                maxDecimals = maxDecimals,
                minExclusiveZero = true,
                maxInclusive = maxInclusive
            )
        ) {
            WorkoutImportNumbers.DecimalParse.Empty -> null
            is WorkoutImportNumbers.DecimalParse.Valid -> parsed.value
            is WorkoutImportNumbers.DecimalParse.Invalid -> {
                errors += error(rowNumber, field, parsed.code, raw.trim())
                null
            }
        }
    }

    private fun normalizeDistance(
        distance: BigDecimal?,
        unitRaw: String,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): BigDecimal? {
        if (distance == null) {
            if (unitRaw.isNotEmpty()) {
                errors += error(rowNumber, "distance_unit", WorkoutImportErrorCode.DistanceUnitWithoutDistance, unitRaw)
            }
            return null
        }
        if (unitRaw.isEmpty()) {
            errors += error(rowNumber, "distance_unit", WorkoutImportErrorCode.MissingDistanceUnit)
            return null
        }
        val meters = when (unitRaw) {
            "m" -> distance
            "km" -> distance.multiply(BigDecimal(1000))
            else -> {
                errors += error(rowNumber, "distance_unit", WorkoutImportErrorCode.InvalidDistanceUnit, unitRaw)
                return null
            }
        }
        if (meters.compareTo(WorkoutImportLimits.MAX_DISTANCE_METERS) > 0) {
            errors += error(rowNumber, "distance", WorkoutImportErrorCode.OutOfRange, meters.toPlainString())
            return null
        }
        return meters
    }

    private fun parseStatus(
        raw: String,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): SessionSetStatus? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return SessionSetStatus.COMPLETED
        }
        return when (trimmed) {
            SessionSetStatus.COMPLETED.name -> SessionSetStatus.COMPLETED
            SessionSetStatus.SKIPPED.name -> SessionSetStatus.SKIPPED
            else -> {
                errors += error(rowNumber, "set_status", WorkoutImportErrorCode.InvalidStatus, trimmed)
                null
            }
        }
    }

    private fun parseLoadKind(
        raw: String,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ): PlannedLoadKind? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        return try {
            PlannedLoadKind.valueOf(trimmed)
        } catch (_: IllegalArgumentException) {
            errors += error(rowNumber, "load_kind", WorkoutImportErrorCode.InvalidLoadKind, trimmed)
            null
        }
    }

    private fun validateSkippedAndLoad(
        status: SessionSetStatus?,
        reps: Int?,
        duration: Int?,
        distanceMeters: BigDecimal?,
        loadKind: PlannedLoadKind?,
        weightKg: BigDecimal?,
        rowNumber: Int,
        errors: MutableList<WorkoutImportError>
    ) {
        if (status == SessionSetStatus.SKIPPED) {
            if (reps != null) {
                errors += error(rowNumber, "reps", WorkoutImportErrorCode.SkippedHasActuals)
            }
            if (duration != null) {
                errors += error(rowNumber, "duration_seconds", WorkoutImportErrorCode.SkippedHasActuals)
            }
            if (distanceMeters != null) {
                errors += error(rowNumber, "distance", WorkoutImportErrorCode.SkippedHasActuals)
            }
            if (weightKg != null) {
                errors += error(rowNumber, "weight_kg", WorkoutImportErrorCode.SkippedHasActuals)
            }
            return
        }
        if (status == SessionSetStatus.COMPLETED && loadKind == null) {
            errors += error(rowNumber, "load_kind", WorkoutImportErrorCode.CompletedMissingLoadKind)
            return
        }
        val kind = loadKind ?: return
        val needsWeight = kind == PlannedLoadKind.ADDED_WEIGHT ||
            kind == PlannedLoadKind.ASSISTANCE ||
            kind == PlannedLoadKind.EXTERNAL_WEIGHT
        val forbidsWeight = kind == PlannedLoadKind.BODYWEIGHT_ONLY || kind == PlannedLoadKind.NONE
        if (needsWeight && weightKg == null) {
            errors += error(rowNumber, "weight_kg", WorkoutImportErrorCode.WeightIncompatibleWithLoad, kind.name)
        }
        if (forbidsWeight && weightKg != null) {
            errors += error(rowNumber, "weight_kg", WorkoutImportErrorCode.WeightIncompatibleWithLoad, kind.name)
        }
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        val start = if (
            bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            3
        } else {
            0
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, start, bytes.size - start)).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun failure(
        code: WorkoutImportErrorCode,
        rowNumber: Int? = null,
        field: String? = null,
        detail: String? = null
    ): WorkoutImportParseResult.Failure {
        return WorkoutImportParseResult.Failure(listOf(error(rowNumber, field, code, detail)))
    }

    private fun error(
        rowNumber: Int?,
        field: String?,
        code: WorkoutImportErrorCode,
        detail: String? = null
    ): WorkoutImportError {
        return WorkoutImportError(rowNumber = rowNumber, field = field, code = code, detail = detail)
    }
}
