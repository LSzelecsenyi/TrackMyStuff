package hu.laca.weighttracker.data.appbackup

import hu.laca.weighttracker.data.local.ExerciseEntity
import hu.laca.weighttracker.data.local.ExerciseMuscleEntity
import hu.laca.weighttracker.data.local.ScheduledWorkoutEntity
import hu.laca.weighttracker.data.local.WeightMeasurementEntity
import hu.laca.weighttracker.data.local.WorkoutSessionEntity
import hu.laca.weighttracker.data.local.WorkoutSessionExerciseEntity
import hu.laca.weighttracker.data.local.WorkoutSessionExerciseMuscleEntity
import hu.laca.weighttracker.data.local.WorkoutSessionSetEntity
import hu.laca.weighttracker.data.local.WorkoutTemplateEntity
import hu.laca.weighttracker.data.local.WorkoutTemplateExerciseEntity
import hu.laca.weighttracker.data.local.WorkoutTemplateSetEntity
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.DateTimeException
import java.time.Instant
import java.time.format.DateTimeParseException

object AppBackupJson {
    fun encode(snapshot: AppBackupSnapshot): String {
        val root = JSONObject()
        root.put("format", AppBackupFormat.FORMAT)
        root.put("formatVersion", snapshot.formatVersion)
        root.put("schemaVersion", snapshot.schemaVersion)
        root.put("exportedAt", snapshot.exportedAt.toString())
        val source = snapshot.source
        if (source != null) {
            root.put(
                "source",
                JSONObject()
                    .put("applicationId", source.applicationId)
                    .put("versionName", source.versionName)
            )
        }
        val tables = JSONObject()
        tables.put(
            AppBackupFormat.TABLE_WEIGHT_MEASUREMENTS,
            JSONArray().also { array ->
                snapshot.tables.weightMeasurements.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("date", row.date)
                            .put("weightKg", row.weightKg)
                            .put("createdAt", row.createdAt)
                            .put("updatedAt", row.updatedAt)
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_EXERCISES,
            JSONArray().also { array ->
                snapshot.tables.exercises.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("name", row.name)
                            .put("normalizedName", row.normalizedName)
                            .put("category", row.category)
                            .put("movementPattern", row.movementPattern)
                            .put("measurementType", row.measurementType)
                            .put("resistanceBasis", row.resistanceBasis)
                            .put("weightInterpretation", row.weightInterpretation)
                            .put("notes", nullable(row.notes))
                            .put("archived", row.archived)
                            .put("createdAt", row.createdAt)
                            .put("updatedAt", row.updatedAt)
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_EXERCISE_MUSCLES,
            JSONArray().also { array ->
                snapshot.tables.exerciseMuscles
                    .sortedWith(compareBy({ it.exerciseId }, { it.muscleGroup }))
                    .forEach { row ->
                        array.put(
                            JSONObject()
                                .put("exerciseId", row.exerciseId)
                                .put("muscleGroup", row.muscleGroup)
                                .put("role", row.role)
                        )
                    }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_WORKOUT_TEMPLATES,
            JSONArray().also { array ->
                snapshot.tables.workoutTemplates.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("name", row.name)
                            .put("normalizedName", row.normalizedName)
                            .put("notes", nullable(row.notes))
                            .put("archived", row.archived)
                            .put("createdAt", row.createdAt)
                            .put("updatedAt", row.updatedAt)
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_WORKOUT_TEMPLATE_EXERCISES,
            JSONArray().also { array ->
                snapshot.tables.workoutTemplateExercises.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("templateId", row.templateId)
                            .put("exerciseId", row.exerciseId)
                            .put("position", row.position)
                            .put("notes", nullable(row.notes))
                            .put("createdAt", row.createdAt)
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_WORKOUT_TEMPLATE_SETS,
            JSONArray().also { array ->
                snapshot.tables.workoutTemplateSets.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("templateExerciseId", row.templateExerciseId)
                            .put("position", row.position)
                            .put("minReps", nullable(row.minReps))
                            .put("maxReps", nullable(row.maxReps))
                            .put("loadKind", row.loadKind)
                            .put("weightKg", nullable(row.weightKg))
                            .put("durationSeconds", nullable(row.durationSeconds))
                            .put("distanceMeters", nullable(row.distanceMeters))
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_SCHEDULED_WORKOUTS,
            JSONArray().also { array ->
                snapshot.tables.scheduledWorkouts.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("scheduledDate", row.scheduledDate)
                            .put("templateId", row.templateId)
                            .put("createdAt", row.createdAt)
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_WORKOUT_SESSIONS,
            JSONArray().also { array ->
                snapshot.tables.workoutSessions.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("templateId", nullable(row.templateId))
                            .put("templateName", row.templateName)
                            .put("status", row.status)
                            .put("workoutDate", row.workoutDate)
                            .put("startedAt", row.startedAt)
                            .put("finishedAt", nullable(row.finishedAt))
                            .put("abandonedAt", nullable(row.abandonedAt))
                            .put("notes", nullable(row.notes))
                            .put("bodyWeightKg", nullable(row.bodyWeightKg))
                            .put("bodyWeightSource", row.bodyWeightSource)
                            .put("bodyWeightSourceDate", nullable(row.bodyWeightSourceDate))
                            .put("createdAt", row.createdAt)
                            .put("updatedAt", row.updatedAt)
                            .put("activeLock", nullable(row.activeLock))
                            .put("importFingerprint", nullable(row.importFingerprint))
                            .put("scheduledWorkoutId", nullable(row.scheduledWorkoutId))
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_WORKOUT_SESSION_EXERCISES,
            JSONArray().also { array ->
                snapshot.tables.workoutSessionExercises.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("sessionId", row.sessionId)
                            .put("exerciseId", row.exerciseId)
                            .put("position", row.position)
                            .put("name", row.name)
                            .put("category", row.category)
                            .put("movementPattern", row.movementPattern)
                            .put("measurementType", row.measurementType)
                            .put("resistanceBasis", row.resistanceBasis)
                            .put("weightInterpretation", row.weightInterpretation)
                            .put("primaryMuscle", row.primaryMuscle)
                            .put("notes", nullable(row.notes))
                    )
                }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_WORKOUT_SESSION_EXERCISE_MUSCLES,
            JSONArray().also { array ->
                snapshot.tables.workoutSessionExerciseMuscles
                    .sortedWith(compareBy({ it.sessionExerciseId }, { it.muscleGroup }))
                    .forEach { row ->
                        array.put(
                            JSONObject()
                                .put("sessionExerciseId", row.sessionExerciseId)
                                .put("muscleGroup", row.muscleGroup)
                                .put("role", row.role)
                        )
                    }
            }
        )
        tables.put(
            AppBackupFormat.TABLE_WORKOUT_SESSION_SETS,
            JSONArray().also { array ->
                snapshot.tables.workoutSessionSets.sortedBy { it.id }.forEach { row ->
                    array.put(
                        JSONObject()
                            .put("id", row.id)
                            .put("sessionExerciseId", row.sessionExerciseId)
                            .put("position", row.position)
                            .put("plannedMinReps", nullable(row.plannedMinReps))
                            .put("plannedMaxReps", nullable(row.plannedMaxReps))
                            .put("plannedLoadKind", row.plannedLoadKind)
                            .put("plannedWeightKg", nullable(row.plannedWeightKg))
                            .put("plannedDurationSeconds", nullable(row.plannedDurationSeconds))
                            .put("plannedDistanceMeters", nullable(row.plannedDistanceMeters))
                            .put("actualReps", nullable(row.actualReps))
                            .put("actualLoadKind", nullable(row.actualLoadKind))
                            .put("actualWeightKg", nullable(row.actualWeightKg))
                            .put("actualDurationSeconds", nullable(row.actualDurationSeconds))
                            .put("actualDistanceMeters", nullable(row.actualDistanceMeters))
                            .put("status", row.status)
                            .put("completedAt", nullable(row.completedAt))
                            .put("addedDuringWorkout", row.addedDuringWorkout)
                    )
                }
            }
        )
        root.put("tables", tables)
        val settings = JSONObject()
        snapshot.settings.toSortedMap().forEach { (key, value) ->
            settings.put(key, value)
        }
        root.put("settings", settings)
        return root.toString(2) + "\n"
    }

    fun parse(bytes: ByteArray): AppBackupParseResult {
        if (bytes.size > AppBackupFormat.MAX_UTF8_BYTES) {
            return AppBackupParseResult.Failure(
                listOf(AppBackupError(AppBackupErrorCode.FileTooLarge, bytes.size.toString()))
            )
        }
        val text = decodeUtf8(bytes) ?: return AppBackupParseResult.Failure(
            listOf(AppBackupError(AppBackupErrorCode.InvalidJson, "utf8"))
        )
        return parse(text)
    }

    fun parse(content: String): AppBackupParseResult {
        val text = content.removePrefix("\uFEFF")
        if (text.isBlank()) {
            return AppBackupParseResult.Failure(listOf(AppBackupError(AppBackupErrorCode.EmptyFile)))
        }
        val utf8Size = text.toByteArray(StandardCharsets.UTF_8).size
        if (utf8Size > AppBackupFormat.MAX_UTF8_BYTES) {
            return AppBackupParseResult.Failure(
                listOf(AppBackupError(AppBackupErrorCode.FileTooLarge, utf8Size.toString()))
            )
        }
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return AppBackupParseResult.Failure(listOf(AppBackupError(AppBackupErrorCode.InvalidJson)))
        }
        val errors = mutableListOf<AppBackupError>()
        val format = root.optionalString("format")
        if (format == null) {
            errors += AppBackupError(AppBackupErrorCode.MissingField, "format")
        } else if (format != AppBackupFormat.FORMAT) {
            errors += AppBackupError(AppBackupErrorCode.InvalidFormat, format)
        }
        val formatVersion = root.optionalInt("formatVersion")
        if (formatVersion == null) {
            errors += AppBackupError(AppBackupErrorCode.MissingField, "formatVersion")
        } else if (formatVersion != AppBackupFormat.FORMAT_VERSION) {
            errors += AppBackupError(
                AppBackupErrorCode.UnsupportedFormatVersion,
                formatVersion.toString()
            )
        }
        val schemaVersion = root.optionalInt("schemaVersion")
        if (schemaVersion == null) {
            errors += AppBackupError(AppBackupErrorCode.MissingField, "schemaVersion")
        } else if (schemaVersion != AppBackupFormat.SCHEMA_VERSION) {
            errors += AppBackupError(
                AppBackupErrorCode.UnsupportedSchemaVersion,
                schemaVersion.toString()
            )
        }
        val exportedAtRaw = root.optionalString("exportedAt")
        val exportedAt = if (exportedAtRaw == null) {
            errors += AppBackupError(AppBackupErrorCode.MissingField, "exportedAt")
            null
        } else {
            try {
                Instant.parse(exportedAtRaw)
            } catch (_: DateTimeParseException) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "exportedAt")
                null
            } catch (_: DateTimeException) {
                errors += AppBackupError(AppBackupErrorCode.InvalidValue, "exportedAt")
                null
            }
        }
        val source = parseSource(root, errors)
        val tablesObject = root.optionalObject("tables")
        if (tablesObject == null) {
            errors += AppBackupError(AppBackupErrorCode.MissingField, "tables")
        }
        val settingsObject = root.optionalObject("settings")
        if (settingsObject == null) {
            errors += AppBackupError(AppBackupErrorCode.MissingField, "settings")
        }
        if (errors.isNotEmpty() || tablesObject == null || settingsObject == null || exportedAt == null) {
            return AppBackupParseResult.Failure(errors)
        }
        AppBackupFormat.TABLE_NAMES.forEach { name ->
            if (!tablesObject.has(name) || tablesObject.isNull(name)) {
                errors += AppBackupError(AppBackupErrorCode.MissingField, "tables.$name")
            } else if (tablesObject.opt(name) !is JSONArray) {
                errors += AppBackupError(AppBackupErrorCode.InvalidType, "tables.$name")
            }
        }
        if (errors.isNotEmpty()) {
            return AppBackupParseResult.Failure(errors)
        }
        val tables = AppBackupTables(
            weightMeasurements = parseArray(tablesObject, AppBackupFormat.TABLE_WEIGHT_MEASUREMENTS, errors) {
                parseWeight(it, errors)
            },
            exercises = parseArray(tablesObject, AppBackupFormat.TABLE_EXERCISES, errors) {
                parseExercise(it, errors)
            },
            exerciseMuscles = parseArray(tablesObject, AppBackupFormat.TABLE_EXERCISE_MUSCLES, errors) {
                parseExerciseMuscle(it, errors)
            },
            workoutTemplates = parseArray(tablesObject, AppBackupFormat.TABLE_WORKOUT_TEMPLATES, errors) {
                parseTemplate(it, errors)
            },
            workoutTemplateExercises = parseArray(
                tablesObject,
                AppBackupFormat.TABLE_WORKOUT_TEMPLATE_EXERCISES,
                errors
            ) { parseTemplateExercise(it, errors) },
            workoutTemplateSets = parseArray(
                tablesObject,
                AppBackupFormat.TABLE_WORKOUT_TEMPLATE_SETS,
                errors
            ) { parseTemplateSet(it, errors) },
            scheduledWorkouts = parseArray(
                tablesObject,
                AppBackupFormat.TABLE_SCHEDULED_WORKOUTS,
                errors
            ) { parseScheduled(it, errors) },
            workoutSessions = parseArray(tablesObject, AppBackupFormat.TABLE_WORKOUT_SESSIONS, errors) {
                parseSession(it, errors)
            },
            workoutSessionExercises = parseArray(
                tablesObject,
                AppBackupFormat.TABLE_WORKOUT_SESSION_EXERCISES,
                errors
            ) { parseSessionExercise(it, errors) },
            workoutSessionExerciseMuscles = parseArray(
                tablesObject,
                AppBackupFormat.TABLE_WORKOUT_SESSION_EXERCISE_MUSCLES,
                errors
            ) { parseSessionMuscle(it, errors) },
            workoutSessionSets = parseArray(
                tablesObject,
                AppBackupFormat.TABLE_WORKOUT_SESSION_SETS,
                errors
            ) { parseSessionSet(it, errors) }
        )
        val settings = parseSettings(settingsObject, errors)
        if (errors.isNotEmpty()) {
            return AppBackupParseResult.Failure(errors)
        }
        val snapshot = AppBackupSnapshot(
            formatVersion = formatVersion!!,
            schemaVersion = schemaVersion!!,
            exportedAt = exportedAt,
            source = source,
            tables = tables,
            settings = settings
        )
        errors += AppBackupValidator.validate(snapshot)
        return if (errors.isEmpty()) {
            AppBackupParseResult.Success(snapshot)
        } else {
            AppBackupParseResult.Failure(errors)
        }
    }

    private fun parseSource(root: JSONObject, errors: MutableList<AppBackupError>): AppBackupSource? {
        if (!root.has("source") || root.isNull("source")) {
            return null
        }
        val obj = root.opt("source") as? JSONObject
        if (obj == null) {
            errors += AppBackupError(AppBackupErrorCode.InvalidType, "source")
            return null
        }
        val applicationId = obj.optionalString("applicationId").orEmpty()
        val versionName = obj.optionalString("versionName").orEmpty()
        return AppBackupSource(applicationId, versionName)
    }

    private fun parseSettings(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (obj.isNull(key)) {
                errors += AppBackupError(AppBackupErrorCode.InvalidType, "settings.$key")
            } else {
                val value = obj.opt(key)
                if (value is String) {
                    result[key] = value
                } else {
                    errors += AppBackupError(AppBackupErrorCode.InvalidType, "settings.$key")
                }
            }
        }
        return result
    }

    private fun <T> parseArray(
        tables: JSONObject,
        name: String,
        errors: MutableList<AppBackupError>,
        parseRow: (JSONObject) -> T?
    ): List<T> {
        val array = tables.optJSONArray(name) ?: return emptyList()
        val rows = ArrayList<T>(array.length())
        for (index in 0 until array.length()) {
            val item = array.opt(index)
            if (item !is JSONObject) {
                errors += AppBackupError(AppBackupErrorCode.InvalidType, "$name[$index]")
            } else {
                parseRow(item)?.let { rows += it }
            }
        }
        return rows
    }

    private fun parseWeight(obj: JSONObject, errors: MutableList<AppBackupError>): WeightMeasurementEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val date = obj.requiredString("date", errors) ?: return null
        val weightKg = obj.requiredDouble("weightKg", errors) ?: return null
        val createdAt = obj.requiredLong("createdAt", errors) ?: return null
        val updatedAt = obj.requiredLong("updatedAt", errors) ?: return null
        return WeightMeasurementEntity(id, date, weightKg, createdAt, updatedAt)
    }

    private fun parseExercise(obj: JSONObject, errors: MutableList<AppBackupError>): ExerciseEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val name = obj.requiredString("name", errors) ?: return null
        val normalizedName = obj.requiredString("normalizedName", errors) ?: return null
        val category = obj.requiredString("category", errors) ?: return null
        val movementPattern = obj.requiredString("movementPattern", errors) ?: return null
        val measurementType = obj.requiredString("measurementType", errors) ?: return null
        val resistanceBasis = obj.requiredString("resistanceBasis", errors) ?: return null
        val weightInterpretation = obj.requiredString("weightInterpretation", errors) ?: return null
        val notes = obj.optionalNullableString("notes", errors)
        val archived = obj.requiredBoolean("archived", errors) ?: return null
        val createdAt = obj.requiredLong("createdAt", errors) ?: return null
        val updatedAt = obj.requiredLong("updatedAt", errors) ?: return null
        if (errors.any { it.detail?.startsWith("notes") == true && it.code == AppBackupErrorCode.InvalidType }) {
            return null
        }
        return ExerciseEntity(
            id,
            name,
            normalizedName,
            category,
            movementPattern,
            measurementType,
            resistanceBasis,
            weightInterpretation,
            notes,
            archived,
            createdAt,
            updatedAt
        )
    }

    private fun parseExerciseMuscle(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): ExerciseMuscleEntity? {
        val exerciseId = obj.requiredId("exerciseId", errors) ?: return null
        val muscleGroup = obj.requiredString("muscleGroup", errors) ?: return null
        val role = obj.requiredString("role", errors) ?: return null
        return ExerciseMuscleEntity(exerciseId, muscleGroup, role)
    }

    private fun parseTemplate(obj: JSONObject, errors: MutableList<AppBackupError>): WorkoutTemplateEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val name = obj.requiredString("name", errors) ?: return null
        val normalizedName = obj.requiredString("normalizedName", errors) ?: return null
        val notes = obj.optionalNullableString("notes", errors)
        val archived = obj.requiredBoolean("archived", errors) ?: return null
        val createdAt = obj.requiredLong("createdAt", errors) ?: return null
        val updatedAt = obj.requiredLong("updatedAt", errors) ?: return null
        return WorkoutTemplateEntity(id, name, normalizedName, notes, archived, createdAt, updatedAt)
    }

