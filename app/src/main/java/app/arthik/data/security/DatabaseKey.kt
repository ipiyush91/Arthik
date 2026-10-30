package app.arthik.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The database passphrase is 32 random bytes, stored only wrapped (AES-GCM) by a non-exportable
 * Android Keystore key. If the Keystore key is lost (factory reset, new device) the database
 * cannot be opened and the app offers "Restore from backup".
 */
class DatabaseKey(private val context: Context) {
    private val file = File(context.noBackupFilesDir, "db.key")

    class KeyLostException : Exception("Database key is unavailable")

    fun passphrase(): ByteArray {
        if (file.exists()) {
            val bytes = file.readBytes()
            val iv = bytes.copyOfRange(0, 12)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val key = existingKey() ?: throw KeyLostException()
            return runCatching {
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                cipher.doFinal(bytes.copyOfRange(12, bytes.size))
            }.getOrElse { throw KeyLostException() }
        }
        if (context.getDatabasePath(app.arthik.data.db.ArthikDatabase.NAME).exists()) throw KeyLostException()
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: createKey())
        file.writeBytes(cipher.iv + cipher.doFinal(passphrase))
        return passphrase
    }

    /** Used after "Restore from backup" when the old database is unreadable. */
    fun reset() {
        file.delete()
        context.deleteDatabase(app.arthik.data.db.ArthikDatabase.NAME)
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    private fun existingKey(): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun createKey(): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "arthik_db_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
