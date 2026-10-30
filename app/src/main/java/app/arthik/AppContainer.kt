package app.arthik

import android.content.Context
import app.arthik.backup.BackupManager
import app.arthik.backup.CsvIo
import app.arthik.core.categorise.Categoriser
import app.arthik.core.categorise.DefaultCategories
import app.arthik.core.parse.SmsParser
import app.arthik.core.rules.RulePack
import app.arthik.core.rules.RuleRepository
import app.arthik.data.db.ArthikDatabase
import app.arthik.data.db.CategoryEntity
import app.arthik.data.prefs.Settings
import app.arthik.data.repo.AccountRepository
import app.arthik.data.repo.BudgetRepository
import app.arthik.data.repo.CategoryRepository
import app.arthik.data.repo.IngestService
import app.arthik.data.repo.InsightsRepository
import app.arthik.data.repo.RecurringRepository
import app.arthik.data.repo.SmsRepository
import app.arthik.data.repo.TransactionRepository
import app.arthik.data.security.DatabaseKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency injection: everything is created lazily, once. */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = Settings(context)
    val databaseKey = DatabaseKey(context)

    /** Throws [DatabaseKey.KeyLostException] when the Keystore key is gone; the UI then offers restore. */
    val db: ArthikDatabase by lazy {
        ArthikDatabase.open(context, databaseKey.passphrase()).also { db ->
            appScope.launch(Dispatchers.IO) { seedCategories(db) }
        }
    }

    fun databaseAvailable(): Boolean = runCatching { db.openHelper.writableDatabase; true }.getOrDefault(false)

    private val rulesDir get() = File(context.filesDir, "rules").apply { mkdirs() }

    @Volatile private var parser: SmsParser? = null

    /** Built-in rule packs plus packs the user imported (Settings → Import rule pack). */
    fun parser(): SmsParser = parser ?: synchronized(this) {
        parser ?: SmsParser(RuleRepository.fromResources(importedPacks())).also { parser = it }
    }

    fun importedPacks(): List<RulePack> = rulesDir.listFiles { f -> f.extension == "json" }.orEmpty()
        .mapNotNull { runCatching { RuleRepository.parsePack(it.readText()) }.getOrNull() }

    /** Validates and stores an imported rule pack; returns the number of rules. */
    fun importRulePack(name: String, text: String): Int {
        val pack = RuleRepository.parsePack(text)
        pack.rules.forEach { r -> r.patterns.forEach { app.arthik.core.rules.TemplateCompiler.compile(it) } }
        File(rulesDir, name.substringAfterLast('/').ifEmpty { "imported" }.removeSuffix(".json") + ".json").writeText(text)
        synchronized(this) { parser = null }
        return pack.rules.size
    }

    val categoriser: Categoriser by lazy { Categoriser.fromResources() }
    val ingest by lazy { IngestService(db, ::parser, categoriser) }
    val transactions by lazy { TransactionRepository(db) }
    val categories by lazy { CategoryRepository(db) }
    val accounts by lazy { AccountRepository(db) }
    val insights by lazy { InsightsRepository(db) }
    val budgets by lazy { BudgetRepository(db) }
    val recurring by lazy { RecurringRepository(db) }
    val sms by lazy { SmsRepository(db) }
    val backup by lazy { BackupManager(context, this) }
    val csv by lazy { CsvIo(context, db) }

    private suspend fun seedCategories(db: ArthikDatabase) {
        if (db.categories().count() > 0) return
        db.categories().upsertAll(DefaultCategories.all.mapIndexed { i, c ->
            CategoryEntity(c.id, c.name, c.colorArgb, c.icon, c.isIncome, i)
        })
    }
}
