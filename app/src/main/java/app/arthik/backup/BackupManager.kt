package app.arthik.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import app.arthik.AppContainer
import app.arthik.data.db.AccountEntity
import app.arthik.data.db.ActivityEntity
import app.arthik.data.db.BudgetEntity
import app.arthik.data.db.CategoryEntity
import app.arthik.data.db.FxRateEntity
import app.arthik.data.db.MerchantRuleEntity
import app.arthik.data.db.OwnIdentityEntity
import app.arthik.data.db.RecurringEntity
import app.arthik.data.db.SmsEntity
import app.arthik.data.db.TransactionEntity
import app.arthik.data.db.clearAll
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val formatVersion: Int = 1,
    val createdAt: String,
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val sms: List<SmsEntity>,
    val categories: List<CategoryEntity>,
    val rules: List<MerchantRuleEntity>,
    val own: List<OwnIdentityEntity>,
    val budgets: List<BudgetEntity>,
    val recurring: List<RecurringEntity>,
    val activities: List<ActivityEntity>,
    val fx: List<FxRateEntity>,
)

class WrongPasswordException : Exception("Wrong password or damaged file")

/**
 * Backup file: "ARTHIKBK" | version byte | 16-byte salt | 12-byte IV | AES-256-GCM(gzip(JSON)).
 * Key = PBKDF2-HMAC-SHA256(password, salt, 210 000 iterations). A forgotten password cannot be
 * recovered.
 */
class BackupManager(private val context: Context, private val c: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(uri: Uri, password: CharArray) {
        val m = c.db.maintenance()
        val data = BackupData(
            createdAt = java.time.LocalDateTime.now().toString(),
            accounts = c.db.accounts().all(), transactions = c.db.transactions().all(), sms = c.db.sms().all(),
            categories = c.db.categories().all(), rules = m.rules(), own = m.own(), budgets = m.budgets(),
            recurring = m.recurring(), activities = c.db.activities().all(), fx = c.db.fx().all(),
        )
        val plain = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.encodeToString(BackupData.serializer(), data).toByteArray()) } }.toByteArray()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt))
        val body = cipher.doFinal(plain)
        context.contentResolver.openOutputStream(uri, "wt")!!.use { out ->
            out.write(MAGIC); out.write(VERSION.toInt()); out.write(salt); out.write(cipher.iv); out.write(body)
        }
        c.settings.setLastBackup(System.currentTimeMillis())
    }

    /** Replaces all data with the backup's contents. */
    suspend fun restore(uri: Uri, password: CharArray) {
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val data = decrypt(bytes, password)
        val m = c.db.maintenance()
        c.db.withTransaction {
            m.clearAll()
            m.upsertCategories(data.categories)
            m.insertAccounts(data.accounts)
            m.insertTransactions(data.transactions)
            m.insertSms(data.sms)
            m.insertRules(data.rules)
            m.insertOwn(data.own)
            m.insertBudgets(data.budgets)
            m.insertRecurring(data.recurring)
            m.insertActivities(data.activities)
            m.insertFx(data.fx)
        }
    }

    fun decrypt(bytes: ByteArray, password: CharArray): BackupData {
        if (bytes.size < 37 || !bytes.copyOfRange(0, 8).contentEquals(MAGIC)) throw WrongPasswordException()
        val salt = bytes.copyOfRange(9, 25)
        val iv = bytes.copyOfRange(25, 37)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        val plain = runCatching { cipher.doFinal(bytes.copyOfRange(37, bytes.size)) }.getOrElse { throw WrongPasswordException() }
        val text = GZIPInputStream(plain.inputStream()).use { it.readBytes().decodeToString() }
        return json.decodeFromString(BackupData.serializer(), text)
    }

    /**
     * Weekly automatic backup into the folder the user picked. The password for automatic backups
     * is held in the Keystore-protected [AutoBackupSecret].
     */
    suspend fun maybeAutoBackup() {
        val s = c.settings.current()
        val folder = s.autoBackupFolder ?: return
        if (!s.autoBackupEnabled) return
        if (System.currentTimeMillis() - s.lastBackupAt < 7L * 24 * 3600 * 1000) return
        val password = AutoBackupSecret(context).get() ?: return
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(folder)) ?: return
        val file = dir.createFile("application/octet-stream", "arthik-${LocalDate.now()}.arthikbk") ?: return
        export(file.uri, password)
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, 210_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(key, "AES")
    }

    companion object {
        private val MAGIC = "ARTHIKBK".toByteArray()
        private const val VERSION: Byte = 1
    }
}

/** Auto-backup password, wrapped by a Keystore key (same scheme as the database key). */
class AutoBackupSecret(private val context: Context) {
    private val file = java.io.File(context.noBackupFilesDir, "autobackup.key")
    private val alias = "arthik_autobackup"

    fun set(password: CharArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
        file.writeBytes(cipher.iv + cipher.doFinal(String(password).toByteArray()))
    }

    fun get(): CharArray? = runCatching {
        val bytes = file.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString().toCharArray()
    }.getOrNull()

    private fun key(create: Boolean): javax.crypto.SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? java.security.KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        check(create) { "No auto-backup key" }
        val gen = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore")
        gen.init(
            android.security.keystore.KeyGenParameterSpec.Builder(alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").setKeySize(256).build()
        )
        return gen.generateKey()
    }
}