    private fun parseTemplateExercise(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): WorkoutTemplateExerciseEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val templateId = obj.requiredId("templateId", errors) ?: return null
        val exerciseId = obj.requiredId("exerciseId", errors) ?: return null
        val position = obj.requiredInt("position", errors) ?: return null
        val notes = obj.optionalNullableString("notes", errors)
        val createdAt = obj.requiredLong("createdAt", errors) ?: return null
        return WorkoutTemplateExerciseEntity(id, templateId, exerciseId, position, notes, createdAt)
    }

    private fun parseTemplateSet(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): WorkoutTemplateSetEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val templateExerciseId = obj.requiredId("templateExerciseId", errors) ?: return null
        val position = obj.requiredInt("position", errors) ?: return null
        val minReps = obj.optionalNullableInt("minReps", errors)
        val maxReps = obj.optionalNullableInt("maxReps", errors)
        val loadKind = obj.requiredString("loadKind", errors) ?: return null
        val weightKg = obj.optionalNullableDouble("weightKg", errors)
        val durationSeconds = obj.optionalNullableInt("durationSeconds", errors)
        val distanceMeters = obj.optionalNullableDouble("distanceMeters", errors)
        return WorkoutTemplateSetEntity(
            id,
            templateExerciseId,
            position,
            minReps,
            maxReps,
            loadKind,
            weightKg,
            durationSeconds,
            distanceMeters
        )
    }

    private fun parseScheduled(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): ScheduledWorkoutEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val scheduledDate = obj.requiredString("scheduledDate", errors) ?: return null
        val templateId = obj.requiredId("templateId", errors) ?: return null
        val createdAt = obj.requiredLong("createdAt", errors) ?: return null
        return ScheduledWorkoutEntity(id, scheduledDate, templateId, createdAt)
    }

    private fun parseSession(obj: JSONObject, errors: MutableList<AppBackupError>): WorkoutSessionEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val templateId = obj.optionalNullableId("templateId", errors)
        val templateName = obj.requiredString("templateName", errors) ?: return null
        val status = obj.requiredString("status", errors) ?: return null
        val workoutDate = obj.requiredString("workoutDate", errors) ?: return null
        val startedAt = obj.requiredLong("startedAt", errors) ?: return null
        val finishedAt = obj.optionalNullableLong("finishedAt", errors)
        val abandonedAt = obj.optionalNullableLong("abandonedAt", errors)
        val notes = obj.optionalNullableString("notes", errors)
        val bodyWeightKg = obj.optionalNullableDouble("bodyWeightKg", errors)
        val bodyWeightSource = obj.requiredString("bodyWeightSource", errors) ?: return null
        val bodyWeightSourceDate = obj.optionalNullableString("bodyWeightSourceDate", errors)
        val createdAt = obj.requiredLong("createdAt", errors) ?: return null
        val updatedAt = obj.requiredLong("updatedAt", errors) ?: return null
        val activeLock = obj.optionalNullableInt("activeLock", errors)
        val importFingerprint = obj.optionalNullableString("importFingerprint", errors)
        val scheduledWorkoutId = obj.optionalNullableId("scheduledWorkoutId", errors)
        return WorkoutSessionEntity(
            id,
            templateId,
            templateName,
            status,
            workoutDate,
            startedAt,
            finishedAt,
            abandonedAt,
            notes,
            bodyWeightKg,
            bodyWeightSource,
            bodyWeightSourceDate,
            createdAt,
            updatedAt,
            activeLock,
            importFingerprint,
            scheduledWorkoutId
        )
    }

    private fun parseSessionExercise(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): WorkoutSessionExerciseEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val sessionId = obj.requiredId("sessionId", errors) ?: return null
        val exerciseId = obj.requiredId("exerciseId", errors) ?: return null
        val position = obj.requiredInt("position", errors) ?: return null
        val name = obj.requiredString("name", errors) ?: return null
        val category = obj.requiredString("category", errors) ?: return null
        val movementPattern = obj.requiredString("movementPattern", errors) ?: return null
        val measurementType = obj.requiredString("measurementType", errors) ?: return null
        val resistanceBasis = obj.requiredString("resistanceBasis", errors) ?: return null
        val weightInterpretation = obj.requiredString("weightInterpretation", errors) ?: return null
        val primaryMuscle = obj.requiredString("primaryMuscle", errors) ?: return null
        val notes = obj.optionalNullableString("notes", errors)
        return WorkoutSessionExerciseEntity(
            id,
            sessionId,
            exerciseId,
            position,
            name,
            category,
            movementPattern,
            measurementType,
            resistanceBasis,
            weightInterpretation,
            primaryMuscle,
            notes
        )
    }

    private fun parseSessionMuscle(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): WorkoutSessionExerciseMuscleEntity? {
        val sessionExerciseId = obj.requiredId("sessionExerciseId", errors) ?: return null
        val muscleGroup = obj.requiredString("muscleGroup", errors) ?: return null
        val role = obj.requiredString("role", errors) ?: return null
        return WorkoutSessionExerciseMuscleEntity(sessionExerciseId, muscleGroup, role)
    }

    private fun parseSessionSet(
        obj: JSONObject,
        errors: MutableList<AppBackupError>
    ): WorkoutSessionSetEntity? {
        val id = obj.requiredId("id", errors) ?: return null
        val sessionExerciseId = obj.requiredId("sessionExerciseId", errors) ?: return null
        val position = obj.requiredInt("position", errors) ?: return null
        val plannedMinReps = obj.optionalNullableInt("plannedMinReps", errors)
        val plannedMaxReps = obj.optionalNullableInt("plannedMaxReps", errors)
        val plannedLoadKind = obj.requiredString("plannedLoadKind", errors) ?: return null
        val plannedWeightKg = obj.optionalNullableDouble("plannedWeightKg", errors)
        val plannedDurationSeconds = obj.optionalNullableInt("plannedDurationSeconds", errors)
        val plannedDistanceMeters = obj.optionalNullableDouble("plannedDistanceMeters", errors)
        val actualReps = obj.optionalNullableInt("actualReps", errors)
        val actualLoadKind = obj.optionalNullableString("actualLoadKind", errors)
        val actualWeightKg = obj.optionalNullableDouble("actualWeightKg", errors)
        val actualDurationSeconds = obj.optionalNullableInt("actualDurationSeconds", errors)
        val actualDistanceMeters = obj.optionalNullableDouble("actualDistanceMeters", errors)
        val status = obj.requiredString("status", errors) ?: return null
        val completedAt = obj.optionalNullableLong("completedAt", errors)
        val addedDuringWorkout = obj.requiredBoolean("addedDuringWorkout", errors) ?: return null
        return WorkoutSessionSetEntity(
            id,
            sessionExerciseId,
            position,
            plannedMinReps,
            plannedMaxReps,
            plannedLoadKind,
            plannedWeightKg,
            plannedDurationSeconds,
            plannedDistanceMeters,
            actualReps,
            actualLoadKind,
            actualWeightKg,
            actualDurationSeconds,
            actualDistanceMeters,
            status,
            completedAt,
            addedDuringWorkout
        )
    }

    private fun nullable(value: Any?): Any {
        return value ?: JSONObject.NULL
    }

    private fun decodeUtf8(bytes: ByteArray): String? {
        return try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}

