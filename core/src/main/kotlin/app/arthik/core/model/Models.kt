package app.arthik.core.model

import java.time.LocalDate
import java.time.LocalDateTime

/** Amounts are stored as paise (1 rupee = 100) to avoid floating-point drift. */
typealias Paise = Long

data class SmsMessage(
    val sender: String,
    val body: String,
    val receivedAt: LocalDateTime,
)

enum class AccountType { SAVINGS, CREDIT_CARD, WALLET, FASTAG, LOAN, INVESTMENT, EPF, NPS, DEPOSIT, UNKNOWN }

enum class Direction { DEBIT, CREDIT }

enum class Channel { UPI, IMPS, NEFT, ATM, CARD, MANDATE, BILLPAY, CASH_DEPOSIT, FASTAG, WALLET, OTHER }

/** Semantic hints the parser can infer; they drive the default category. */
enum class TxnKind {
    PURCHASE, CARD_PAYMENT, WALLET_TOPUP, CASH_WITHDRAWAL, CASH_DEPOSIT, SALARY, INTEREST,
    DIVIDEND, REFUND, EMI, INVESTMENT, EPF_CONTRIBUTION, MF_ALLOTMENT, MF_REDEMPTION,
    SECURITIES_DEBIT, BROKER_PAYOUT, TRANSFER,
}

data class ParsedTransaction(
    val institution: String,
    val accountType: AccountType,
    val accountLast4: String?,
    val direction: Direction,
    val amount: Paise,
    val date: LocalDate,
    val merchant: String?,
    val reference: String?,
    val channel: Channel,
    val kind: TxnKind?,
    val balance: Paise? = null,
    val availableLimit: Paise? = null,
    /** Original currency for foreign spends (amount is then in that currency's minor units). */
    val foreignCurrency: String? = null,
    val investment: InvestmentDetail? = null,
    val ruleId: String,
)

data class InvestmentDetail(
    val scheme: String? = null,
    val folio: String? = null,
    val units: Double? = null,
    val nav: Double? = null,
    val quantity: Double? = null,
    val security: String? = null,
)

sealed interface ParseResult {
    val ruleId: String?

    data class Transaction(val txn: ParsedTransaction) : ParseResult {
        override val ruleId get() = txn.ruleId
    }

    data class Statement(
        val institution: String,
        val cardLast4: String?,
        val totalDue: Paise?,
        val minimumDue: Paise?,
        val dueDate: LocalDate?,
        override val ruleId: String,
    ) : ParseResult

    /** Pre-debit / e-mandate notice: feeds the subscriptions list, never booked as money. */
    data class UpcomingDebit(
        val institution: String,
        val accountType: AccountType,
        val accountLast4: String?,
        val merchant: String?,
        val amount: Paise,
        val dueDate: LocalDate?,
        override val ruleId: String,
    ) : ParseResult

    /** Balance or limit snapshot without a money movement (e.g. exchange balance SMS, EPF passbook). */
    data class Snapshot(
        val institution: String,
        val accountType: AccountType,
        val accountLast4: String?,
        val balance: Paise?,
        val note: String?,
        override val ruleId: String,
    ) : ParseResult

    data class Ignored(val reason: String, override val ruleId: String? = null) : ParseResult

    /** Looks financial but no rule understood it: goes to the review screen. */
    data class Unrecognised(val institution: String?) : ParseResult {
        override val ruleId: String? get() = null
    }

    /** Not a financial message at all. */
    data object NotFinancial : ParseResult {
        override val ruleId: String? get() = null
    }
}
