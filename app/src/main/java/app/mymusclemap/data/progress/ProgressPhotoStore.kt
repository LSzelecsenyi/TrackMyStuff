package app.mymusclemap.data.progress

import java.io.File
import java.util.UUID

class ProgressPhotoStore(
    private val directory: File
) {
    fun prepare() {
        directory.mkdirs()
    }

    fun newFileName(): String = "${UUID.randomUUID()}.jpg"

    fun resolve(fileName: String): File? {
        if (!FILE_NAME.matches(fileName)) return null
        val file = File(directory, fileName)
        val parent = file.parentFile?.canonicalFile
        if (parent != directory.canonicalFile) return null
        return file
    }

    fun exists(fileName: String): Boolean = resolve(fileName)?.isFile == true

    fun delete(fileName: String) {
        resolve(fileName)?.delete()
    }

    fun deleteOwned(name: String) {
        if (!OWNED_NAME.matches(name)) return
        val file = File(directory, name)
        if (file.parentFile?.canonicalFile != directory.canonicalFile) return
        file.delete()
    }

    fun ownedFileNames(): Set<String> {
        prepare()
        return directory.list()
            ?.filter { OWNED_NAME.matches(it) }
            ?.toSet()
            .orEmpty()
    }

    fun stagingDirectory(): File {
        val parent = directory.parentFile ?: directory
        return File(parent, "${directory.name}_restore_staging")
    }

    fun restoreMarker(): File {
        val parent = directory.parentFile ?: directory
        return File(parent, "${directory.name}_restore_marker.json")
    }

    fun copyFrom(fileName: String, source: File): Boolean {
        val target = resolve(fileName) ?: return false
        if (source.canonicalFile == target.canonicalFile) return true
        prepare()
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.isFile
    }

    fun deleteJpegsExcept(keep: Set<String>) {
        ownedFileNames()
            .filter { it !in keep }
            .forEach(::deleteOwned)
    }

    companion object {
        const val DIRECTORY_NAME = "progress_photos"
        private val FILE_NAME = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jpg"
        )
        private val OWNED_NAME = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jpg(\\.part)?"
        )

        fun isPortableFileName(name: String): Boolean = FILE_NAME.matches(name)
    }
}
