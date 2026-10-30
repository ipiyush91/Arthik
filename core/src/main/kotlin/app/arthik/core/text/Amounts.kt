package app.arthik.core.text

import app.arthik.core.model.Paise
import java.math.BigDecimal
import java.math.RoundingMode

object Amounts {
    /** Accepts any comma grouping (1,93,523.41 or 196,000.00) and 0-3 decimals. */
    const val NUMBER = """[0-9][0-9,]*(?:\.[0-9]{1,3})?"""

    fun toPaise(raw: String?): Paise? {
        val cleaned = raw?.replace(",", "")?.trim()?.trimEnd('.') ?: return null
        if (cleaned.isEmpty()) return null
        return runCatching {
            BigDecimal(cleaned).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        }.getOrNull()
    }

    fun toDouble(raw: String?): Double? = raw?.replace(",", "")?.trim()?.toDoubleOrNull()

    /** ₹1,23,456.78 with Indian digit grouping. */
    fun formatInr(paise: Paise, withPaise: Boolean = false): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val rupees = abs / 100
        val digits = rupees.toString()
        val grouped = if (digits.length <= 3) digits else {
            val last3 = digits.takeLast(3)
            val rest = digits.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$rest,$last3"
        }
        val decimals = if (withPaise) ".%02d".format(abs % 100) else ""
        return (if (negative) "-₹" else "₹") + grouped + decimals
    }
}
