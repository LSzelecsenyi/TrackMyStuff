package app.mymusclemap.data.auth

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

interface StrictSessionStore {
    fun read(): StoredStrictSession?
    fun write(session: StoredStrictSession)
    fun clear()
}

interface SessionSealer {
    fun seal(plaintext: ByteArray): ByteArray
    fun open(sealed: ByteArray): ByteArray
}

/**
 * Ciphertext lives in [Context.getNoBackupFilesDir], which Android excludes from Auto Backup.
 * Backup rules also exclude `no_backup`. The portable app-backup JSON does not include this file.
 */
class EncryptedFileStrictSessionStore(
    private val file: File,
    private val sealer: SessionSealer,
    var onCleared: (() -> Unit)? = null,
    var onWritten: (() -> Unit)? = null
) : StrictSessionStore {
    override fun read(): StoredStrictSession? {
        if (!file.isFile) {
            return null
        }
        return try {
            decode(sealer.open(file.readBytes()))
        } catch (_: Exception) {
            clear()
            null
        }
    }

    override fun write(session: StoredStrictSession) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeBytes(sealer.seal(encode(session)))
        if (!temporary.renameTo(file)) {
            file.writeBytes(temporary.readBytes())
            temporary.delete()
        }
        onWritten?.invoke()
    }

    override fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
        onCleared?.invoke()
    }

    companion object {
        const val FILE_NAME = "strict_session.bin"

        fun create(context: Context): EncryptedFileStrictSessionStore {
            return EncryptedFileStrictSessionStore(
                file = File(context.applicationContext.noBackupFilesDir, FILE_NAME),
                sealer = AndroidKeystoreSessionSealer()
            )
        }

        private fun encode(session: StoredStrictSession): ByteArray {
            return JSONObject()
                .put("accessToken", session.accessToken.value)
                .put("expiresAt", session.expiresAt)
                .put("userId", session.userId)
                .toString()
                .toByteArray(Charsets.UTF_8)
        }

        private fun decode(plaintext: ByteArray): StoredStrictSession {
            val json = JSONObject(plaintext.toString(Charsets.UTF_8))
            val token = json.getString("accessToken")
            val expiresAt = json.getString("expiresAt")
            val userId = json.getString("userId")
            require(isCredentialSafe(token))
            require(expiresAt.isNotBlank())
            require(isCredentialSafe(userId))
            return StoredStrictSession(StrictBearerToken(token), expiresAt, userId)
        }
    }
}

internal fun isCredentialSafe(value: String): Boolean {
    return value.isNotBlank() && value.none { it == '\r' || it == '\n' || it == '\u0000' }
}

class AndroidKeystoreSessionSealer : SessionSealer {
    override fun seal(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)
        val out = ByteArray(1 + iv.size + encrypted.size)
        out[0] = VERSION
        iv.copyInto(out, destinationOffset = 1)
        encrypted.copyInto(out, destinationOffset = 1 + iv.size)
        return out
    }

    override fun open(sealed: ByteArray): ByteArray {
        require(sealed.size > 1 + GCM_IV_LENGTH + GCM_TAG_LENGTH_BYTES)
        require(sealed[0] == VERSION)
        val iv = sealed.copyOfRange(1, 1 + GCM_IV_LENGTH)
        val encrypted = sealed.copyOfRange(1 + GCM_IV_LENGTH, sealed.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "strict_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VERSION: Byte = 1
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BYTES = 16
    }
}