private fun JSONObject.optionalString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return opt(key) as? String
}

private fun JSONObject.optionalInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return (opt(key) as? Number)?.toInt()
}

private fun JSONObject.optionalObject(key: String): JSONObject? {
    if (!has(key) || isNull(key)) return null
    return opt(key) as? JSONObject
}

private fun JSONObject.requiredString(key: String, errors: MutableList<AppBackupError>): String? {
    if (!has(key) || isNull(key)) {
        errors += AppBackupError(AppBackupErrorCode.MissingField, key)
        return null
    }
    val value = opt(key)
    if (value !is String) {
        errors += AppBackupError(AppBackupErrorCode.InvalidType, key)
        return null
    }
    return value
}

private fun JSONObject.requiredBoolean(key: String, errors: MutableList<AppBackupError>): Boolean? {
    if (!has(key) || isNull(key)) {
        errors += AppBackupError(AppBackupErrorCode.MissingField, key)
        return null
    }
    val value = opt(key)
    if (value is Boolean) {
        return value
    }
    errors += AppBackupError(AppBackupErrorCode.InvalidType, key)
    return null
}

private fun JSONObject.requiredId(key: String, errors: MutableList<AppBackupError>): Long? {
    val value = requiredLong(key, errors) ?: return null
    if (value <= 0L) {
        errors += AppBackupError(AppBackupErrorCode.InvalidValue, key)
        return null
    }
    return value
}

