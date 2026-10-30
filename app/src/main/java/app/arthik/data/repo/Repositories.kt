package app.arthik.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.arthik.core.categorise.CategoryIds
import app.arthik.core.categorise.MerchantNormaliser
import app.arthik.core.csv.ActivityType
import app.arthik.core.model.AccountType
import app.arthik.core.model.Channel
import app.arthik.core.model.Direction
import app.arthik.core.recurring.Occurrence
import app.arthik.core.recurring.RecurringDetector
import app.arthik.data.db.AccountEntity
import app.arthik.data.db.ActivityEntity
import app.arthik.data.db.ArthikDatabase
import app.arthik.data.db.BudgetEntity
import app.arthik.data.db.CategoryEntity
import app.arthik.data.db.MerchantRuleEntity
import app.arthik.data.db.OwnIdentityEntity
import app.arthik.data.db.RecurringEntity
import app.arthik.data.db.RecurringSource
import app.arthik.data.db.RecurringStatus
import app.arthik.data.db.SmsEntity
import app.arthik.data.db.SmsStatus
import app.arthik.data.db.TransactionEntity
import app.arthik.data.db.TxnRow
import app.arthik.data.db.TxnSource
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class TxnFilter(
    val period: Period,
    val categoryId: String? = null,
    val accountId: Long? = null,
    val direction: Direction? = null,
    val query: String? = null,
)

class TransactionRepository(private val db: ArthikDatabase) {
    fun paged(filter: TxnFilter): Flow<PagingData<TxnRow>> = Pager(PagingConfig(pageSize = 40, prefetchDistance = 20)) {
        db.transactions().page(
            filter.period.from, filter.period.to, filter.categoryId, filter.accountId, filter.direction?.name,
            filter.query?.trim()?.takeIf { it.isNotEmpty() },
        )
    }.flow

    fun observe(id: Long) = db.transactions().observe(id)
    suspend fun get(id: Long) = db.transactions().byId(id)
    suspend fun rawSms(txnId: Long): List<SmsEntity> = db.sms().forTransaction(txnId)

    /** How many other transactions from the same merchant would change if the rule is applied to the past. */
    suspend fun countSameMerchant(txn: TransactionEntity, categoryId: String): Int =
        txn.merchantKey?.let { db.transactions().countOtherCategoryForMerchant(it, categoryId) } ?: 0

    /**
     * Recategorise one transaction and learn "merchant -> category" for future ones. Past
     * transactions change only when [applyToPast] (the UI asks first).
     */
    suspend fun recategorise(txn: TransactionEntity, categoryId: String, applyToPast: Boolean) {
        db.transactions().update(txn.copy(categoryId = categoryId, userEdited = true))
        val key = txn.merchantKey ?: return
        db.rules().upsert(MerchantRuleEntity(key, txn.merchant ?: key, categoryId))
        if (applyToPast) db.transactions().recategoriseMerchant(key, categoryId)
    }

    suspend fun save(txn: TransactionEntity) {
        if (txn.id == 0L) db.transactions().insert(txn.copy(source = TxnSource.MANUAL, userEdited = true))
        else db.transactions().update(txn.copy(userEdited = true, approximate = false))
    }

    suspend fun delete(id: Long) = db.transactions().delete(id)

    suspend fun markOwn(txn: TransactionEntity) {
        val key = txn.merchantKey ?: return
        db.rules().upsertOwn(OwnIdentityEntity(key, txn.merchant ?: key))
        db.transactions().recategoriseMerchant(key, CategoryIds.TRANSFERS)
    }

    fun newManual(accountId: Long) = TransactionEntity(
        accountId = accountId, direction = Direction.DEBIT, amount = 0, date = LocalDate.now(), time = LocalDateTime.now(),
        merchant = null, merchantKey = null, categoryId = CategoryIds.OTHERS, reference = null, channel = Channel.OTHER,
        kind = null, source = TxnSource.MANUAL,
    )
}

class CategoryRepository(private val db: ArthikDatabase) {
    val categories: Flow<List<CategoryEntity>> = db.categories().observeAll()
    val rules: Flow<List<MerchantRuleEntity>> = db.rules().observeAll()
    suspend fun upsert(c: CategoryEntity) = db.categories().upsert(c)
    suspend fun deleteRule(key: String) = db.rules().delete(key)

    suspend fun add(name: String, color: Long, isIncome: Boolean) {
        val id = "custom_" + MerchantNormaliser.key(name).ifEmpty { System.currentTimeMillis().toString() }
        val order = (db.categories().all().maxOfOrNull { it.sortOrder } ?: 0) + 1
        db.categories().upsert(CategoryEntity(id, name, color, "tag", isIncome, order))
    }
}

data class NetWorth(val bank: Long, val investments: Long, val cardDues: Long, val loans: Long) {
    val total: Long get() = bank + investments - cardDues - loans
}

class AccountRepository(private val db: ArthikDatabase) {
    val accounts: Flow<List<AccountEntity>> = db.accounts().observeAll()
    fun observe(id: Long) = db.accounts().observe(id)
    fun recent(id: Long, limit: Int = 50) = db.transactions().observeForAccount(id, limit)
    fun activities(id: Long) = db.activities().observeForAccount(id)

    suspend fun update(a: AccountEntity) = db.accounts().update(a)

    /** Moves every transaction and activity of [from] into [into] and deletes [from]. */
    suspend fun merge(from: Long, into: Long) {
        db.transactions().moveAccount(from, into)
        db.activities().moveAccount(from, into)
        db.accounts().delete(from)
    }

    suspend fun addOpeningHolding(a: ActivityEntity) = db.activities().insert(a)

