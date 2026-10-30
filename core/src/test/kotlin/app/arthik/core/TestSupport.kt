package app.arthik.core

import app.arthik.core.categorise.Categoriser
import app.arthik.core.model.ParseResult
import app.arthik.core.model.ParsedTransaction
import app.arthik.core.model.SmsMessage
import app.arthik.core.parse.SmsParser
import app.arthik.core.rules.RuleRepository
import java.time.LocalDateTime
import kotlin.test.assertIs

object TestSupport {
    val repo: RuleRepository by lazy { RuleRepository.fromResources() }
    val parser: SmsParser by lazy { SmsParser(repo) }
    val categoriser: Categoriser by lazy { Categoriser.fromResources() }

    val NOW: LocalDateTime = LocalDateTime.of(2026, 10, 30, 12, 0)

    fun parse(body: String, sender: String = "AD-UNKNWN-S", at: LocalDateTime = NOW): ParseResult =
        parser.parse(SmsMessage(sender, body, at))

    fun txn(body: String, sender: String = "AD-UNKNWN-S", at: LocalDateTime = NOW): ParsedTransaction {
        val r = parse(body, sender, at)
        assertIs<ParseResult.Transaction>(r, "Expected a transaction for: $body but got $r")
        return r.txn
    }

    fun rupees(r: Long) = r * 100
}
