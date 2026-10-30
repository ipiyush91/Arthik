package app.arthik.data.repo

import java.time.LocalDate
import java.time.YearMonth

/** A budgeting month. With a start day other than 1, "October" runs e.g. 25 Sep – 24 Oct. */
data class Period(val from: LocalDate, val to: LocalDate) {
    val key: Int get() = to.year * 100 + to.monthValue
    fun previous(startDay: Int) = Periods.containing(from.minusDays(1), startDay)
    fun next(startDay: Int) = Periods.containing(to.plusDays(1), startDay)
    val days: Int get() = (to.toEpochDay() - from.toEpochDay() + 1).toInt()
}

object Periods {
    fun containing(date: LocalDate, startDay: Int): Period {
        if (startDay <= 1) {
            val ym = YearMonth.from(date)
            return Period(ym.atDay(1), ym.atEndOfMonth())
        }
        val thisStart = date.withDayOfMonth(startDay.coerceAtMost(date.lengthOfMonth()))
        val start = if (date >= thisStart) thisStart else date.minusMonths(1).let { it.withDayOfMonth(startDay.coerceAtMost(it.lengthOfMonth())) }
        val nextStart = start.plusMonths(1).let { it.withDayOfMonth(startDay.coerceAtMost(it.lengthOfMonth())) }
        return Period(start, nextStart.minusDays(1))
    }
}
