package app.arthik.core.categorise

import app.arthik.core.model.Direction
import app.arthik.core.model.ParsedTransaction
import app.arthik.core.model.TxnKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MerchantDictionary(
    /** Alias regex -> canonical merchant name. */
    val aliases: Map<String, String> = emptyMap(),
    /** Category id -> keywords (case-insensitive, matched as prefixes of words). */
    val keywords: Map<String, List<String>> = emptyMap(),
)

data class Categorisation(val categoryId: String, val merchant: String?, val personToPerson: Boolean)

/**
 * Order: the user's learned merchant rules -> semantic kind -> merchant dictionary -> default.
 * Learned rules are keyed by [MerchantNormaliser.key].
 */
class Categoriser(dictionary: MerchantDictionary) {
    private val aliases = dictionary.aliases.map { (k, v) -> Regex(k, RegexOption.IGNORE_CASE) to v }
    private val keywords = dictionary.keywords.flatMap { (cat, words) ->
        words.map { Regex("""(?i)(?:^|[^a-z])""" + Regex.escape(it).replace(" ", """\E\s*\Q""")) to cat }
    }

    fun canonicalMerchant(raw: String?): String? {
        val cleaned = MerchantNormaliser.clean(raw) ?: return null
        // Aliases are tried on the raw text first so narration codes like "BIL*ONL" still match.
        return aliases.firstOrNull { it.first.containsMatchIn(raw!!) || it.first.containsMatchIn(cleaned) }?.second ?: cleaned
    }

    fun categorise(txn: ParsedTransaction, learned: Map<String, String> = emptyMap()): Categorisation {
        val merchant = canonicalMerchant(txn.merchant)
        val p2p = merchant != null && MerchantNormaliser.looksLikePerson(merchant) && dictionaryCategory(merchant) == null
        learned[MerchantNormaliser.key(merchant)]?.let { return Categorisation(it, merchant, p2p) }
        kindCategory(txn.kind, txn.direction)?.let { return Categorisation(it, merchant, p2p) }
        if (txn.direction == Direction.DEBIT) dictionaryCategory(merchant)?.let { return Categorisation(it, merchant, p2p) }
        val fallback = if (txn.direction == Direction.DEBIT) CategoryIds.OTHERS else CategoryIds.OTHER_INCOME
        return Categorisation(fallback, merchant, p2p)
    }

    fun dictionaryCategory(merchant: String?): String? {
        if (merchant == null) return null
        return keywords.firstOrNull { it.first.containsMatchIn(merchant) }?.second
    }

    private fun kindCategory(kind: TxnKind?, direction: Direction): String? = when (kind) {
        TxnKind.CARD_PAYMENT, TxnKind.WALLET_TOPUP, TxnKind.CASH_DEPOSIT, TxnKind.TRANSFER -> CategoryIds.TRANSFERS
        TxnKind.CASH_WITHDRAWAL -> CategoryIds.CASH
        TxnKind.SALARY -> CategoryIds.SALARY
        TxnKind.INTEREST -> CategoryIds.INTEREST
        TxnKind.DIVIDEND -> CategoryIds.DIVIDENDS
        TxnKind.REFUND -> CategoryIds.REFUNDS
        TxnKind.EMI -> CategoryIds.RENT_EMI
        TxnKind.INVESTMENT, TxnKind.EPF_CONTRIBUTION, TxnKind.MF_ALLOTMENT, TxnKind.SECURITIES_DEBIT -> CategoryIds.INVESTMENTS
        TxnKind.BROKER_PAYOUT -> CategoryIds.INVESTMENTS
        TxnKind.MF_REDEMPTION -> if (direction == Direction.CREDIT) CategoryIds.INVESTMENTS else null
        TxnKind.PURCHASE, null -> null
    }

    companion object {
        fun fromResources(): Categoriser {
            val text = Categoriser::class.java.classLoader!!.getResourceAsStream("arthik/merchants.json")!!
                .bufferedReader().use { it.readText() }
            return Categoriser(Json { ignoreUnknownKeys = true }.decodeFromString(text))
        }
    }
}

object MerchantNormaliser {
    private val gateways = Regex(
        """^(?:RAZORPAY|RZP|PAYU|PYU|CASHFREE|CF|PINELABS|PINE LABS|BILLDESK|CCAVENUE|PAYTM|JUSPAY)[\s*_.:-]+""",
        RegexOption.IGNORE_CASE,
    )
    private val junk = Regex("""[*_]+|\s{2,}""")
    private val trailingDigits = Regex("""[\s-]+\d+$""")
    private val business = Regex(
        """(?i)\b(?:pvt|ltd|limited|llp|inc|store|stores|mart|shop|services|technologies|tech|retail|foods|enterprises|""" +
            """traders|hardware|pharma\w*|medical|chemist|dairy|hotel|restaurant|cafe|bank|club|finance|financi|finlease|""" +
            """invest|campus|motors|petrol|fuel|toll|plaza|mall|centre|center|india|online|digital|payments?)\b"""
    )

    fun clean(raw: String?): String? {
        var s = raw?.trim() ?: return null
        if (s.isEmpty()) return null
        if (s.contains('@')) return s.trim('*', ' ') // keep VPAs as-is (masked QR VPAs are not names)
        s = s.replace(gateways, "")
        s = s.replace(junk, " ").replace(trailingDigits, "").trim()
        return s.ifEmpty { null }
    }

    /** Key for learned merchant->category rules: case- and spacing-insensitive. */
    fun key(merchant: String?): String = merchant.orEmpty().lowercase().replace(Regex("""[^a-z0-9@.]"""), "")

    /** "KRISHAN SAINI", "Kumari Poonam": letters only, 2-3 words, no business words. */
    fun looksLikePerson(merchant: String): Boolean {
        if (merchant.contains('@') || merchant.any { it.isDigit() }) return false
        val words = merchant.trim().split(Regex("""\s+"""))
        return words.size in 2..4 && words.all { it.matches(Regex("""[A-Za-z.]+""")) } && !business.containsMatchIn(merchant)
    }
}
