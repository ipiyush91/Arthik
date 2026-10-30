package app.arthik.core

import app.arthik.core.recurring.Cadence
import app.arthik.core.recurring.Occurrence
import app.arthik.core.recurring.RecurringDetector
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecurringTest {
    private fun occ(key: String, amount: Long, date: String) = Occurrence(key, key.replaceFirstChar { it.uppercase() }, amount, LocalDate.parse(date))

    @Test fun `monthly Netflix is detected with the next date`() {
        val items = listOf("2026-06-05", "2026-07-05", "2026-08-05", "2026-09-05").map { occ("netflix", 64900, it) }
        val found = RecurringDetector.detect(items.shuffled())
        assertEquals(1, found.size)
        val n = found.single()
        assertEquals("netflix", n.merchantKey)
        assertEquals(Cadence.MONTHLY, n.cadence)
        assertEquals(64900L, n.typicalAmount)
        assertEquals(LocalDate.parse("2026-09-05"), n.lastDate)
        assertEquals(LocalDate.parse("2026-10-05"), n.nextDate)
        assertEquals(4, n.occurrences)
    }

    @Test fun `month-length variation and small price changes still count as monthly`() {
        val items = listOf(
            occ("spotify", 11900, "2026-01-31"), occ("spotify", 11900, "2026-02-28"),
            occ("spotify", 11900, "2026-03-31"), occ("spotify", 12900, "2026-04-30"),
        )
        val s = RecurringDetector.detect(items).single()
        assertEquals(Cadence.MONTHLY, s.cadence)
        assertEquals(4, s.occurrences)
    }

    @Test fun `weekly and yearly cadences`() {
        val weekly = listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22").map { occ("milk", 50000, it) }
        assertEquals(Cadence.WEEKLY, RecurringDetector.detect(weekly).single().cadence)
        val yearly = listOf("2024-03-10", "2025-03-11", "2026-03-10").map { occ("prime", 149900, it) }
        val y = RecurringDetector.detect(yearly).single()
        assertEquals(Cadence.YEARLY, y.cadence)
        assertEquals(LocalDate.parse("2027-03-10"), y.nextDate)
    }

    @Test fun `irregular payments, too few payments and wildly varying amounts are not recurring`() {
        val irregular = listOf("2026-06-01", "2026-06-04", "2026-07-14", "2026-07-25").map { occ("swiggy", 45000, it) }
        val twoOnly = listOf("2026-06-05", "2026-07-05").map { occ("hotstar", 29900, it) }
        val amounts = listOf(
            occ("amazon", 10000, "2026-06-05"), occ("amazon", 250000, "2026-07-05"),
            occ("amazon", 49900, "2026-08-05"), occ("amazon", 99900, "2026-09-05"),
        )
        assertTrue(RecurringDetector.detect(irregular + twoOnly + amounts).isEmpty())
    }

    @Test fun `merchants are analysed separately`() {
        val netflix = listOf("2026-06-05", "2026-07-05", "2026-08-05").map { occ("netflix", 64900, it) }
        val noise = listOf("2026-06-02", "2026-06-20", "2026-08-30").map { occ("uber", 25000, it) }
        assertEquals(listOf("netflix"), RecurringDetector.detect(netflix + noise).map { it.merchantKey })
    }
}
