package app.arthik.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.arthik.container
import app.arthik.core.csv.ActivityType
import app.arthik.core.model.AccountType
import app.arthik.core.model.Direction
import app.arthik.core.text.Amounts
import app.arthik.data.db.AccountEntity
import app.arthik.data.db.ActivityEntity
import app.arthik.ui.Routes
import app.arthik.ui.components.Amount
import app.arthik.ui.components.EmptyState
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.Meter
import app.arthik.ui.components.ScreenList
import app.arthik.ui.components.SectionTitle
import app.arthik.ui.components.glass
import app.arthik.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

private val groups = listOf(
    "Savings & wallets" to setOf(AccountType.SAVINGS, AccountType.WALLET, AccountType.FASTAG, AccountType.DEPOSIT, AccountType.UNKNOWN),
    "Credit cards" to setOf(AccountType.CREDIT_CARD),
    "Investments" to setOf(AccountType.INVESTMENT, AccountType.EPF, AccountType.NPS),
    "Loans" to setOf(AccountType.LOAN),
)

@Composable
fun AccountsScreen(nav: NavController) {
    val c = androidx.compose.ui.platform.LocalContext.current.container
    val accounts by c.accounts.accounts.collectAsStateWithLifecycle(emptyList())
    val glass = LocalGlass.current
    ScreenList("Accounts") {
        if (accounts.isEmpty()) item(key = "empty") {
            EmptyState("No accounts yet", "Accounts are created automatically from your bank, card and investment SMS.")
        }
        groups.forEach { (title, types) ->
            val list = accounts.filter { it.type in types }
            if (list.isEmpty()) return@forEach
            item(key = "g_$title") { SectionTitle(title) }
            list.forEach { a ->
                item(key = "a_${a.id}", contentType = a.type.name) {
                    GlassCard(onClick = { nav.navigate(Routes.account(a.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name + if (a.hidden) " (hidden)" else "", style = MaterialTheme.typography.titleMedium)
                                Text(a.institution, style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                            }
                            when (a.type) {
                                AccountType.CREDIT_CARD -> Amount(c.accounts.cardOutstanding(a))
                                AccountType.LOAN -> a.loanOutstanding?.let { Amount(it) }
                                else -> a.balance?.let { Amount(it) } ?: Text("—", color = glass.textSecondary)
                            }
                        }
                        if (a.type == AccountType.CREDIT_CARD) CardSummary(a)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSummary(a: AccountEntity) {
    val glass = LocalGlass.current
    val limit = a.creditLimit
    if (limit != null && a.availableLimit != null && limit > 0) {
        val used = (limit - a.availableLimit).coerceAtLeast(0)
        Meter(used.toFloat() / limit, Modifier.padding(top = 10.dp))
        Text("${used * 100 / limit}% of ${Amounts.formatInr(limit)} used", style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
    } else if (a.availableLimit != null) {
        Text("Available ${Amounts.formatInr(a.availableLimit)}", style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
    }
    a.dueDate?.let { due ->
        val left = ChronoUnit.DAYS.between(LocalDate.now(), due)
        val text = buildString {
            a.statementTotal?.let { append("Statement ${Amounts.formatInr(it)} · ") }
            a.statementMin?.let { append("min ${Amounts.formatInr(it)} · ") }
            append(if (left >= 0) "due in $left days" else "was due ${due.format(DateTimeFormatter.ofPattern("d MMM"))}")
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (left in 0..5) glass.warning else glass.textSecondary)
    }
    if (a.statementNeedsInput) Text("Statement ready — open to enter the amount", style = MaterialTheme.typography.bodySmall, color = glass.warning)
}

@Composable
fun AccountDetailScreen(id: Long, nav: NavController) {
    val c = androidx.compose.ui.platform.LocalContext.current.container
    val account by remember(id) { c.accounts.observe(id) }.collectAsStateWithLifecycle(null)
    val recent by remember(id) { c.accounts.recent(id) }.collectAsStateWithLifecycle(emptyList())
    val acts by remember(id) { c.accounts.activities(id) }.collectAsStateWithLifecycle(emptyList())
    val all by c.accounts.accounts.collectAsStateWithLifecycle(emptyList())
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var merge by remember { mutableStateOf(false) }
    var addHolding by remember { mutableStateOf(false) }
    val a = account ?: return

    ScreenList(a.name, onBack = { nav.popBackStack() }) {
        item(key = "head") {
            GlassCard {
                Text(a.institution + " · " + a.type.name.lowercase().replace('_', ' '), color = glass.textSecondary)
                when (a.type) {
                    AccountType.CREDIT_CARD -> { Amount(c.accounts.cardOutstanding(a), style = MaterialTheme.typography.headlineMedium); CardSummary(a) }
                    AccountType.INVESTMENT, AccountType.EPF, AccountType.NPS -> {
                        Text("Invested", color = glass.textSecondary)
                        Amount(a.balance ?: c.accounts.investedValue(acts), style = MaterialTheme.typography.headlineMedium)
                        Text("From SMS only — may be incomplete. Add opening holdings for accuracy.", style = MaterialTheme.typography.bodySmall,
                            color = glass.warning)
                    }
                    AccountType.LOAN -> Amount(a.loanOutstanding ?: 0L, style = MaterialTheme.typography.headlineMedium)
                    else -> {
                        a.balance?.let { Amount(it, style = MaterialTheme.typography.headlineMedium) } ?: Text("Balance not seen in SMS yet")
                        a.balanceAt?.let { Text("as of ${it.format(DateTimeFormatter.ofPattern("d MMM, h:mm a"))}", style = MaterialTheme.typography.bodySmall, color = glass.textSecondary) }
                        val since = LocalDate.now().withDayOfMonth(1)
                        val month = recent.filter { !it.date.isBefore(since) }
                        Text("This month: in ${Amounts.formatInr(month.filter { it.direction == Direction.CREDIT }.sumOf { it.amount })} · " +
                            "out ${Amounts.formatInr(month.filter { it.direction == Direction.DEBIT }.sumOf { it.amount })}",
                            style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { edit = true }, Modifier.weight(1f)) { Text("Edit") }
                    OutlinedButton(onClick = { merge = true }, Modifier.weight(1f)) { Text("Merge") }
                    OutlinedButton(onClick = { nav.navigate(Routes.transactions(account = a.id)) }, Modifier.weight(1f)) { Text("All txns") }
                }
            }
        }
        if (a.type in setOf(AccountType.INVESTMENT, AccountType.EPF, AccountType.NPS)) {
            item(key = "acts_title") {
                SectionTitle("Activity") { TextButton(onClick = { addHolding = true }) { Text("Add opening holding") } }
            }
            if (acts.isEmpty()) item(key = "acts_empty") { EmptyState("No activity yet", "Buys, SIPs, dividends and contributions from SMS appear here.") }
            acts.forEach { act ->
                item(key = "act_${act.id}") {
                    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(act.name, style = MaterialTheme.typography.bodyLarge)
                            Text("${act.type.name.lowercase()} · ${act.date}" + if (act.quantity > 0) " · ${act.quantity} @ ${act.unitPrice}" else "",
                                style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                        }
                        Amount(act.amount)
                    }
                }
            }
        } else {
            item(key = "recent_title") { SectionTitle("Recent") }
            if (recent.isEmpty()) item(key = "recent_empty") { EmptyState("No transactions", "Nothing recorded for this account yet.") }
            recent.take(20).forEach { t ->
                item(key = "t_${t.id}") {
                    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t.merchant ?: "Unknown", style = MaterialTheme.typography.bodyLarge)
                            Text(t.date.format(DateTimeFormatter.ofPattern("d MMM")), style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                        }
                        Amount(t.amount, color = if (t.direction == Direction.CREDIT) glass.positive else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }

    if (edit) EditAccountDialog(a, onDismiss = { edit = false }) { updated -> scope.launch { c.accounts.update(updated); edit = false } }
    if (merge) MergeDialog(a, all.filter { it.id != a.id && it.type == a.type }, onDismiss = { merge = false }) { into ->
        scope.launch { c.accounts.merge(a.id, into); merge = false; nav.popBackStack() }
    }
    if (addHolding) OpeningHoldingDialog(onDismiss = { addHolding = false }) { name, qty, price ->
        scope.launch {
            c.accounts.addOpeningHolding(ActivityEntity(accountId = a.id, date = LocalDate.now(), type = ActivityType.BUY, symbol = name, name = name,
                quantity = qty, unitPrice = price, amount = Math.round(qty * price * 100)))
            addHolding = false
        }
    }
}

@Composable
private fun EditAccountDialog(a: AccountEntity, onDismiss: () -> Unit, onSave: (AccountEntity) -> Unit) {
    var name by remember { mutableStateOf(a.name) }
    var hidden by remember { mutableStateOf(a.hidden) }
    var balance by remember { mutableStateOf(a.balance?.let { (it / 100.0).toString() }.orEmpty()) }
    var limit by remember { mutableStateOf(a.creditLimit?.let { (it / 100.0).toString() }.orEmpty()) }
    var total by remember { mutableStateOf(a.statementTotal?.let { (it / 100.0).toString() }.orEmpty()) }
    var due by remember { mutableStateOf(a.dueDate?.toString().orEmpty()) }
    var loan by remember { mutableStateOf(a.loanOutstanding?.let { (it / 100.0).toString() }.orEmpty()) }
    val money = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                when (a.type) {
                    AccountType.CREDIT_CARD -> {
                        OutlinedTextField(limit, { limit = it }, label = { Text("Credit limit (₹)") }, singleLine = true, keyboardOptions = money)
                        OutlinedTextField(total, { total = it }, label = { Text("Statement total (₹)") }, singleLine = true, keyboardOptions = money)
                        OutlinedTextField(due, { due = it }, label = { Text("Due date (YYYY-MM-DD)") }, singleLine = true)
                    }
                    AccountType.LOAN -> OutlinedTextField(loan, { loan = it }, label = { Text("Outstanding (₹)") }, singleLine = true, keyboardOptions = money)
                    else -> OutlinedTextField(balance, { balance = it }, label = { Text("Current balance (₹)") }, singleLine = true, keyboardOptions = money)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide from totals", Modifier.weight(1f)); Switch(hidden, { hidden = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val dueDate = runCatching { LocalDate.parse(due) }.getOrNull()
                onSave(
                    a.copy(
                        name = name.trim().ifEmpty { a.name }, hidden = hidden,
                        balance = Amounts.toPaise(balance) ?: a.balance,
                        balanceAt = if (Amounts.toPaise(balance) != a.balance) java.time.LocalDateTime.now() else a.balanceAt,
                        creditLimit = Amounts.toPaise(limit) ?: a.creditLimit,
                        statementTotal = Amounts.toPaise(total) ?: a.statementTotal,
                        dueDate = dueDate ?: a.dueDate,
                        statementNeedsInput = a.statementNeedsInput && Amounts.toPaise(total) == null,
                        loanOutstanding = Amounts.toPaise(loan) ?: a.loanOutstanding,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MergeDialog(a: AccountEntity, targets: List<AccountEntity>, onDismiss: () -> Unit, onMerge: (Long) -> Unit) {
    var target by remember { mutableStateOf<Long?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge ${a.name} into…") },
        text = {
            if (targets.isEmpty()) Text("There is no other account of the same type.")
            else ChipFlow(targets.map { it.id.toString() to it.name }, target?.toString().orEmpty()) { target = it.toLong() }
        },
        confirmButton = { TextButton(enabled = target != null, onClick = { onMerge(target!!) }) { Text("Merge") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OpeningHoldingDialog(onDismiss: () -> Unit, onSave: (String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opening holding") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Fund / stock (symbol or name)") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("Units / quantity") }, singleLine = true, keyboardOptions = num)
                OutlinedTextField(price, { price = it }, label = { Text("Average cost per unit (₹)") }, singleLine = true, keyboardOptions = num)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && qty.toDoubleOrNull() != null && price.toDoubleOrNull() != null,
                onClick = { onSave(name.trim(), qty.toDouble(), price.toDouble()) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
