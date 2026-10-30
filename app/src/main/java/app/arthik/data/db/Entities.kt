@file:UseSerializers(LocalDateSerializer::class, LocalDateTimeSerializer::class)

package app.arthik.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.arthik.core.csv.ActivityType
import app.arthik.core.model.AccountType
import app.arthik.core.model.Channel
import app.arthik.core.model.Direction
import app.arthik.core.model.TxnKind
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
@Entity(
    tableName = "accounts",
    indices = [Index(value = ["institution", "type", "last4"])],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val institution: String,
    val type: AccountType,
    /** Masked number tail, vehicle number for FASTag, member id for EPF. Null = unassigned bucket. */
    val last4: String?,
    val name: String,
    val hidden: Boolean = false,
    val balance: Long? = null,
    val balanceAt: LocalDateTime? = null,
    val creditLimit: Long? = null,
    val availableLimit: Long? = null,
    val statementTotal: Long? = null,
    val statementMin: Long? = null,
    val dueDate: LocalDate? = null,
    /** HDFC-style "statement generated" without amounts: prompt the user once. */
    val statementNeedsInput: Boolean = false,
    val loanOutstanding: Long? = null,
    val note: String? = null,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

enum class TxnSource { SMS, MANUAL, IMPORT }

enum class LinkType { NONE, TRANSFER, CARD_PAYMENT, REFUND }

@Serializable
@Entity(
    tableName = "transactions",
    indices = [
        Index("date"), Index("accountId"), Index("categoryId"), Index("reference"), Index("merchantKey"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val direction: Direction,
    /** INR paise. For foreign spends this is the converted amount and [approximate] is true. */
    val amount: Long,
    val date: LocalDate,
    val time: LocalDateTime,
    val merchant: String?,
    val merchantKey: String?,
    val categoryId: String,
    val reference: String?,
    val channel: Channel,
    val kind: TxnKind?,
    val note: String? = null,
    /** "My share" of a shared expense; totals use this when set. */
    val myShare: Long? = null,
    val foreignCurrency: String? = null,
    val foreignAmount: Long? = null,
    val approximate: Boolean = false,
    val personToPerson: Boolean = false,
    val linkedId: Long? = null,
    val linkType: LinkType = LinkType.NONE,
    /** Rescans never overwrite fields the user edited. */
    val userEdited: Boolean = false,
    val source: TxnSource = TxnSource.SMS,
    val smsId: Long? = null,
    val ruleId: String? = null,
)

enum class SmsStatus { PARSED, DUPLICATE, STATEMENT, UPCOMING, SNAPSHOT, UNRECOGNISED, IGNORED_BY_USER }

/** Copy of the original SMS (OTPs and non-financial messages are never stored). */
@Serializable
@Entity(tableName = "sms", indices = [Index(value = ["providerId"], unique = true), Index("status")])
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Telephony provider _id, or a hash for messages received live. */
    val providerId: String,
    val sender: String,
    val body: String,
    val receivedAt: LocalDateTime,
    val status: SmsStatus,
    val institution: String? = null,
    val transactionId: Long? = null,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val icon: String,
    val isIncome: Boolean,
    val sortOrder: Int,
    val hidden: Boolean = false,
)

/** Learned "merchant -> category" rule created when the user recategorises. */
@Serializable
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val merchant: String,
    val categoryId: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

/** VPAs / names the user marked as their own: transfers to them are self-transfers. */
@Serializable
@Entity(tableName = "own_identities")
data class OwnIdentityEntity(@PrimaryKey val key: String, val label: String)

const val OVERALL_BUDGET = "__overall"

@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryId: String,
    val amount: Long,
    /** yyyyMM of the month in which the 80% / 100% alerts last fired (once per bucket per month). */
    val alerted80: Int = 0,
    val alerted100: Int = 0,
)

enum class RecurringStatus { DETECTED, CONFIRMED, DISMISSED }
enum class RecurringSource { DETECTED, MANDATE, EMI }

@Serializable
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey val merchantKey: String,
    val merchant: String,
    val cadence: String,
    val amount: Long,
    val lastDate: LocalDate?,
    val nextDate: LocalDate?,
    val accountId: Long? = null,
    val status: RecurringStatus = RecurringStatus.DETECTED,
    val source: RecurringSource = RecurringSource.DETECTED,
    val occurrences: Int = 0,
)

/** Wealthfolio-compatible investment activity. */
@Serializable
@Entity(tableName = "activities", indices = [Index("accountId"), Index("date")])
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val date: LocalDate,
    val type: ActivityType,
    /** Symbol/ISIN when known, otherwise the name as seen in the SMS. */
    val symbol: String,
    val name: String,
    val quantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val amount: Long,
    val fee: Long = 0,
    val folio: String? = null,
    val transactionId: Long? = null,
    val symbolVerified: Boolean = false,
)

/** Per-currency INR conversion rates for foreign spends (user-editable). */
@Serializable
@Entity(tableName = "fx_rates")
data class FxRateEntity(@PrimaryKey val currency: String, val inrPerUnit: Double)

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

object LocalDateTimeSerializer : KSerializer<LocalDateTime> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDateTime", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDateTime) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDateTime = LocalDateTime.parse(decoder.decodeString())
}
