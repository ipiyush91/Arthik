package app.arthik.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.arthik.core.model.AccountType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY type, institution, name")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts")
    suspend fun all(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observe(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE institution = :institution AND type = :type")
    suspend fun byInstitution(institution: String, type: AccountType): List<AccountEntity>

    @Insert suspend fun insert(a: AccountEntity): Long
    @Update suspend fun update(a: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Row used by the transaction list (joined with category and account for display). */
data class TxnRow(
    val id: Long,
    val accountId: Long,
    val accountName: String,
    val direction: String,
    val amount: Long,
    val myShare: Long?,
    val date: LocalDate,
    val merchant: String?,
    val categoryId: String,
    val categoryName: String,
    val categoryColor: Long,
    val approximate: Boolean,
    val personToPerson: Boolean,
    val linkType: String,
)

data class CategoryTotal(val categoryId: String, val name: String, val color: Long, val isIncome: Boolean, val total: Long)
data class DayTotal(val date: LocalDate, val total: Long)
data class MerchantTotal(val merchant: String, val total: Long, val count: Int)

@Dao
interface TransactionDao {
    @Insert suspend fun insert(t: TransactionEntity): Long
    @Update suspend fun update(t: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun observe(id: Long): Flow<TransactionEntity?>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """SELECT t.id, t.accountId, a.name AS accountName, t.direction, t.amount, t.myShare, t.date, t.merchant,
           t.categoryId, c.name AS categoryName, c.color AS categoryColor, t.approximate, t.personToPerson, t.linkType
           FROM transactions t JOIN accounts a ON a.id = t.accountId JOIN categories c ON c.id = t.categoryId
           WHERE t.date BETWEEN :from AND :to
             AND (:categoryId IS NULL OR t.categoryId = :categoryId)
             AND (:accountId IS NULL OR t.accountId = :accountId)
             AND (:direction IS NULL OR t.direction = :direction)
             AND (:query IS NULL OR t.merchant LIKE '%' || :query || '%' OR t.note LIKE '%' || :query || '%')
           ORDER BY t.date DESC, t.time DESC, t.id DESC"""
    )
    fun page(
        from: LocalDate, to: LocalDate, categoryId: String?, accountId: Long?, direction: String?, query: String?,
    ): PagingSource<Int, TxnRow>

    /** Candidates for dedupe / transfer matching around a time. */
    @Query("SELECT * FROM transactions WHERE amount = :amount AND time BETWEEN :from AND :to")
    suspend fun sameAmountBetween(amount: Long, from: LocalDateTime, to: LocalDateTime): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE reference = :reference")
    suspend fun byReference(reference: String): List<TransactionEntity>

    @Query(
        """SELECT * FROM transactions WHERE merchantKey = :merchantKey AND direction = 'DEBIT'
           AND date BETWEEN :from AND :to ORDER BY date DESC"""
    )
    suspend fun debitsForMerchant(merchantKey: String, from: LocalDate, to: LocalDate): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE merchantKey = :merchantKey AND userEdited = 0")
    suspend fun uneditedForMerchant(merchantKey: String): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE merchantKey = :merchantKey AND userEdited = 0")
    suspend fun recategoriseMerchant(merchantKey: String, categoryId: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE merchantKey = :merchantKey AND categoryId != :categoryId")
    suspend fun countOtherCategoryForMerchant(merchantKey: String, categoryId: String): Int

    @Query("SELECT * FROM transactions WHERE direction = 'DEBIT' AND date >= :from AND merchantKey IS NOT NULL")
    suspend fun debitsSince(from: LocalDate): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC, time DESC LIMIT :limit")
    fun observeForAccount(accountId: Long, limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date")
    suspend fun all(): List<TransactionEntity>

    @Query("UPDATE transactions SET accountId = :into WHERE accountId = :from")
    suspend fun moveAccount(from: Long, into: Long)

    // Spending categories: debits minus credits (refunds net against their category); excludes
    // Transfers/Investments, hidden accounts, and uses "my share" when set.
    @Query(
        """SELECT c.id AS categoryId, c.name, c.color, c.isIncome,
           SUM(CASE WHEN t.direction = 'DEBIT' THEN COALESCE(t.myShare, t.amount) ELSE -COALESCE(t.myShare, t.amount) END) AS total
           FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
           WHERE t.date BETWEEN :from AND :to AND a.hidden = 0 AND c.isIncome = 0
             AND c.id NOT IN ('transfers', 'investments')
           GROUP BY c.id HAVING total > 0 ORDER BY total DESC"""
    )
    fun observeSpendByCategory(from: LocalDate, to: LocalDate): Flow<List<CategoryTotal>>

    @Query(
        """SELECT c.id AS categoryId, c.name, c.color, c.isIncome,
           SUM(CASE WHEN t.direction = 'DEBIT' THEN COALESCE(t.myShare, t.amount) ELSE -COALESCE(t.myShare, t.amount) END) AS total
           FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
           WHERE t.date BETWEEN :from AND :to AND a.hidden = 0 AND c.isIncome = 0
             AND c.id NOT IN ('transfers', 'investments')
           GROUP BY c.id HAVING total > 0 ORDER BY total DESC"""
    )
    suspend fun spendByCategory(from: LocalDate, to: LocalDate): List<CategoryTotal>

    @Query(
        """SELECT COALESCE(SUM(t.amount), 0) FROM transactions t JOIN categories c ON c.id = t.categoryId
           JOIN accounts a ON a.id = t.accountId
           WHERE t.date BETWEEN :from AND :to AND a.hidden = 0 AND c.isIncome = 1 AND t.direction = 'CREDIT'"""
    )
    fun observeIncome(from: LocalDate, to: LocalDate): Flow<Long>

    @Query(
        """SELECT t.date AS date,
           SUM(CASE WHEN t.direction = 'DEBIT' THEN COALESCE(t.myShare, t.amount) ELSE -COALESCE(t.myShare, t.amount) END) AS total
           FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
           WHERE t.date BETWEEN :from AND :to AND a.hidden = 0 AND c.isIncome = 0
             AND c.id NOT IN ('transfers', 'investments')
           GROUP BY t.date ORDER BY t.date"""
    )
    fun observeDailySpend(from: LocalDate, to: LocalDate): Flow<List<DayTotal>>

    @Query(
        """SELECT COALESCE(t.merchant, 'Unknown') AS merchant, SUM(COALESCE(t.myShare, t.amount)) AS total, COUNT(*) AS count
           FROM transactions t JOIN categories c ON c.id = t.categoryId JOIN accounts a ON a.id = t.accountId
           WHERE t.date BETWEEN :from AND :to AND a.hidden = 0 AND t.direction = 'DEBIT' AND c.isIncome = 0
             AND c.id NOT IN ('transfers', 'investments')
           GROUP BY t.merchantKey ORDER BY total DESC LIMIT :limit"""
    )
    fun observeTopMerchants(from: LocalDate, to: LocalDate, limit: Int): Flow<List<MerchantTotal>>
}

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(s: SmsEntity): Long
    @Update suspend fun update(s: SmsEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM sms WHERE providerId = :providerId)")
    suspend fun exists(providerId: String): Boolean

    @Query("SELECT * FROM sms WHERE id = :id")
    suspend fun byId(id: Long): SmsEntity?

    @Query("SELECT * FROM sms WHERE transactionId = :txnId")
    suspend fun forTransaction(txnId: Long): List<SmsEntity>

    @Query("SELECT * FROM sms WHERE status = 'UNRECOGNISED' ORDER BY receivedAt DESC")
    fun observeUnrecognised(): Flow<List<SmsEntity>>

    @Query("SELECT * FROM sms WHERE status = 'UNRECOGNISED' ORDER BY receivedAt DESC")
    suspend fun unrecognised(): List<SmsEntity>

    @Query("SELECT COUNT(*) FROM sms WHERE status = 'UNRECOGNISED'")
    fun observeUnrecognisedCount(): Flow<Int>

    @Query("SELECT MAX(receivedAt) FROM sms")
    suspend fun latestReceivedAt(): LocalDateTime?

    @Query("UPDATE sms SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: SmsStatus)

    @Query("SELECT * FROM sms")
    suspend fun all(): List<SmsEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isIncome, sortOrder")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isIncome, sortOrder")
    suspend fun all(): List<CategoryEntity>

    @Upsert suspend fun upsert(c: CategoryEntity)
    @Upsert suspend fun upsertAll(c: List<CategoryEntity>)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM merchant_rules") suspend fun all(): List<MerchantRuleEntity>
    @Query("SELECT * FROM merchant_rules ORDER BY merchant") fun observeAll(): Flow<List<MerchantRuleEntity>>
    @Upsert suspend fun upsert(r: MerchantRuleEntity)
    @Query("DELETE FROM merchant_rules WHERE merchantKey = :key") suspend fun delete(key: String)

    @Query("SELECT * FROM own_identities") suspend fun ownIdentities(): List<OwnIdentityEntity>
    @Upsert suspend fun upsertOwn(o: OwnIdentityEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets") fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets") suspend fun all(): List<BudgetEntity>
    @Upsert suspend fun upsert(b: BudgetEntity)
    @Query("DELETE FROM budgets WHERE categoryId = :categoryId") suspend fun delete(categoryId: String)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring WHERE status != 'DISMISSED' ORDER BY nextDate")
    fun observeActive(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring") suspend fun all(): List<RecurringEntity>
    @Upsert suspend fun upsert(r: RecurringEntity)
    @Query("UPDATE recurring SET status = :status WHERE merchantKey = :key")
    suspend fun setStatus(key: String, status: RecurringStatus)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE accountId = :accountId ORDER BY date DESC")
    fun observeForAccount(accountId: Long): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY date") suspend fun all(): List<ActivityEntity>
    @Query("SELECT * FROM activities ORDER BY date") fun observeAll(): Flow<List<ActivityEntity>>
    @Insert suspend fun insert(a: ActivityEntity): Long
    @Insert suspend fun insertAll(a: List<ActivityEntity>)
    @Update suspend fun update(a: ActivityEntity)
    @Query("DELETE FROM activities WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE activities SET accountId = :into WHERE accountId = :from") suspend fun moveAccount(from: Long, into: Long)
}

@Dao
interface FxDao {
    @Query("SELECT * FROM fx_rates") suspend fun all(): List<FxRateEntity>
    @Query("SELECT * FROM fx_rates") fun observeAll(): Flow<List<FxRateEntity>>
    @Upsert suspend fun upsert(r: FxRateEntity)
}

@Dao
interface MaintenanceDao {
    @Transaction
    suspend fun clearAll() {
        clearTransactions(); clearSms(); clearAccounts(); clearRules(); clearBudgets(); clearRecurring(); clearActivities()
        clearOwn(); clearFx()
    }

    @Insert suspend fun insertAccounts(v: List<AccountEntity>)
    @Insert suspend fun insertTransactions(v: List<TransactionEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSms(v: List<SmsEntity>)
    @Upsert suspend fun upsertCategories(v: List<CategoryEntity>)
    @Insert suspend fun insertRules(v: List<MerchantRuleEntity>)
    @Insert suspend fun insertOwn(v: List<OwnIdentityEntity>)
    @Insert suspend fun insertBudgets(v: List<BudgetEntity>)
    @Insert suspend fun insertRecurring(v: List<RecurringEntity>)
    @Insert suspend fun insertActivities(v: List<ActivityEntity>)
    @Insert suspend fun insertFx(v: List<FxRateEntity>)

    @Query("SELECT * FROM merchant_rules") suspend fun rules(): List<MerchantRuleEntity>
    @Query("SELECT * FROM own_identities") suspend fun own(): List<OwnIdentityEntity>
    @Query("SELECT * FROM budgets") suspend fun budgets(): List<BudgetEntity>
    @Query("SELECT * FROM recurring") suspend fun recurring(): List<RecurringEntity>

    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM sms") suspend fun clearSms()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM merchant_rules") suspend fun clearRules()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM activities") suspend fun clearActivities()
    @Query("DELETE FROM own_identities") suspend fun clearOwn()
    @Query("DELETE FROM fx_rates") suspend fun clearFx()
}
