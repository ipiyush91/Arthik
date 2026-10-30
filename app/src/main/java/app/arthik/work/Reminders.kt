package app.arthik.work

import android.content.Context
import app.arthik.container
import app.arthik.core.model.AccountType
import app.arthik.core.text.Amounts
import app.arthik.data.db.OVERALL_BUDGET
import app.arthik.data.db.RecurringStatus
import app.arthik.data.repo.Periods
import app.arthik.notify.Notifier
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first

object Reminders {
    suspend fun run(context: Context) {
        val c = context.container
        val today = LocalDate.now()
        val days = c.settings.current().reminderDaysBefore

        c.db.accounts().all().filter { it.type == AccountType.CREDIT_CARD && !it.hidden && it.dueDate != null }.forEach { card ->
            val left = ChronoUnit.DAYS.between(today, card.dueDate).toInt()
            if (left in days) {
                val amount = card.statementTotal?.let { " · ${Amounts.formatInr(it)}" }.orEmpty()
                val whenText = when (left) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $left days" }
                Notifier.cardDue(context, card.id, "${card.name} due $whenText$amount", card.statementMin?.let { "Minimum due ${Amounts.formatInr(it)}" })
            }
            if (card.statementNeedsInput && left == days.max()) {
                Notifier.cardDue(context, card.id, "${card.name}: statement ready", "Tap to enter the total and due date")
            }
        }

        c.recurring.active.first().filter { it.status != RecurringStatus.DISMISSED && it.nextDate != null }.forEach { r ->
            val left = ChronoUnit.DAYS.between(today, r.nextDate).toInt()
            if (left == 1 || left == 0) {
                Notifier.upcoming(context, r.merchantKey.hashCode(), "${r.merchant} ${Amounts.formatInr(r.amount)} " + if (left == 0) "today" else "tomorrow")
            }
        }

        // Backup reminder: monthly while the user has never backed up.
        val s = c.settings.current()
        if (s.lastBackupAt == 0L && today.dayOfMonth == 1 && s.initialScanDone) Notifier.backupReminder(context)
    }
}

/** 80% and 100% alerts, once per bucket per month. */
object BudgetChecker {
    suspend fun check(context: Context) {
        val c = context.container
        val period = Periods.containing(LocalDate.now(), c.settings.current().monthStartDay)
        val spend = c.insights.spendByCategory(period)
        val byCat = spend.associate { it.categoryId to it.total }
        val total = spend.sumOf { it.total }
        val names = c.db.categories().all().associate { it.id to it.name }
        for (b in c.budgets.all()) {
            val used = if (b.categoryId == OVERALL_BUDGET) total else byCat[b.categoryId] ?: 0L
            val pct = if (b.amount > 0) used * 100 / b.amount else 0
            val name = if (b.categoryId == OVERALL_BUDGET) "Overall budget" else names[b.categoryId] ?: b.categoryId
            when {
                pct >= 100 && b.alerted100 != period.key -> {
                    Notifier.budget(context, b.categoryId.hashCode(), "$name exceeded", "${Amounts.formatInr(used)} of ${Amounts.formatInr(b.amount)}")
                    c.budgets.update(b.copy(alerted100 = period.key, alerted80 = period.key))
                }
                pct >= 80 && b.alerted80 != period.key -> {
                    Notifier.budget(context, b.categoryId.hashCode(), "$name at $pct%", "${Amounts.formatInr(used)} of ${Amounts.formatInr(b.amount)}")
                    c.budgets.update(b.copy(alerted80 = period.key))
                }
            }
        }
    }
}
