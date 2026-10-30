package app.arthik.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, SmsEntity::class, CategoryEntity::class,
        MerchantRuleEntity::class, OwnIdentityEntity::class, BudgetEntity::class, RecurringEntity::class,
        ActivityEntity::class, FxRateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ArthikDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun sms(): SmsDao
    abstract fun categories(): CategoryDao
    abstract fun rules(): RuleDao
    abstract fun budgets(): BudgetDao
    abstract fun recurring(): RecurringDao
    abstract fun activities(): ActivityDao
    abstract fun fx(): FxDao
    abstract fun maintenance(): MaintenanceDao

    companion object {
        const val NAME = "arthik.db"

        /** SQLCipher-encrypted Room database. The passphrase comes from [app.arthik.data.security.DatabaseKey]. */
        fun open(context: Context, passphrase: ByteArray): ArthikDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, ArthikDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
