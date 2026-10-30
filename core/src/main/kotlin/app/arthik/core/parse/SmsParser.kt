package app.arthik.core.parse

import app.arthik.core.model.AccountType
import app.arthik.core.model.Direction
import app.arthik.core.model.InvestmentDetail
import app.arthik.core.model.ParseResult
import app.arthik.core.model.ParsedTransaction
import app.arthik.core.model.SmsMessage
import app.arthik.core.rules.CompiledRule
import app.arthik.core.rules.RuleRepository
import app.arthik.core.rules.RuleType
import app.arthik.core.text.Amounts
import app.arthik.core.text.Dates
import app.arthik.core.text.Normalizer

/**
 * Turns one SMS into a [ParseResult]. Order: sender gate -> OTP -> ignore rules -> institution
 * rules -> generic fallback -> "unrecognised" if it still looks financial.
 */
class SmsParser(private val repo: RuleRepository) {
    private val senders = Senders(repo.senders)

    fun parse(sms: SmsMessage): ParseResult {
        val body = Normalizer.normalise(sms.body)
        if (body.isEmpty()) return ParseResult.NotFinancial
        if (OTP.containsMatchIn(body)) return ParseResult.Ignored("otp")
        if (Senders.isPhoneNumber(sms.sender)) {
            return if (looksFinancial(body)) ParseResult.Ignored("untrusted-sender") else ParseResult.NotFinancial
        }

        val core = Senders.core(sms.sender)
        val institution = senders.institutionFor(sms.sender, body)

        for (compiled in repo.rules) {
            val rule = compiled.rule
            val applies = rule.institution == "*" || rule.institution == institution || core in rule.senders
            if (!applies) continue
            for (regex in compiled.regexes) {
                val match = regex.find(body) ?: continue
                return build(compiled, match, body, sms, institution) ?: continue
            }
        }

        if (!looksFinancial(body)) return ParseResult.NotFinancial
        if (NOT_COMPLETED.containsMatchIn(body)) return ParseResult.Ignored("not-completed")
        // Only trust the generic parser for known institutions; unknown senders go to review.
        if (institution != null) GenericParser.parse(body, institution, sms)?.let { return ParseResult.Transaction(it) }
        return ParseResult.Unrecognised(institution)
    }

    private fun build(compiled: CompiledRule, m: MatchResult, body: String, sms: SmsMessage, detected: String?): ParseResult? {
        val rule = compiled.rule
        fun g(name: String): String? = runCatching { m.groups[name]?.value }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val institution = if (rule.institution == "*") detected ?: "Unknown" else rule.institution
        return when (rule.type) {
            RuleType.IGNORE -> ParseResult.Ignored(rule.reason ?: "ignored", rule.id)
            RuleType.STATEMENT -> ParseResult.Statement(
                institution = institution,
                cardLast4 = g("acct"),
                totalDue = Amounts.toPaise(g("total")),
                minimumDue = Amounts.toPaise(g("min")),
                dueDate = Dates.parse(g("due") ?: g("date"), sms.receivedAt),
                ruleId = rule.id,
            )
            RuleType.UPCOMING -> ParseResult.UpcomingDebit(
                institution = institution,
                accountType = rule.accountType,
                accountLast4 = g("acct"),
                merchant = cleanMerchant(g("merchant")),
                amount = Amounts.toPaise(g("amount")) ?: return null,
                dueDate = Dates.parse(g("due") ?: g("date"), sms.receivedAt),
                ruleId = rule.id,
            )
            RuleType.SNAPSHOT -> ParseResult.Snapshot(
                institution = institution,
                accountType = rule.accountType,
                accountLast4 = g("acct"),
                balance = Amounts.toPaise(g("balance")),
                note = g("note"),
                ruleId = rule.id,
            )
            RuleType.TXN -> {
                // Securities movements (CDSL debits) carry a quantity but no money amount.
                val amount = Amounts.toPaise(g("amount")) ?: if (g("qty") != null) 0L else return null
                val direction = rule.direction ?: GenericParser.direction(body) ?: return null
                val merchant = cleanMerchant(g("merchant"))
                val detail = InvestmentDetail(
                    scheme = g("scheme"), folio = g("folio"), units = Amounts.toDouble(g("units")),
                    nav = Amounts.toDouble(g("nav")), quantity = Amounts.toDouble(g("qty")), security = g("security"),
                ).takeIf { it != InvestmentDetail() }
                ParseResult.Transaction(
                    ParsedTransaction(
                        institution = institution,
                        accountType = rule.accountType,
                        accountLast4 = g("acct"),
                        direction = direction,
                        amount = amount,
                        date = Dates.transactionDate(g("date"), sms.receivedAt),
                        merchant = merchant,
                        reference = g("ref"),
                        channel = if (rule.channel == app.arthik.core.model.Channel.OTHER) KindInference.channel(body) else rule.channel,
                        kind = rule.kind ?: KindInference.infer(body, merchant, direction),
                        balance = Amounts.toPaise(g("balance")),
                        availableLimit = Amounts.toPaise(g("limit")),
                        foreignCurrency = g("currency")?.uppercase()?.takeIf { it != "INR" && it != "RS" },
                        investment = detail,
                        ruleId = rule.id,
                    )
                )
            }
        }
    }