    /** Card outstanding: limit - available when known, else the last statement total. */
    fun cardOutstanding(a: AccountEntity): Long =
        if (a.creditLimit != null && a.availableLimit != null) (a.creditLimit - a.availableLimit).coerceAtLeast(0)
        else a.statementTotal ?: 0

    fun investedValue(activities: List<ActivityEntity>): Long = activities.sumOf {
        when (it.type) {
            ActivityType.BUY, ActivityType.DEPOSIT -> it.amount
            ActivityType.SELL, ActivityType.WITHDRAWAL -> -it.amount
            else -> 0L
        }
    }

    val netWorth: Flow<NetWorth> = combine(db.accounts().observeAll(), db.activities().observeAll()) { accounts, acts ->
        val visible = accounts.filterNot { it.hidden }
        val byAccount = acts.groupBy { it.accountId }
        NetWorth(
            bank = visible.filter { it.type in setOf(AccountType.SAVINGS, AccountType.WALLET, AccountType.FASTAG, AccountType.DEPOSIT) }
                .sumOf { it.balance ?: 0 },
            investments = visible.filter { it.type in setOf(AccountType.INVESTMENT, AccountType.EPF, AccountType.NPS) }
                .sumOf { it.balance ?: investedValue(byAccount[it.id].orEmpty()) },
            cardDues = visible.filter { it.type == AccountType.CREDIT_CARD }.sumOf { cardOutstanding(it) },
            loans = visible.filter { it.type == AccountType.LOAN }.sumOf { it.loanOutstanding ?: 0 },
        )
    }
}

data class Dashboard(
    val period: Period,
    val spend: Long,
    val income: Long,
    val lastPeriodSpend: Long,
    val byCategory: List<app.arthik.data.db.CategoryTotal>,
)

class InsightsRepository(private val db: ArthikDatabase) {
    fun dashboard(period: Period, previous: Period): Flow<Dashboard> = combine(
        db.transactions().observeSpendByCategory(period.from, period.to),
        db.transactions().observeIncome(period.from, period.to),
        db.transactions().observeSpendByCategory(previous.from, previous.to),
    ) { cats, income, prev ->
        Dashboard(period, cats.sumOf { it.total }, income, prev.sumOf { it.total }, cats)
    }

    fun daily(period: Period) = db.transactions().observeDailySpend(period.from, period.to)
    fun topMerchants(period: Period, limit: Int = 8) = db.transactions().observeTopMerchants(period.from, period.to, limit)
    fun byCategory(period: Period) = db.transactions().observeSpendByCategory(period.from, period.to)
    suspend fun spendByCategory(period: Period) = db.transactions().spendByCategory(period.from, period.to)
}

class BudgetRepository(private val db: ArthikDatabase) {
    val budgets: Flow<List<BudgetEntity>> = db.budgets().observeAll()
    suspend fun set(categoryId: String, amount: Long) =
        if (amount <= 0) db.budgets().delete(categoryId) else db.budgets().upsert(BudgetEntity(categoryId, amount))
    suspend fun all() = db.budgets().all()
    suspend fun update(b: BudgetEntity) = db.budgets().upsert(b)
}

class RecurringRepository(private val db: ArthikDatabase) {
    val active: Flow<List<RecurringEntity>> = db.recurring().observeActive()
    suspend fun setStatus(key: String, status: RecurringStatus) = db.recurring().setStatus(key, status)

    /** Re-runs detection over the last 13 months; keeps the user's confirm/dismiss decisions. */
    suspend fun refresh() {
        val debits = db.transactions().debitsSince(LocalDate.now().minusMonths(13))
            .filter { it.categoryId != CategoryIds.TRANSFERS }
        val existing = db.recurring().all().associateBy { it.merchantKey }
        RecurringDetector.detect(debits.map { Occurrence(it.merchantKey!!, it.merchant ?: it.merchantKey, it.amount, it.date) })
            .forEach { r ->
                val old = existing[r.merchantKey]
                if (old?.source == RecurringSource.MANDATE && old.nextDate != null && old.nextDate > r.nextDate) return@forEach
                db.recurring().upsert(
                    RecurringEntity(
                        merchantKey = r.merchantKey, merchant = r.merchant, cadence = r.cadence.name, amount = r.typicalAmount,
                        lastDate = r.lastDate, nextDate = r.nextDate, status = old?.status ?: RecurringStatus.DETECTED,
                        source = old?.source ?: RecurringSource.DETECTED, occurrences = r.occurrences,
                    )
                )
            }
    }
}

class SmsRepository(private val db: ArthikDatabase) {
    val unrecognised: Flow<List<SmsEntity>> = db.sms().observeUnrecognised()
    val unrecognisedCount: Flow<Int> = db.sms().observeUnrecognisedCount()
    suspend fun ignore(id: Long) = db.sms().setStatus(id, SmsStatus.IGNORED_BY_USER)
    suspend fun allUnrecognised() = db.sms().unrecognised()
}

/** Anonymises unrecognised SMS before the user shares them for new rules (spec 14.5). */
object SmsAnonymiser {
    private val vpa = Regex("""\b[\w.\-]+@([a-zA-Z]+)\b""")
    private val longNumber = Regex("""\b\d{5,}(\d{4})\b""")
    private val nameAfter = Regex("""(?i)\b(from|to|by|; )\s*([A-Z][A-Za-z]+(?: [A-Z][A-Za-z]+){0,3})(?= credited|\.| on |$)""")

    fun anonymise(body: String): String = body
        .replace(vpa) { "xxxx@${it.groupValues[1]}" }
        .replace(longNumber) { "X".repeat(it.value.length - 4) + it.groupValues[1] }
        .replace(nameAfter) { "${it.groupValues[1]} NAME" }
}

fun Flow<List<CategoryEntity>>.asMap(): Flow<Map<String, CategoryEntity>> = map { list -> list.associateBy { it.id } }
