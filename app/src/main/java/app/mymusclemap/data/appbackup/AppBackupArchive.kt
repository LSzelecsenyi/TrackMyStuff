package app.mymusclemap.data.appbackup

import android.graphics.BitmapFactory
import app.mymusclemap.data.progress.ProgressPhotoStore
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.DateTimeException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal data class PhotoRestoreMarker(
    val previousFileNames: Set<String>,
    val stagedFileNames: Set<String>
)

internal object AppBackupArchive {
    fun export(
        snapshot: AppBackupSnapshot,
        photos: List<java.io.File>,
        output: OutputStream
    ) {
        val data = AppBackupJson.encode(snapshot).toByteArray(Charsets.UTF_8)
        val manifest = manifestJson(snapshot).toByteArray(Charsets.UTF_8)
        ZipOutputStream(output).use { zip ->
            writeEntry(zip, AppBackupFormat.ARCHIVE_MANIFEST, manifest)
            writeEntry(zip, AppBackupFormat.ARCHIVE_DATA, data)
            snapshot.tables.progressPhotos.forEach { photo ->
                val file = photos.first { it.name == photo.fileName }
                zip.putNextEntry(ZipEntry(AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY + photo.fileName))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun inspect(input: InputStream, staging: File): AppBackupParseResult {
        staging.deleteRecursively()
        staging.mkdirs()
        val buffered = BufferedInputStream(input)
        val manifestBytes = HashMap<String, ByteArray>()
        val photoNames = mutableSetOf<String>()
        var entryCount = 0
        var photoBytes = 0L
        try {
            ZipInputStream(buffered).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount += 1
                    if (entryCount > AppBackupFormat.MAX_ARCHIVE_ENTRIES) {
                        return reject(staging, AppBackupErrorCode.FileTooLarge, "entries")
                    }
                    val name = entry.name
                    if (!isSafeEntryName(name) || entry.isDirectory && name != AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY) {
                        return reject(staging, AppBackupErrorCode.CorruptBackup, "entry")
                    }
                    if (name == AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY && entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    if (name == AppBackupFormat.ARCHIVE_MANIFEST || name == AppBackupFormat.ARCHIVE_DATA) {
                        if (manifestBytes.containsKey(name)) {
                            return reject(staging, AppBackupErrorCode.DuplicateKey, name)
                        }
                        val bytes = readCapped(zip, AppBackupFormat.MAX_UTF8_BYTES)
                            ?: return reject(staging, AppBackupErrorCode.FileTooLarge, name)
                        manifestBytes[name] = bytes
                    } else if (name.startsWith(AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY)) {
                        val fileName = name.removePrefix(AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY)
                        if (!ProgressPhotoStore.isPortableFileName(fileName) || fileName in photoNames) {
                            return reject(staging, AppBackupErrorCode.CorruptBackup, "photo")
                        }
                        val written = writePhoto(zip, File(staging, fileName))
                        if (written < 0L) {
                            return reject(staging, AppBackupErrorCode.FileTooLarge, "photo")
                        }
                        photoBytes += written
                        if (photoBytes > AppBackupFormat.MAX_PROGRESS_PHOTO_BYTES_TOTAL) {
                            return reject(staging, AppBackupErrorCode.FileTooLarge, "photos")
                        }
                        if (!isSupportedJpeg(File(staging, fileName))) {
                            return reject(staging, AppBackupErrorCode.InvalidPhoto, "photo")
                        }
                        photoNames += fileName
                    } else {
                        return reject(staging, AppBackupErrorCode.CorruptBackup, "entry")
                    }
                    zip.closeEntry()
                }
            }
        } catch (_: Exception) {
            return reject(staging, AppBackupErrorCode.CorruptBackup, "zip")
        }
        if (entryCount == 0) {
            return reject(staging, AppBackupErrorCode.CorruptBackup, "zip")
        }
        val manifest = manifestBytes[AppBackupFormat.ARCHIVE_MANIFEST]
            ?: return reject(staging, AppBackupErrorCode.IncompleteBackup, "manifest")
        val data = manifestBytes[AppBackupFormat.ARCHIVE_DATA]
            ?: return reject(staging, AppBackupErrorCode.IncompleteBackup, "data")
        val declared = parseManifest(manifest) ?: return reject(
            staging,
            AppBackupErrorCode.CorruptBackup,
            "manifest"
        )
        if (declared.containerVersion != AppBackupFormat.CONTAINER_VERSION) {
            return reject(
                staging,
                AppBackupErrorCode.UnsupportedContainerVersion,
                declared.containerVersion.toString()
            )
        }
        if (declared.dataSchemaVersion != AppBackupFormat.ARCHIVE_DATA_SCHEMA_VERSION) {
            return reject(
                staging,
                AppBackupErrorCode.UnsupportedSchemaVersion,
                declared.dataSchemaVersion.toString()
            )
        }
        val parsed = AppBackupJson.parse(
            data.toString(Charsets.UTF_8),
            maxSchemaVersion = AppBackupFormat.ARCHIVE_DATA_SCHEMA_VERSION
        )
        if (parsed is AppBackupParseResult.Failure) {
            staging.deleteRecursively()
            return parsed
        }
        val snapshot = (parsed as AppBackupParseResult.Success).snapshot
        if (snapshot.schemaVersion != declared.dataSchemaVersion ||
            !snapshot.tables.replacesProgressPhotos ||
            snapshot.tables.progressPhotos.size != declared.progressPhotoCount
        ) {
            return reject(staging, AppBackupErrorCode.IncompleteBackup, "manifest")
        }
        val expected = snapshot.tables.progressPhotos.map { it.fileName }.toSet()
        if (expected != photoNames) {
            return reject(staging, AppBackupErrorCode.IncompleteBackup, "photos")
        }
        return parsed
    }

    fun readMarker(file: File): PhotoRestoreMarker? {
        if (!file.isFile) return null
        return try {
            val root = JSONObject(file.readText())
            PhotoRestoreMarker(
                previousFileNames = root.getJSONArray("previousFileNames").toStringSet(),
                stagedFileNames = root.getJSONArray("stagedFileNames").toStringSet()
            )
        } catch (_: Exception) {
            null
        }
    }

    fun writeMarker(file: File, marker: PhotoRestoreMarker) {
        val root = JSONObject()
            .put("previousFileNames", org.json.JSONArray(marker.previousFileNames.sorted()))
            .put("stagedFileNames", org.json.JSONArray(marker.stagedFileNames.sorted()))
        val temporary = File(file.parentFile, file.name + ".part")
        temporary.writeText(root.toString())
        if (!temporary.renameTo(file)) {
            file.writeText(root.toString())
            temporary.delete()
        }
    }

    private fun manifestJson(snapshot: AppBackupSnapshot): String {
        return JSONObject()
            .put("format", AppBackupFormat.FORMAT)
            .put("containerVersion", AppBackupFormat.CONTAINER_VERSION)
            .put("dataSchemaVersion", snapshot.schemaVersion)
            .put("createdAt", snapshot.exportedAt.toString())
            .put("appVersion", snapshot.source?.versionName.orEmpty())
            .put("progressPhotoCount", snapshot.tables.progressPhotos.size)
            .toString()
    }

    private fun parseManifest(bytes: ByteArray): ManifestFields? {
        val text = bytes.toString(Charsets.UTF_8)
        val root = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return null
        }
        if (root.optString("format") != AppBackupFormat.FORMAT) return null
        val createdAt = try {
            Instant.parse(root.optString("createdAt"))
        } catch (_: DateTimeParseException) {
            return null
        } catch (_: DateTimeException) {
            return null
        }
        if (!root.has("containerVersion") || !root.has("dataSchemaVersion") || !root.has("progressPhotoCount")) {
            return null
        }
        return ManifestFields(
            containerVersion = root.optInt("containerVersion"),
            dataSchemaVersion = root.optInt("dataSchemaVersion"),
            createdAt = createdAt,
            progressPhotoCount = root.optInt("progressPhotoCount")
        )
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writePhoto(zip: ZipInputStream, target: File): Long {
        target.parentFile?.mkdirs()
        var written = 0L
        target.outputStream().use { output ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = zip.read(buffer)
                if (count < 0) break
                written += count
                if (written > AppBackupFormat.MAX_PROGRESS_PHOTO_BYTES) {
                    output.close()
                    target.delete()
                    return -1L
                }
                output.write(buffer, 0, count)
            }
        }
        return written
    }

    private fun readCapped(zip: ZipInputStream, maxBytes: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var written = 0
        while (true) {
            val count = zip.read(buffer)
            if (count < 0) break
            written += count
            if (written > maxBytes) return null
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun isSupportedJpeg(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        val header = ByteArray(3)
        val read = file.inputStream().use { it.read(header) }
        if (read < 3 || header[0] != 0xFF.toByte() || header[1] != 0xD8.toByte() || header[2] != 0xFF.toByte()) {
            return false
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        return bounds.outWidth in 1..AppBackupFormat.MAX_PROGRESS_PHOTO_EDGE &&
            bounds.outHeight in 1..AppBackupFormat.MAX_PROGRESS_PHOTO_EDGE
    }

    private fun isSafeEntryName(name: String): Boolean {
        if (name.isEmpty() || name.length > 180) return false
        if (name.startsWith("/") || name.startsWith("\\") || name.contains("..") || name.contains('\\')) {
            return false
        }
        if (name.any { it.code < 32 }) return false
        return name == AppBackupFormat.ARCHIVE_MANIFEST ||
            name == AppBackupFormat.ARCHIVE_DATA ||
            name == AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY ||
            (name.startsWith(AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY) &&
                !name.removePrefix(AppBackupFormat.ARCHIVE_PHOTO_DIRECTORY).contains('/'))
    }

    private fun reject(staging: File, code: AppBackupErrorCode, detail: String): AppBackupParseResult {
        staging.deleteRecursively()
        return AppBackupParseResult.Failure(listOf(AppBackupError(code, detail)))
    }

    private fun org.json.JSONArray.toStringSet(): Set<String> {
        return buildSet {
            for (index in 0 until length()) {
                add(getString(index))
            }
        }
    }

    private data class ManifestFields(
        val containerVersion: Int,
        val dataSchemaVersion: Int,
        val createdAt: Instant,
        val progressPhotoCount: Int
    )
}
