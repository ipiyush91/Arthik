package app.arthik.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ForegroundInfo
import app.arthik.R
import app.arthik.ui.MainActivity

/**
 * All notifications are VISIBILITY_PRIVATE with a redacted public version, so amounts never show
 * on the lock screen.
 */
object Notifier {
    private const val CH_DUES = "dues"
    private const val CH_BUDGETS = "budgets"
    private const val CH_UPCOMING = "upcoming"
    private const val CH_SYSTEM = "system"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_DUES, "Card due dates", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CH_BUDGETS, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_UPCOMING, "Subscriptions & EMIs", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_SYSTEM, "Backups & scans", NotificationManager.IMPORTANCE_LOW),
            ).onEach { it.lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        )
    }

    fun cardDue(context: Context, id: Long, title: String, text: String?) = post(context, CH_DUES, 1000 + id.toInt(), title, text, "Card payment reminder")
    fun budget(context: Context, id: Int, title: String, text: String) = post(context, CH_BUDGETS, 2000 + (id and 0xFFF), title, text, "Budget alert")
    fun upcoming(context: Context, id: Int, title: String) = post(context, CH_UPCOMING, 3000 + (id and 0xFFF), title, null, "Upcoming payment")
    fun backupReminder(context: Context) = post(context, CH_SYSTEM, 4000, "Back up your data", "You have never made a backup. Settings → Backup.", "Backup reminder")

    private fun post(context: Context, channel: String, id: Int, title: String, text: String?, publicTitle: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(publicTitle).build()
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .apply { if (text != null) setContentText(text) }
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    /** Needed by expedited work on older API levels; shown only if the system runs it in the foreground. */
    fun workForeground(context: Context): ForegroundInfo = ForegroundInfo(
        5000,
        NotificationCompat.Builder(context, CH_SYSTEM).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Updating Arthik").setSilent(true).build(),
    )
}