private fun JSONObject.requiredLong(key: String, errors: MutableList<AppBackupError>): Long? {
    if (!has(key) || isNull(key)) {
        errors += AppBackupError(AppBackupErrorCode.MissingField, key)
        return null
    }
    val value = opt(key)
    if (value is Number) {
        return value.toLong()
    }
    errors += AppBackupError(AppBackupErrorCode.InvalidType, key)
    return null
}

private fun JSONObject.requiredInt(key: String, errors: MutableList<AppBackupError>): Int? {
    val value = requiredLong(key, errors) ?: return null
    return value.toInt()
}

private fun JSONObject.requiredDouble(key: String, errors: MutableList<AppBackupError>): Double? {
    if (!has(key) || isNull(key)) {
        errors += AppBackupError(AppBackupErrorCode.MissingField, key)
        return null
    }
    val value = opt(key)
    if (value is Number) {
        return value.toDouble()
    }
    errors += AppBackupError(AppBackupErrorCode.InvalidType, key)
    return null
}

private fun JSONObject.optionalNullableString(key: String, errors: MutableList<AppBackupError>): String? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    if (value is String) return value
    errors += AppBackupError(AppBackupErrorCode.InvalidType, key)
    return null
}

private fun JSONObject.optionalNullableLong(key: String, errors: MutableList<AppBackupError>): Long? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    if (value is Number) return value.toLong()
    errors += AppBackupError(AppBackupErrorCode.InvalidType, key)
    return null
}

private fun JSONObject.optionalNullableInt(key: String, errors: MutableList<AppBackupError>): Int? {
    return optionalNullableLong(key, errors)?.toInt()
}

private fun JSONObject.optionalNullableDouble(key: String, errors: MutableList<AppBackupError>): Double? {
    if (!has(key) || isNull(key)) return null
    val value = opt(key)
    if (value is Number) return value.toDouble()
    errors += AppBackupError(AppBackupErrorCode.InvalidType, key)
    return null
}

private fun JSONObject.optionalNullableId(key: String, errors: MutableList<AppBackupError>): Long? {
    val value = optionalNullableLong(key, errors) ?: return null
    if (value <= 0L) {
        errors += AppBackupError(AppBackupErrorCode.InvalidValue, key)
        return null
    }
    return value
}
