package app.arthik.core.match

import app.arthik.core.model.Direction
import app.arthik.core.model.Paise
import java.time.Duration
import java.time.LocalDateTime

/** The minimum the matcher needs to know about a stored transaction. */
data class TxnRef(
    val id: Long,
    val accountId: Long,
    val direction: Direction,
    val amount: Paise,
    val at: LocalDateTime,
    val reference: String?,
    val merchantKey: String?,
)

enum class DuplicateStrength { STRONG, PROBABLE }

object Deduper {
    private val PROBABLE_WINDOW: Duration = Duration.ofMinutes(15)

    /**
     * Strong: same reference (UTR/RRN) and amount. Probable: same amount and direction on the same
     * account within ±15 minutes, with no conflicting references. Different references are never
     * duplicates.
     */
    fun find(candidate: TxnRef, existing: List<TxnRef>): Pair<TxnRef, DuplicateStrength>? {
        existing.firstOrNull { e ->
            candidate.reference != null && e.reference != null &&
                normaliseRef(candidate.reference) == normaliseRef(e.reference) &&
                e.amount == candidate.amount && e.direction == candidate.direction
        }?.let { return it to DuplicateStrength.STRONG }
        existing.firstOrNull { e ->
            e.amount == candidate.amount && e.direction == candidate.direction && e.accountId == candidate.accountId &&
                Duration.between(e.at, candidate.at).abs() <= PROBABLE_WINDOW &&
                (e.reference == null || candidate.reference == null)
        }?.let { return it to DuplicateStrength.PROBABLE }
        return null
    }

    private fun normaliseRef(r: String) = r.trim().uppercase().trimStart('0')
}

object TransferMatcher {
    private val SELF_WINDOW: Duration = Duration.ofMinutes(30)
    private val CARD_PAYMENT_WINDOW: Duration = Duration.ofDays(3)

    /**
     * A debit on one tracked account and a credit of the same amount on another tracked account
     * within 30 minutes (or with the same reference) is a self-transfer.
     */
    fun findSelfTransfer(txn: TxnRef, others: List<TxnRef>): TxnRef? = others.firstOrNull { o ->
        o.accountId != txn.accountId && o.amount == txn.amount && o.direction != txn.direction &&
            ((txn.reference != null && txn.reference == o.reference) || Duration.between(o.at, txn.at).abs() <= SELF_WINDOW)
    }

    /** A bank debit (CRED, BBPS, BIL*ONL...) paired with a card's "payment received" within ±3 days. */
    fun findCardPayment(bankDebit: TxnRef, cardCredits: List<TxnRef>): TxnRef? = cardCredits
        .filter { it.amount == bankDebit.amount && Duration.between(it.at, bankDebit.at).abs() <= CARD_PAYMENT_WINDOW }
        .minByOrNull { Duration.between(it.at, bankDebit.at).abs() }

    /** A refund matches the latest debit to the same merchant within 60 days with amount >= refund. */
    fun findRefundOrigin(refund: TxnRef, debits: List<TxnRef>): TxnRef? = debits
        .filter {
            it.direction == Direction.DEBIT && it.merchantKey != null && it.merchantKey == refund.merchantKey &&
                it.amount >= refund.amount && !it.at.isAfter(refund.at) &&
                Duration.between(it.at, refund.at) <= Duration.ofDays(60)
        }
        .maxByOrNull { it.at }
}
