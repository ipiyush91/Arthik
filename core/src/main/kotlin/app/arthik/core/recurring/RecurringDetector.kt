package app.arthik.core.recurring

import app.arthik.core.model.Paise
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class Cadence(val days: Long, val tolerance: Long) { WEEKLY(7, 2), MONTHLY(30, 3), YEARLY(365, 5) }

data class Occurrence(val merchantKey: String, val merchant: String, val amount: Paise, val date: LocalDate)

data class RecurringItem(
    val merchantKey: String,
    val merchant: String,
    val cadence: Cadence,
    val typicalAmount: Paise,
    val lastDate: LocalDate,
    val nextDate: LocalDate,
    val occurrences: Int,
)

/** ≥3 occurrences, amounts within ±10% of the median, gaps that fit a weekly/monthly/yearly cadence. */
object RecurringDetector {
    fun detect(items: List<Occurrence>): List<RecurringItem> = items
        .groupBy { it.merchantKey }
        .mapNotNull { (_, list) -> analyse(list.sortedBy { it.date }) }

    private fun analyse(list: List<Occurrence>): RecurringItem? {
        if (list.size < 3) return null
        val median = list.map { it.amount }.sorted()[list.size / 2]
        val similar = list.filter { abs(it.amount - median) <= median / 10 }
        if (similar.size < 3) return null
        val gaps = similar.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val cadence = Cadence.entries.firstOrNull { c ->
            // Monthly gaps vary with month length (28-31); allow the tolerance around 28..31.
            gaps.all { g ->
                if (c == Cadence.MONTHLY) g in (28 - c.tolerance)..(31 + c.tolerance) else abs(g - c.days) <= c.tolerance
            }
        } ?: return null
        val last = similar.last()
        val next = when (cadence) {
            Cadence.WEEKLY -> last.date.plusWeeks(1)
            Cadence.MONTHLY -> last.date.plusMonths(1)
            Cadence.YEARLY -> last.date.plusYears(1)
        }
        return RecurringItem(last.merchantKey, last.merchant, cadence, median, last.date, next, similar.size)
    }
}
