package app.arthik.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.arthik.container
import app.arthik.data.repo.IngestStats
import app.arthik.data.repo.RawSms
import app.arthik.sms.SmsIds
import app.arthik.sms.SmsReader
import app.arthik.widget.ArthikWidget
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Scans the inbox (12 months on first run, or since the last stored SMS on a rescan) with progress. */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val months = inputData.getInt(KEY_MONTHS, 12)
        val since = LocalDateTime.now().minusMonths(months.toLong())
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val reader = SmsReader(applicationContext)
        val total = reader.count(since).coerceAtLeast(1)
        var seen = 0
        val stats = IngestStats()
        reader.forEachBatch(since) { batch ->
            c.ingest.ingest(batch, stats)
            seen += batch.size
            setProgress(workDataOf(KEY_DONE to seen, KEY_TOTAL to total, KEY_TXNS to stats.transactions))
        }
        c.recurring.refresh()
        c.settings.setInitialScanDone(true)
        ArthikWidget.refresh(applicationContext)
        return Result.success(workDataOf(KEY_TXNS to stats.transactions, KEY_UNRECOGNISED to stats.unrecognised))
    }

    companion object {
        const val NAME = "scan"
        const val KEY_MONTHS = "months"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_TXNS = "txns"
        const val KEY_UNRECOGNISED = "unrecognised"

        fun start(context: Context, months: Int = 12) {
            val req = OneTimeWorkRequestBuilder<ScanWorker>().setInputData(workDataOf(KEY_MONTHS to months)).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
        }
    }
}

/** One new SMS from the broadcast receiver. */
class IngestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val sender = inputData.getString(KEY_SENDER) ?: return Result.success()
        val body = inputData.getString(KEY_BODY) ?: return Result.success()
        val at = SmsReader.toLocal(inputData.getLong(KEY_TS, System.currentTimeMillis()))
        val stats = c.ingest.ingest(listOf(RawSms(SmsIds.of(sender, body, at), sender, body, at)))
        if (stats.transactions > 0) {
            BudgetChecker.check(applicationContext)
            ArthikWidget.refresh(applicationContext)
        }
        return Result.success()
    }

    override suspend fun getForegroundInfo() = app.arthik.notify.Notifier.workForeground(applicationContext)

    companion object {
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_TS = "ts"

        fun enqueue(context: Context, sender: String, body: String, ts: Long) {
            val data = Data.Builder().putString(KEY_SENDER, sender).putString(KEY_BODY, body).putLong(KEY_TS, ts).build()
            val req = OneTimeWorkRequestBuilder<IngestWorker>()
                .setInputData(data)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}

/**
 * Daily ~09:00: card due reminders (5 days, 1 day, due day), subscription/EMI alerts, budget
 * alerts, recurring refresh, backup reminder, optional weekly auto-backup, widget refresh.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.container.databaseAvailable()) return Result.success()
        Reminders.run(applicationContext)
        BudgetChecker.check(applicationContext)
        applicationContext.container.recurring.refresh()
        applicationContext.container.backup.maybeAutoBackup()
        ArthikWidget.refresh(applicationContext)
        return Result.success()
    }
}

object Schedules {
    fun ensureDaily(context: Context) {
        val now = LocalDateTime.now()
        var next = now.with(LocalTime.of(9, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val req = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
