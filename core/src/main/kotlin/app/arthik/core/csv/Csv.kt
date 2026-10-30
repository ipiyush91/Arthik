package app.arthik.core.csv

import java.time.LocalDate

/** Wealthfolio activity types (subset used by Arthik). */
enum class ActivityType { BUY, SELL, DIVIDEND, DEPOSIT, WITHDRAWAL, INTEREST, FEE }

data class Activity(
    val date: LocalDate,
    val accountName: String,
    val symbol: String,
    val activityType: ActivityType,
    val quantity: Double,
    val unitPrice: Double,
    val amount: Double,
    val fee: Double = 0.0,
    val currency: String = "INR",
)

/**
 * Wealthfolio's activity import CSV: date,symbol,quantity,activityType,unitPrice,currency,fee,amount
 * plus a trailing `account` column (Wealthfolio imports into one chosen account and ignores unknown
 * columns). Cash movements use the symbol `$CASH-INR`. Verify against the current Wealthfolio
 * import docs if their format changes.
 */
object WealthfolioCsv {
    const val HEADER = "date,symbol,quantity,activityType,unitPrice,currency,fee,amount,account"
    const val CASH_SYMBOL = "\$CASH-INR"

    fun export(activities: List<Activity>): String = buildString {
        appendLine(HEADER)
        activities.sortedBy { it.date }.forEach { a ->
            appendLine(
                listOf(
                    a.date.toString(), a.symbol, num(a.quantity), a.activityType.name, num(a.unitPrice), a.currency,
                    num(a.fee), num(a.amount), a.accountName,
                ).joinToString(",") { Csv.escape(it) }
            )
        }
    }

    fun import(text: String, defaultAccount: String): List<Activity> {
        val rows = Csv.parse(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { it.trim().lowercase() }
        fun idx(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n.lowercase()).takeIf { it >= 0 } }
        val iDate = idx("date") ?: error("CSV has no date column")
        val iSymbol = idx("symbol", "ticker")
        val iQty = idx("quantity", "shares", "units")
        val iType = idx("activitytype", "activity type", "type") ?: error("CSV has no activityType column")
        val iPrice = idx("unitprice", "unit price", "price")
        val iCurrency = idx("currency")
        val iFee = idx("fee", "fees")
        val iAmount = idx("amount", "total")
        val iAccount = idx("account", "accountname", "account name")
        return rows.drop(1).filter { r -> r.any { it.isNotBlank() } }.mapNotNull { r ->
            fun col(i: Int?) = i?.let { r.getOrNull(it) }?.trim().orEmpty()
            val type = runCatching { ActivityType.valueOf(col(iType).uppercase()) }.getOrNull() ?: return@mapNotNull null
            val qty = col(iQty).toDoubleOrNull() ?: 0.0
            val price = col(iPrice).toDoubleOrNull() ?: 0.0
            Activity(
                date = LocalDate.parse(col(iDate).take(10)),
                accountName = col(iAccount).ifEmpty { defaultAccount },
                symbol = col(iSymbol).ifEmpty { CASH_SYMBOL },
                activityType = type,
                quantity = qty,
                unitPrice = price,
                amount = col(iAmount).toDoubleOrNull() ?: (qty * price),
                fee = col(iFee).toDoubleOrNull() ?: 0.0,
                currency = col(iCurrency).ifEmpty { "INR" },
            )
        }
    }

    private fun num(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}

data class TransactionRow(
    val date: LocalDate,
    val account: String,
    val direction: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    val reference: String,
    val note: String,
)

object TransactionsCsv {
    const val HEADER = "date,account,type,amount,category,merchant,reference,note"

    fun export(rows: List<TransactionRow>): String = buildString {
        appendLine(HEADER)
        rows.forEach { r ->
            appendLine(
                listOf(r.date.toString(), r.account, r.direction, "%.2f".format(r.amount), r.category, r.merchant, r.reference, r.note)
                    .joinToString(",") { Csv.escape(it) }
            )
        }
    }
}

object Csv {
    fun escape(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v

    /** RFC 4180-ish parser with quoted fields. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row.add(field.toString()); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
