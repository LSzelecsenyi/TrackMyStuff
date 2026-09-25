package app.mymusclemap.data.workoutimport

class FakeWorkoutImportFileReader(
    var result: WorkoutImportFileReadResult = WorkoutImportFileReadResult.Unreadable
) : WorkoutImportFileReader {
    val uris = mutableListOf<String>()
    var readCount = 0

    override suspend fun read(uri: String, maxBytes: Int): WorkoutImportFileReadResult {
        uris += uri
        readCount += 1
        return result
    }
}