    companion object {
        private val OTP = Regex(
            """\bOTP\b|one[ -]?time password|verification code|\bTPIN\b|secret code|\bpasscode\b|SafeKey""",
            RegexOption.IGNORE_CASE,
        )
        internal val NOT_COMPLETED = Regex(
            """not (?:been )?debited|will be (?:debited|deducted|auto-?debited)|auto-?debited via|is due (?:on|by)|""" +
                """has requested|requested (?:Rs|money)|collect request|declined|\bfailed\b|could not be processed|""" +
                """is blocked|has been blocked""",
            RegexOption.IGNORE_CASE,
        )
        private val FINANCIAL_AMOUNT = Regex("""Rs ?[0-9]""")
        private val FINANCIAL_WORD = Regex(
            """debit|credit|\bDr\b|\bCr\b|spent|paid|sent|received|withdraw|deposit|transfer|refund|revers|txn|purchase|payment|balance|\bA/?c\b|card""",
            RegexOption.IGNORE_CASE,
        )

        fun looksFinancial(body: String) = FINANCIAL_AMOUNT.containsMatchIn(body) && FINANCIAL_WORD.containsMatchIn(body)

        private val merchantJunk = Regex("""^[\s.:;,\-]+|[\s.:;,\-]+$""")

        fun cleanMerchant(raw: String?): String? {
            val m = raw?.replace(merchantJunk, "")?.replace(Regex("""\s+"""), " ")?.trim() ?: return null
            if (m.isEmpty() || m.equals("NA", true) || m.equals("N/A", true)) return null
            return m
        }
    }
}

/** Cross-bank fallback (spec 14.2). Used only after every institution rule failed. */
internal object GenericParser {
    private fun r(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    private val amount = r("""(?<!Bal |Bal: |Bal:|Avl |Avbl |Available |limit |Lmt |limit: )Rs ?(${Amounts.NUMBER})""")
    private val bareDebitAmount = r("""debited by (${Amounts.NUMBER})""")
    private val debitWords = r("""\b(?:debited|Dr\.?|spent|sent|paid|withdrawn|w/d|transferred from|used (?:on|at)|purchase)\b""")
    private val creditWords = r("""\b(?:credited|Cr\.?|received|deposited|refund(?:ed)?|reversed|reversal|cashback)\b""")
    private val acct = listOf(
        r("""(?:A/?c|Acct?|AC|Account|Card(?: no\.?)?|ending(?: with)?)\s*(?:no\.?\s*)?[:\s]*[*Xx.]+\s*(\d{3,6})"""),
        r("""(?:A/?c|Acct?|Account|Card)\s*(?:no\.?)?\s*(\d{4})\b"""),
        r("""BLOCK(?:CARD| CC| DC)?\s*(?:XX)?(\d{4})"""),
    )
    private val merchant = listOf(
        r("""to VPA (\S+@\S+)"""),
        r("""trf to (.+?) Refno"""),
        r("""; (.+?) credited"""),
        r("""\bat (.+?) on\b"""),
        r("""\bto:? (.+?)(?:\.| on | Ref| UPI|$)"""),
        r("""\bfrom (.+?)(?: on | via |\.|$)"""),
        r("""UPI/(?:DR|CR|P2M|P2A)/\d+/([^/]+)"""),
        r("""Info:?\s*-?\s*(.+?)(?:\.|$)"""),
    )
    private val ref = r("""(?:UPI Ref(?: No\.?| ID)?|Ref ?(?:no|No\.?|#)?|RRN|UTR(?: Ref\.?)?|IMPS Ref\.?(?: no\.?)?|UPI)[:\s.-]*([A-Z0-9]{10,22})""")
    private val date = Regex("""\b(${Dates.PATTERN})""")
    private val balance = r("""(?:Avl\.? ?Bal|Aval Bal|AvlBal|Avbl Amt|Avb Bal|Total Bal|New Bal|Clear Bal|Current Bal|Available Balance|Bal)\s*(?:is)?\s*[:\-]?\s*Rs ?(${Amounts.NUMBER})""")
    private val limit = r("""(?:Avl|Avbl|Available)\.? ?(?:Limit|Lmt|credit limit)\s*(?:is)?\s*[:\-]?\s*Rs ?(${Amounts.NUMBER})""")
    private val creditCard = r("""credit ?card|\bCC\b""")
    private val card = r("""\bcard\b""")
    private val wallet = r("""wallet|Pay Balance|gift card""")

    fun direction(body: String): Direction? {
        val d = debitWords.find(body)?.range?.first
        val c = creditWords.find(body)?.range?.first
        return when {
            d == null && c == null -> null
            c == null -> Direction.DEBIT
            d == null -> Direction.CREDIT
            d < c -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }

    fun parse(body: String, institution: String, sms: SmsMessage): ParsedTransaction? {
        val amountText = amount.find(body)?.groupValues?.get(1) ?: bareDebitAmount.find(body)?.groupValues?.get(1) ?: return null
        val dir = direction(body) ?: return null
        val merchantText = SmsParser.cleanMerchant(merchant.firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) })
            ?.take(60)
        val accountType = when {
            creditCard.containsMatchIn(body) -> AccountType.CREDIT_CARD
            wallet.containsMatchIn(body) -> AccountType.WALLET
            card.containsMatchIn(body) && !body.contains("debit card", true) -> AccountType.CREDIT_CARD
            else -> AccountType.SAVINGS
        }
        return ParsedTransaction(
            institution = institution,
            accountType = accountType,
            accountLast4 = acct.firstNotNullOfOrNull { it.find(body)?.groupValues?.get(1) },
            direction = dir,
            amount = Amounts.toPaise(amountText) ?: return null,
            date = Dates.transactionDate(date.find(body)?.groupValues?.get(1), sms.receivedAt),
            merchant = merchantText,
            reference = ref.find(body)?.groupValues?.get(1),
            channel = KindInference.channel(body),
            kind = KindInference.infer(body, merchantText, dir),
            balance = Amounts.toPaise(balance.find(body)?.groupValues?.get(1)),
            availableLimit = Amounts.toPaise(limit.find(body)?.groupValues?.get(1)),
            ruleId = "generic",
        )
    }
}
