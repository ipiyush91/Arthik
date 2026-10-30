package app.arthik.data.repo

import androidx.room.withTransaction
import app.arthik.core.categorise.Categoriser
import app.arthik.core.categorise.CategoryIds
import app.arthik.core.categorise.MerchantNormaliser
import app.arthik.core.csv.ActivityType
import app.arthik.core.match.Deduper
import app.arthik.core.match.TransferMatcher
import app.arthik.core.match.TxnRef
import app.arthik.core.model.AccountType
import app.arthik.core.model.Direction
import app.arthik.core.model.ParseResult
import app.arthik.core.model.ParsedTransaction
import app.arthik.core.model.SmsMessage
import app.arthik.core.model.TxnKind
import app.arthik.core.parse.SmsParser
import app.arthik.data.db.AccountEntity
import app.arthik.data.db.ActivityEntity
import app.arthik.data.db.ArthikDatabase
import app.arthik.data.db.LinkType
import app.arthik.data.db.RecurringEntity
import app.arthik.data.db.RecurringSource
import app.arthik.data.db.SmsEntity
import app.arthik.data.db.SmsStatus
import app.arthik.data.db.TransactionEntity
import java.time.LocalDateTime
import kotlin.math.roundToLong

/** One SMS as read from the provider or the broadcast. */
data class RawSms(val providerId: String, val sender: String, val body: String, val receivedAt: LocalDateTime)

data class IngestStats(var transactions: Int = 0, var duplicates: Int = 0, var unrecognised: Int = 0, var other: Int = 0)

/**
 * Parses SMS and stores the results: resolves the account, drops duplicates, categorises (learned
 * rules first), links transfers / card payments / refunds, and keeps balances and card dues fresh.
 */
