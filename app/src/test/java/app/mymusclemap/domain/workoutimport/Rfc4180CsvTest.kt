package app.mymusclemap.domain.workoutimport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Rfc4180CsvTest {
    @Test
    fun parsesQuotedCommaEscapedQuoteAndMultilineField() {
        val csv = "a,b\n\"x,y\",\"he said \"\"hi\"\"\",\"line1\nline2\"\n"
        val parsed = Rfc4180Csv.parse(csv) as Rfc4180Csv.Result.Success
        assertEquals(2, parsed.records.size)
        assertEquals(listOf("a", "b"), parsed.records[0].fields)
        assertEquals(listOf("x,y", "he said \"hi\"", "line1\nline2"), parsed.records[1].fields)
        assertEquals(2, parsed.records[1].startLine)
    }

    @Test
    fun acceptsCrLfAndBareCrRecordSeparators() {
        val crlf = Rfc4180Csv.parse("a,b\r\n1,2\r\n") as Rfc4180Csv.Result.Success
        assertEquals(listOf("1", "2"), crlf.records[1].fields)
        val cr = Rfc4180Csv.parse("a,b\r1,2") as Rfc4180Csv.Result.Success
        assertEquals(2, cr.records.size)
    }

    @Test
    fun rejectsUnterminatedQuote() {
        val failed = Rfc4180Csv.parse("a,b\n\"abc") as Rfc4180Csv.Result.Failure
        assertEquals(WorkoutImportErrorCode.MalformedQuote, failed.error.code)
    }

    @Test
    fun rejectsDanglingQuoteInUnquotedField() {
        val failed = Rfc4180Csv.parse("a,b\n1\"2,3") as Rfc4180Csv.Result.Failure
        assertEquals(WorkoutImportErrorCode.MalformedQuote, failed.error.code)
    }
}
