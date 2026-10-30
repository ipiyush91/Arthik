package app.arthik.core.text

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import java.util.Locale

object Dates {
    /** Regex fragment matching every date shape seen in Indian bank SMS. */
    const val PATTERN =
        """(?:\d{4}-\d{2}-\d{2}(?::\d{2}:\d{2}:\d{2})?""" +
            """|\d{1,2}[-/ ]?[A-Za-z]{3,9}[-/ ,]?\d{2,4}(?:[ ,]+\d{1,2}:\d{2}(?::\d{2})?(?: ?[AP]M)?)?""" +
            """|\d{1,2}[-/]\d{1,2}(?:[-/]\d{2,4})?(?:[ ,]+\d{1,2}:\d{2}(?::\d{2})?)?""" +
            """|[A-Za-z]{3,9} \d{1,2},? \d{4})"""

    private fun fmt(p: String) = DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p)
        .toFormatter(Locale.ENGLISH)

    // Two-digit years map to 2000-2099.
    private fun fmt2(prefix: String) = DateTimeFormatterBuilder().parseCaseInsensitive()
        .appendPattern(prefix).appendValueReduced(ChronoField.YEAR, 2, 2, 2000)
        .toFormatter(Locale.ENGLISH)

    private val withYear: List<DateTimeFormatter> = listOf(
        fmt("yyyy-MM-dd"),
        fmt("dd-MMM-yyyy"), fmt2("dd-MMM-"), fmt("dd/MM/yyyy"), fmt2("dd/MM/"), fmt("dd-MM-yyyy"), fmt2("dd-MM-"),
        fmt("ddMMMyyyy"), fmt2("ddMMM"), fmt("dd MMM yyyy"), fmt2("dd MMM "), fmt("dd MMMM yyyy"),
        fmt("MMM dd, yyyy"), fmt("MMM d, yyyy"), fmt("MMMM d, yyyy"), fmt("MMMM dd, yyyy"), fmt("d MMM yyyy"),
        fmt("d-MMM-yyyy"), fmt2("d-MMM-"), fmt("d/M/yyyy"), fmt2("d/M/"),
    )

    /**
     * Parses a date token. Times are dropped (the SMS received time supplies time of day).
     * A day-month token without a year (e.g. "28-10") takes the year of [received], moved back a
     * year if that would put it in the future.
     */
    fun parse(raw: String?, received: LocalDateTime): LocalDate? {
        if (raw.isNullOrBlank()) return null
        val token = raw.trim().replace(Regex("""[ ,]+\d{1,2}:\d{2}(:\d{2})?( ?[AP]M)?$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(\d{4}-\d{2}-\d{2}):.*"""), "$1").trim().trimEnd(',', '.')
        val normalised = token.replace(Regex("""\s+"""), " ")
        for (f in withYear) {
            runCatching { return LocalDate.parse(normalised, f) }
        }
        Regex("""^(\d{1,2})[-/](\d{1,2})$""").matchEntire(normalised)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            return runCatching {
                var d = LocalDate.of(received.year, month, day)
                if (d.isAfter(received.toLocalDate().plusDays(2))) d = d.minusYears(1)
                d
            }.getOrNull()
        }
        return null
    }

    /**
     * Transaction date rule: use the received date when the date is missing or implausible — more
     * than 2 days after the SMS arrived, or more than 45 days before it (almost always a mis-parse).
     */
    fun transactionDate(raw: String?, received: LocalDateTime): LocalDate {
        val parsed = parse(raw, received) ?: return received.toLocalDate()
        val daysBefore = ChronoUnit.DAYS.between(parsed, received.toLocalDate())
        return if (daysBefore < -2 || daysBefore > 45) received.toLocalDate() else parsed
    }
}