class IngestService(
    private val db: ArthikDatabase,
    private val parserProvider: () -> SmsParser,
    private val categoriser: Categoriser,
) {
    suspend fun ingest(messages: List<RawSms>, stats: IngestStats = IngestStats()): IngestStats {
        val parser = parserProvider()
        val learned = db.rules().all().associate { it.merchantKey to it.categoryId }
        val own = db.rules().ownIdentities().map { it.key }.toSet()
        for (raw in messages) {
            if (db.sms().exists(raw.providerId)) continue
            val result = parser.parse(SmsMessage(raw.sender, raw.body, raw.receivedAt))
            db.withTransaction { store(raw, result, learned, own, stats) }
        }
        return stats
    }

    private suspend fun store(
        raw: RawSms, result: ParseResult, learned: Map<String, String>, own: Set<String>, stats: IngestStats,
    ) {
        fun smsRow(status: SmsStatus, institution: String?, txnId: Long? = null) =
            SmsEntity(providerId = raw.providerId, sender = raw.sender, body = raw.body, receivedAt = raw.receivedAt,
                status = status, institution = institution, transactionId = txnId)

        when (result) {
            is ParseResult.Transaction -> storeTransaction(raw, result.txn, learned, own, stats, ::smsRow)
            is ParseResult.Statement -> {
                val account = resolveAccount(result.institution, AccountType.CREDIT_CARD, result.cardLast4)
                db.accounts().update(
                    account.copy(
                        statementTotal = result.totalDue ?: account.statementTotal,
                        statementMin = result.minimumDue ?: account.statementMin,
                        dueDate = result.dueDate ?: account.dueDate,
                        statementNeedsInput = result.totalDue == null,
                    )
                )
                db.sms().insert(smsRow(SmsStatus.STATEMENT, result.institution)); stats.other++
            }
            is ParseResult.UpcomingDebit -> {
                val account = resolveAccount(result.institution, result.accountType.takeIf { it != AccountType.UNKNOWN } ?: AccountType.SAVINGS, result.accountLast4)
                val merchant = categoriser.canonicalMerchant(result.merchant) ?: "Auto-debit"
                val key = MerchantNormaliser.key(merchant)
                val existing = db.recurring().all().firstOrNull { it.merchantKey == key }
                db.recurring().upsert(
                    RecurringEntity(
                        merchantKey = key, merchant = merchant, cadence = existing?.cadence ?: "MONTHLY",
                        amount = result.amount, lastDate = existing?.lastDate, nextDate = result.dueDate,
                        accountId = account.id, status = existing?.status ?: app.arthik.data.db.RecurringStatus.CONFIRMED,
                        source = RecurringSource.MANDATE, occurrences = existing?.occurrences ?: 0,
                    )
                )
                db.sms().insert(smsRow(SmsStatus.UPCOMING, result.institution)); stats.other++
            }
            is ParseResult.Snapshot -> {
                val account = resolveAccount(result.institution, result.accountType.takeIf { it != AccountType.UNKNOWN } ?: AccountType.INVESTMENT, result.accountLast4)
                db.accounts().update(account.copy(
                    balance = result.balance ?: account.balance, balanceAt = raw.receivedAt, note = result.note ?: account.note,
                ))
                db.sms().insert(smsRow(SmsStatus.SNAPSHOT, result.institution)); stats.other++
            }
            is ParseResult.Unrecognised -> { db.sms().insert(smsRow(SmsStatus.UNRECOGNISED, result.institution)); stats.unrecognised++ }
            is ParseResult.Ignored, ParseResult.NotFinancial -> Unit // OTPs and noise are never stored
        }
    }

    private suspend fun storeTransaction(
        raw: RawSms, p: ParsedTransaction, learned: Map<String, String>, own: Set<String>, stats: IngestStats,
        smsRow: (SmsStatus, String?, Long?) -> SmsEntity,
    ) {
        val type = if (p.accountType == AccountType.UNKNOWN) AccountType.SAVINGS else p.accountType
        val account = resolveAccount(p.institution, type, p.accountLast4)
        val time = if (p.date == raw.receivedAt.toLocalDate()) raw.receivedAt else p.date.atTime(12, 0)

        // Foreign spends: convert with the user's rate; flagged approximate until edited.
        var amount = p.amount
        var approximate = false
        if (p.foreignCurrency != null) {
            val rate = db.fx().all().firstOrNull { it.currency == p.foreignCurrency }?.inrPerUnit ?: DEFAULT_FX[p.foreignCurrency] ?: 1.0
            amount = (p.amount * rate).roundToLong()
            approximate = true
        }

        val candidates = (db.transactions().sameAmountBetween(amount, time.minusDays(1), time.plusDays(1)) +
            (p.reference?.let { db.transactions().byReference(it) } ?: emptyList())).distinctBy { it.id }
        val ref = TxnRef(0, account.id, p.direction, amount, time, p.reference, null)
        val dup = Deduper.find(ref, candidates.map { it.toRef() })
        if (dup != null) {
            db.sms().insert(smsRow(SmsStatus.DUPLICATE, p.institution, dup.first.id)); stats.duplicates++
            return
        }

        val cat = categoriser.categorise(p, learned)
        val merchantKey = cat.merchant?.let(MerchantNormaliser::key)
        var categoryId = cat.categoryId
        if (merchantKey != null && merchantKey in own) categoryId = CategoryIds.TRANSFERS

        val smsId = db.sms().insert(smsRow(SmsStatus.PARSED, p.institution, null))
        var txn = TransactionEntity(
            accountId = account.id, direction = p.direction, amount = amount, date = p.date, time = time,
            merchant = cat.merchant, merchantKey = merchantKey, categoryId = categoryId, reference = p.reference,
            channel = p.channel, kind = p.kind, foreignCurrency = p.foreignCurrency,
            foreignAmount = if (p.foreignCurrency != null) p.amount else null, approximate = approximate,
            personToPerson = cat.personToPerson, smsId = smsId, ruleId = p.ruleId,
        )
        val id = db.transactions().insert(txn)
        txn = txn.copy(id = id)
        db.sms().byId(smsId)?.let { db.sms().update(it.copy(transactionId = id)) }
        stats.transactions++

        updateAccountFromTxn(account, p, raw.receivedAt)
        link(txn, candidates)
        recordInvestment(txn, p, account)
    }

    /** Keeps the last known balance / available limit, and reduces a loan's outstanding by each EMI. */
    private suspend fun updateAccountFromTxn(account: AccountEntity, p: ParsedTransaction, at: LocalDateTime) {
        val fresh = db.accounts().byId(account.id) ?: return
        val newer = fresh.balanceAt == null || !at.isBefore(fresh.balanceAt)
        db.accounts().update(
            fresh.copy(
                balance = if (p.balance != null && newer) p.balance else fresh.balance,
                balanceAt = if (p.balance != null && newer) at else fresh.balanceAt,
                availableLimit = if (p.availableLimit != null && newer) p.availableLimit else fresh.availableLimit,
            )
        )
    }

    private suspend fun link(txn: TransactionEntity, candidates: List<TransactionEntity>) {
        val accounts = db.accounts().all().associateBy { it.id }
        val type = accounts[txn.accountId]?.type
        val others = candidates.filter { it.id != txn.id && it.linkedId == null }

        // Credit card bill payments (CRED, BBPS, BIL*ONL...) <-> card "payment received".
        val cardPayment = if (type == AccountType.CREDIT_CARD && txn.direction == Direction.CREDIT) {
            val bankDebits = db.transactions().sameAmountBetween(txn.amount, txn.time.minusDays(3), txn.time.plusDays(3))
                .filter {
                    it.direction == Direction.DEBIT && it.linkedId == null && it.isBillPaymentLike() &&
                        accounts[it.accountId]?.type != AccountType.CREDIT_CARD
                }
            TransferMatcher.findCardPayment(txn.toRef(), bankDebits.map { it.toRef() })
        } else if (type != AccountType.CREDIT_CARD && txn.direction == Direction.DEBIT && txn.isBillPaymentLike()) {
            val cardCredits = db.transactions().sameAmountBetween(txn.amount, txn.time.minusDays(3), txn.time.plusDays(3))
                .filter { it.direction == Direction.CREDIT && it.linkedId == null && accounts[it.accountId]?.type == AccountType.CREDIT_CARD }
            TransferMatcher.findCardPayment(txn.toRef(), cardCredits.map { it.toRef() })
        } else null
        if (cardPayment != null) return pair(txn.id, cardPayment.id, LinkType.CARD_PAYMENT)

        TransferMatcher.findSelfTransfer(txn.toRef(), others.map { it.toRef() })?.let {
            return pair(txn.id, it.id, LinkType.TRANSFER)
        }

        // Refunds net against the original purchase's category.
        if (txn.direction == Direction.CREDIT && txn.kind == TxnKind.REFUND && txn.merchantKey != null && !txn.userEdited) {
            val debits = db.transactions().debitsForMerchant(txn.merchantKey, txn.date.minusDays(60), txn.date)
            TransferMatcher.findRefundOrigin(txn.toRef(), debits.map { it.toRef() })?.let { origin ->
                val o = debits.first { it.id == origin.id }
                db.transactions().update(txn.copy(categoryId = o.categoryId, linkedId = o.id, linkType = LinkType.REFUND))
            }
        }
    }

    private suspend fun pair(a: Long, b: Long, type: LinkType) {
        listOf(a to b, b to a).forEach { (x, y) ->
            db.transactions().byId(x)?.let { t ->
                db.transactions().update(
                    t.copy(linkedId = y, linkType = type, categoryId = if (t.userEdited) t.categoryId else CategoryIds.TRANSFERS)
                )
            }
        }
    }

    /** Investment SMS also become Wealthfolio-style activities on the investment account. */
    private suspend fun recordInvestment(txn: TransactionEntity, p: ParsedTransaction, account: AccountEntity) {
        val inv = p.investment
        val (type, name) = when (p.kind) {
            TxnKind.MF_ALLOTMENT -> ActivityType.BUY to (inv?.scheme ?: p.merchant ?: "Mutual fund")
            TxnKind.MF_REDEMPTION -> ActivityType.SELL to (inv?.scheme ?: p.merchant ?: "Mutual fund")
            TxnKind.SECURITIES_DEBIT -> ActivityType.SELL to (inv?.security ?: "Security")
            TxnKind.DIVIDEND -> ActivityType.DIVIDEND to (p.merchant ?: "Dividend")
            TxnKind.EPF_CONTRIBUTION -> ActivityType.DEPOSIT to "EPF"
            TxnKind.BROKER_PAYOUT -> ActivityType.WITHDRAWAL to (p.institution)
            else -> return
        }
        val qty = inv?.units ?: inv?.quantity ?: 0.0
        db.activities().insert(
            ActivityEntity(
                accountId = account.id, date = txn.date, type = type,
                symbol = if (type == ActivityType.DEPOSIT || type == ActivityType.WITHDRAWAL) app.arthik.core.csv.WealthfolioCsv.CASH_SYMBOL else name,
                name = name, quantity = qty, unitPrice = inv?.nav ?: 0.0, amount = txn.amount,
                folio = inv?.folio, transactionId = txn.id,
            )
        )
    }

    /**
     * Account key = institution + type + last digits. Without digits: the institution's only account
     * of that type, else an "Unassigned" bucket the user resolves once.
     */
    suspend fun resolveAccount(institution: String, type: AccountType, last4: String?): AccountEntity {
        val same = db.accounts().byInstitution(institution, type)
        val match = if (last4 != null) same.firstOrNull { it.last4 == last4 } ?: same.firstOrNull { it.last4 == null && same.size == 1 }?.let {
            // The first SMS without digits created a placeholder; now we know its number.
            it.copy(last4 = last4, name = defaultName(institution, type, last4)).also { u -> db.accounts().update(u) }
        } else same.singleOrNull { it.last4 != null } ?: same.firstOrNull { it.last4 == null }
        if (match != null) return match
        val entity = AccountEntity(institution = institution, type = type, last4 = last4, name = defaultName(institution, type, last4))
        return entity.copy(id = db.accounts().insert(entity))
    }

    private fun defaultName(institution: String, type: AccountType, last4: String?): String {
        val kind = when (type) {
            AccountType.SAVINGS -> "Savings"
            AccountType.CREDIT_CARD -> "Card"
            AccountType.WALLET -> "Wallet"
            AccountType.FASTAG -> "FASTag"
            AccountType.LOAN -> "Loan"
            AccountType.INVESTMENT -> "Investments"
            AccountType.EPF -> "EPF"
            AccountType.NPS -> "NPS"
            AccountType.DEPOSIT -> "Deposit"
            AccountType.UNKNOWN -> "Account"
        }
        return when {
            last4 == null -> "$institution $kind (unassigned)"
            type == AccountType.FASTAG -> "$institution FASTag $last4"
            else -> "$institution $kind ••$last4"
        }
    }

    /** Only debits that look like card bill payments may pair with a card's "payment received". */
    private fun TransactionEntity.isBillPaymentLike() =
        kind == TxnKind.CARD_PAYMENT || channel == app.arthik.core.model.Channel.BILLPAY

    private fun TransactionEntity.toRef() = TxnRef(id, accountId, direction, amount, time, reference, merchantKey)

    companion object {
        /** Starting point only; users edit rates in Settings and transactions stay "≈" until edited. */
        val DEFAULT_FX = mapOf("USD" to 88.0, "EUR" to 102.0, "GBP" to 117.0, "AED" to 24.0, "SGD" to 68.0)
    }
}
