package app.mymusclemap.domain.workoutimport

/**
 * Minimal RFC 4180 reader: comma-separated records, quoted fields, doubled quotes,
 * and CRLF / LF / CR record separators. Quoted fields may contain commas and newlines.
 */
object Rfc4180Csv {
    data class Record(
        val recordNumber: Int,
        val startLine: Int,
        val fields: List<String>
    )

    sealed class Result {
        data class Success(val records: List<Record>) : Result()
        data class Failure(val error: WorkoutImportError) : Result()
    }

    fun parse(text: String, maxFieldLength: Int = WorkoutImportLimits.MAX_FIELD_CHARS): Result {
        if (text.isEmpty()) {
            return Result.Failure(
                WorkoutImportError(rowNumber = 1, field = null, code = WorkoutImportErrorCode.EmptyFile)
            )
        }
        val records = ArrayList<Record>()
        val fields = ArrayList<String>()
        val field = StringBuilder()
        var recordNumber = 1
        var line = 1
        var recordStartLine = 1
        var index = 0
        var state = State.Field
        fun fail(code: WorkoutImportErrorCode): Result {
            return Result.Failure(
                WorkoutImportError(rowNumber = recordNumber, field = null, code = code)
            )
        }
        fun endField(): WorkoutImportErrorCode? {
            if (field.length > maxFieldLength) {
                return WorkoutImportErrorCode.FieldTooLong
            }
            fields += field.toString()
            field.setLength(0)
            return null
        }
        fun endRecord(): WorkoutImportErrorCode? {
            val overflow = endField()
            if (overflow != null) {
                return overflow
            }
            records += Record(
                recordNumber = recordNumber,
                startLine = recordStartLine,
                fields = fields.toList()
            )
            fields.clear()
            recordNumber += 1
            recordStartLine = line
            state = State.Field
            return null
        }
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            when (state) {
                State.Field -> when (char) {
                    '"' -> {
                        if (field.isNotEmpty()) {
                            return fail(WorkoutImportErrorCode.MalformedQuote)
                        }
                        state = State.Quoted
                    }
                    ',' -> {
                        endField()?.let { return fail(it) }
                    }
                    '\n' -> {
                        line += 1
                        endRecord()?.let { return fail(it) }
                    }
                    '\r' -> {
                        line += 1
                        if (next == '\n') {
                            index += 1
                        }
                        endRecord()?.let { return fail(it) }
                    }
                    else -> field.append(char)
                }
                State.Quoted -> when (char) {
                    '"' -> state = State.QuoteInQuoted
                    '\n' -> {
                        line += 1
                        field.append('\n')
                    }
                    '\r' -> {
                        line += 1
                        field.append('\n')
                        if (next == '\n') {
                            index += 1
                        }
                    }
                    else -> field.append(char)
                }
                State.QuoteInQuoted -> when (char) {
                    '"' -> {
                        field.append('"')
                        state = State.Quoted
                    }
                    ',' -> {
                        endField()?.let { return fail(it) }
                        state = State.Field
                    }
                    '\n' -> {
                        line += 1
                        endRecord()?.let { return fail(it) }
                    }
                    '\r' -> {
                        line += 1
                        if (next == '\n') {
                            index += 1
                        }
                        endRecord()?.let { return fail(it) }
                    }
                    else -> return fail(WorkoutImportErrorCode.MalformedQuote)
                }
            }
            index += 1
        }
        return when (state) {
            State.Quoted -> fail(WorkoutImportErrorCode.MalformedQuote)
            State.Field, State.QuoteInQuoted -> {
                val lastBlank = fields.isEmpty() && field.isEmpty()
                if (lastBlank && records.isNotEmpty()) {
                    Result.Success(records)
                } else {
                    endRecord()?.let { return fail(it) }
                    Result.Success(records)
                }
            }
        }
    }

    private enum class State {
        Field,
        Quoted,
        QuoteInQuoted
    }
}
