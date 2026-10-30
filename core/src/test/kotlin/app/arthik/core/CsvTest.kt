package app.arthik.core

import app.arthik.core.csv.Activity
import app.arthik.core.csv.ActivityType
import app.arthik.core.csv.Csv
import app.arthik.core.csv.TransactionRow
import app.arthik.core.csv.TransactionsCsv
import app.arthik.core.csv.WealthfolioCsv
import app.arthik.core.text.Amounts
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class CsvTest {
    private val activities = listOf(
        Activity(LocalDate.parse("2026-02-24"), "HDFC MF", "HDFC Mid Cap Fund-DG", ActivityType.BUY, 31.169, 224.603, 7000.65),
        Activity(LocalDate.parse("2026-05-19"), "Zerodha, Kite", "VARUNBEVERAGES LIMI", ActivityType.DIVIDEND, 0.0, 0.0, 104.0),
        Activity(LocalDate.parse("2026-01-18"), "Zerodha, Kite", WealthfolioCsv.CASH_SYMBOL, ActivityType.WITHDRAWAL, 0.0, 0.0, 13916.62, fee = 23.6),
        Activity(LocalDate.parse("2019-01-18"), "CDSL \"demat\"", "TREASURE BILL OF GOVERNMENT OF INDIA", ActivityType.SELL, 100.0, 0.0, 0.0),
    )

    @Test fun `Wealthfolio export and import round-trip`() {
        val text = WealthfolioCsv.export(activities)
        val lines = text.trimEnd().lines()
        assertEquals(WealthfolioCsv.HEADER, lines.first())
        // Rows are sorted by date; whole numbers are written without a decimal part.
        assertEquals("2019-01-18,TREASURE BILL OF GOVERNMENT OF INDIA,100,SELL,0,INR,0,0,\"CDSL \"\"demat\"\"\"", lines[1])
        assertEquals("2026-02-24,HDFC Mid Cap Fund-DG,31.169,BUY,224.603,INR,0,7000.65,HDFC MF", lines[3])
        val back = WealthfolioCsv.import(text, defaultAccount = "Unused")
        assertEquals(activities.sortedBy { it.date }, back)
    }

    @Test fun `Wealthfolio import accepts header aliases, defaults and skips unknown types`() {
        val text = "Date,Ticker,Shares,Type,Price,Fees,Total\r\n" +
            "2026-03-01T00:00:00Z,INFY,10,buy,1500.5,20,15025\r\n" +
            "2026-03-02,INFY,1,SPLIT,0,0,0\r\n" +
            ",,,,,,\r\n" +
            "2026-03-03,,,DEPOSIT,,,5000\r\n"
        val rows = WealthfolioCsv.import(text, defaultAccount = "Zerodha")
        assertEquals(2, rows.size)
        assertEquals(Activity(LocalDate.parse("2026-03-01"), "Zerodha", "INFY", ActivityType.BUY, 10.0, 1500.5, 15025.0, 20.0), rows[0])
        assertEquals(WealthfolioCsv.CASH_SYMBOL, rows[1].symbol)
        assertEquals(5000.0, rows[1].amount)
        assertEquals("INR", rows[1].currency)
    }

    @Test fun `CSV parser handles quotes, escaped quotes, commas and newlines inside fields`() {
        val text = "a,\"b, with comma\",\"say \"\"hi\"\"\"\n\"multi\nline\",,last\r\nsolo"
        assertEquals(
            listOf(listOf("a", "b, with comma", "say \"hi\""), listOf("multi\nline", "", "last"), listOf("solo")),
            Csv.parse(text),
        )
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"q\"\"x\"", Csv.escape("q\"x"))
    }

    @Test fun `transactions CSV escapes merchants and formats amounts`() {
        val out = TransactionsCsv.export(listOf(
            TransactionRow(LocalDate.parse("2026-10-30"), "ICICI ••468", "DEBIT", 91.0, "Others", "Kumari, Poonam", "120807593798", ""),
        ))
        assertEquals(TransactionsCsv.HEADER + "\n2026-10-30,ICICI ••468,DEBIT,91.00,Others,\"Kumari, Poonam\",120807593798,\n", out)
        assertEquals(listOf(TransactionsCsv.HEADER.split(","), listOf("2026-10-30", "ICICI ••468", "DEBIT", "91.00", "Others", "Kumari, Poonam", "120807593798", "")), Csv.parse(out))
    }

    @Test fun `INR formatting uses Indian digit grouping`() {
        assertEquals("₹1,23,456", Amounts.formatInr(12345600))
        assertEquals("₹1,23,456.78", Amounts.formatInr(12345678, withPaise = true))
        assertEquals("₹999", Amounts.formatInr(99900))
        assertEquals("₹1,000", Amounts.formatInr(100000))
        assertEquals("₹1,00,00,000", Amounts.formatInr(1_000_000_000L))
        assertEquals("₹10,00,000", Amounts.formatInr(100_000_000L))
        assertEquals("-₹5,79,822.00", Amounts.formatInr(-57982200, withPaise = true))
        assertEquals("₹0.05", Amounts.formatInr(5, withPaise = true))
    }

    @Test fun `amount parsing accepts any grouping and up to three decimals`() {
        assertEquals(19352341L, Amounts.toPaise("1,93,523.41"))
        assertEquals(19600000L, Amounts.toPaise("196,000.00"))
        assertEquals(30500L, Amounts.toPaise("305.0"))
        assertEquals(500L, Amounts.toPaise("5.000"))
        assertEquals(1391662L, Amounts.toPaise("13916.620"))
        assertEquals(null, Amounts.toPaise("--"))
    }
}
