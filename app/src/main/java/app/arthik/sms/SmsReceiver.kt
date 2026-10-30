package app.arthik.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.arthik.work.IngestWorker

/** New SMS: no parsing here, just hand the text to a small expedited job (spec 11.4). */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // Multi-part messages arrive as several PDUs from the same sender.
        parts.groupBy { it.displayOriginatingAddress }.forEach { (sender, pdus) ->
            val body = pdus.joinToString("") { it.displayMessageBody.orEmpty() }
            val ts = pdus.first().timestampMillis
            if (sender != null && SmsPrefilter.worthParsing(sender, body)) IngestWorker.enqueue(context, sender, body, ts)
        }
    }
}
