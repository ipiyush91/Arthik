package app.arthik.sms

import android.content.Context
import android.provider.Telephony
import app.arthik.data.repo.RawSms
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Reads the SMS inbox in pages so a 12-month scan never loads everything into memory. */
class SmsReader(private val context: Context) {
    fun count(sinceMillis: Long): Int = context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms._ID), "${Telephony.Sms.DATE} >= ?",
        arrayOf(sinceMillis.toString()), null,
    )?.use { it.count } ?: 0

    suspend fun forEachBatch(sinceMillis: Long, batchSize: Int = 500, block: suspend (List<RawSms>) -> Unit) {
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()), "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val batch = ArrayList<RawSms>(batchSize)
            while (c.moveToNext()) {
                val sender = c.getString(iAddr) ?: continue
                val body = c.getString(iBody) ?: continue
                if (!SmsPrefilter.worthParsing(sender, body)) continue
                val at = toLocal(c.getLong(iDate))
                batch += RawSms(SmsIds.of(sender, body, at), sender, body, at)
                if (batch.size == batchSize) { block(batch.toList()); batch.clear() }
            }
            if (batch.isNotEmpty()) block(batch)
        }
    }

    companion object {
        fun toLocal(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    }
}

/**
 * Stable id shared by the inbox scan and the live receiver, so the same message is stored once.
 * Identical body from the same sender on the same day is the same message (bodies carry refs).
 */
object SmsIds {
    fun of(sender: String, body: String, at: LocalDateTime): String {
        val core = app.arthik.core.parse.Senders.core(sender)
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest("$core|${at.toLocalDate()}|${body.trim()}".toByteArray())
        return "h:" + digest.joinToString("") { "%02x".format(it) }
    }
}

/** Cheap check before any regex: needs a digit and a money/bank word. Personal chats are skipped. */
object SmsPrefilter {
    private val money = Regex("""(?i)\b(?:rs|inr)\b|₹|debit|credit|a/c|acct|card|upi|balance|bal\b|txn|spent|paid|received|due""")
    fun worthParsing(sender: String, body: String): Boolean = body.any(Char::isDigit) && money.containsMatchIn(body) &&
        sender.any { it.isLetter() } // DLT headers contain letters; plain numbers are filtered (phishing guard also in core)
}
