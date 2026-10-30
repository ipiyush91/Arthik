package app.arthik.core

import app.arthik.core.model.AccountType
import app.arthik.core.model.Channel
import app.arthik.core.model.Direction
import app.arthik.core.model.ParsedTransaction
import app.arthik.core.model.TxnKind
import app.arthik.core.text.Amounts
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/** Helpers for the public-sample tests (spec Section 14). Message texts come from the research files. */
object Pub {
    /** Marker for "do not check this field". */
    object Skip

    fun rs(text: String): Long = Amounts.toPaise(text)!!
    fun day(iso: String): LocalDate = LocalDate.parse(iso)

    /** Received time: noon on the given day, so the parsed date passes the plausibility window. */
    fun on(iso: String): LocalDateTime = LocalDate.parse(iso).atTime(12, 0)

    fun expect(
        t: ParsedTransaction,
        amount: String,
        direction: Direction,
        acct: Any? = Skip,
        merchant: Any? = Skip,
        ref: Any? = Skip,
        date: String? = null,
        balance: Any? = Skip,
        limit: Any? = Skip,
        accountType: AccountType? = null,
        institution: String? = null,
        kind: Any? = Skip,
        channel: Channel? = null,
        rule: String? = null,
    ) {
        assertEquals(rs(amount), t.amount, "amount")
        assertEquals(direction, t.direction, "direction")
        if (acct != Skip) assertEquals(acct, t.accountLast4, "account")
        if (merchant != Skip) assertEquals(merchant, t.merchant, "merchant")
        if (ref != Skip) assertEquals(ref, t.reference, "reference")
        if (date != null) assertEquals(day(date), t.date, "date")
        if (balance != Skip) assertEquals((balance as String?)?.let(::rs), t.balance, "balance")
        if (limit != Skip) assertEquals((limit as String?)?.let(::rs), t.availableLimit, "limit")
        if (accountType != null) assertEquals(accountType, t.accountType, "accountType")
        if (institution != null) assertEquals(institution, t.institution, "institution")
        if (kind != Skip) assertEquals(kind as TxnKind?, t.kind, "kind")
        if (channel != null) assertEquals(channel, t.channel, "channel")
        if (rule != null) assertEquals(rule, t.ruleId, "rule")
    }
}
