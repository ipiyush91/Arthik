package app.arthik.backup

import android.content.Context
import android.net.Uri
import app.arthik.core.csv.Activity
import app.arthik.core.csv.TransactionRow
import app.arthik.core.csv.TransactionsCsv
import app.arthik.core.csv.WealthfolioCsv
import app.arthik.core.model.AccountType
import app.arthik.data.db.ActivityEntity
import app.arthik.data.db.ArthikDatabase
import app.arthik.data.db.AccountEntity

/** Wealthfolio CSV (investment accounts only) and a generic transactions CSV, through SAF uris. */
class CsvIo(private val context: Context, private val db: ArthikDatabase) {
    private val investmentTypes = setOf(AccountType.INVESTMENT, AccountType.EPF, AccountType.NPS, AccountType.DEPOSIT)

    suspend fun exportWealthfolio(uri: Uri): Int {
        val accounts = db.accounts().all().associateBy { it.id }
        val acts = db.activities().all().filter { accounts[it.accountId]?.type in investmentTypes }
        val rows = acts.map {
            Activity(
                date = it.date, accountName = accounts[it.accountId]?.name.orEmpty(), symbol = it.symbol,
                activityType = it.type, quantity = it.quantity, unitPrice = it.unitPrice, amount = it.amount / 100.0,
                fee = it.fee / 100.0,
            )
        }
        write(uri, WealthfolioCsv.export(rows))
        return rows.size
    }

    /** Imports activities; accounts are matched by name or created as investment accounts. */
    suspend fun importWealthfolio(uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val parsed = WealthfolioCsv.import(text, defaultAccount = "Wealthfolio import")
        val byName = db.accounts().all().associateBy { it.name }.toMutableMap()
        val entities = parsed.map { a ->
            val account = byName[a.accountName] ?: AccountEntity(
                institution = "Wealthfolio", type = AccountType.INVESTMENT, last4 = a.accountName.takeLast(4), name = a.accountName,
            ).let { it.copy(id = db.accounts().insert(it)) }.also { byName[a.accountName] = it }
            ActivityEntity(
                accountId = account.id, date = a.date, type = a.activityType, symbol = a.symbol, name = a.symbol,
                quantity = a.quantity, unitPrice = a.unitPrice, amount = Math.round(a.amount * 100), fee = Math.round(a.fee * 100),
                symbolVerified = true,
            )
        }
        db.activities().insertAll(entities)
        return entities.size
    }

    suspend fun exportTransactions(uri: Uri): Int {
        val accounts = db.accounts().all().associateBy { it.id }
        val cats = db.categories().all().associateBy { it.id }
        val rows = db.transactions().all().map { t ->
            TransactionRow(
                date = t.date, account = accounts[t.accountId]?.name.orEmpty(), direction = t.direction.name,
                amount = (t.myShare ?: t.amount) / 100.0, category = cats[t.categoryId]?.name ?: t.categoryId,
                merchant = t.merchant.orEmpty(), reference = t.reference.orEmpty(), note = t.note.orEmpty(),
            )
        }
        write(uri, TransactionsCsv.export(rows))
        return rows.size
    }

    fun writeText(uri: Uri, text: String) = write(uri, text)

    private fun write(uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
    }
}
