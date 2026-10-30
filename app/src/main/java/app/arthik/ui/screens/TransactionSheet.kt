package app.arthik.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.arthik.AppContainer
import app.arthik.core.categorise.MerchantNormaliser
import app.arthik.core.model.Direction
import app.arthik.core.text.Amounts
import app.arthik.data.db.AccountEntity
import app.arthik.data.db.CategoryEntity
import app.arthik.data.db.SmsEntity
import app.arthik.data.db.TransactionEntity
import app.arthik.ui.components.Amount
import app.arthik.ui.components.glass
import app.arthik.ui.theme.AmountStyle
import app.arthik.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Detail + edit sheet. [id] null = create a manual transaction. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSheet(
    id: Long?,
    c: AppContainer,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val glass = LocalGlass.current
    var txn by remember { mutableStateOf<TransactionEntity?>(null) }
    var sms by remember { mutableStateOf<List<SmsEntity>>(emptyList()) }
    var editing by remember { mutableStateOf(id == null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pickCategory by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        txn = if (id == null) c.transactions.newManual(accounts.firstOrNull()?.id ?: 0L) else c.transactions.get(id)
        if (id != null) sms = c.transactions.rawSms(id)
    }
    val t = txn ?: return
    val cat = categories.firstOrNull { it.id == t.categoryId }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            if (!editing) {
                Text(t.merchant ?: "Unknown", style = MaterialTheme.typography.titleLarge)
                Amount(t.myShare ?: t.amount, style = AmountStyle, approximate = t.approximate,
                    color = if (t.direction == Direction.CREDIT) glass.positive else MaterialTheme.colorScheme.onSurface)
                if (t.myShare != null) Text("My share of ${Amounts.formatInr(t.amount)}", color = glass.textSecondary)
                if (t.foreignCurrency != null) Text("Originally ${t.foreignCurrency} ${t.foreignAmount?.let { it / 100.0 }} · converted at your rate",
                    color = glass.textSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer8()
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(16.dp)).clickable { pickCategory = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).background(Color((cat?.color ?: 0xFFB0BEC5).toInt()), CircleShape))
                    Text(cat?.name ?: t.categoryId, Modifier.weight(1f).padding(start = 10.dp))
                    Text("Change", color = MaterialTheme.colorScheme.primary)
                }
                Spacer8()
                Detail("Date", t.date.format(DateTimeFormatter.ofPattern("d MMM yyyy")))
                Detail("Account", accounts.firstOrNull { it.id == t.accountId }?.name ?: "—")
                Detail("Type", if (t.direction == Direction.DEBIT) "Spent" else "Received")
                t.reference?.let { Detail("Reference", it) }
                Detail("Channel", t.channel.name)
                t.note?.let { Detail("Note", it) }
                if (t.linkType.name != "NONE") Detail("Linked", t.linkType.name.lowercase().replace('_', ' '))
                if (sms.isNotEmpty()) {
                    Text("Original SMS", style = MaterialTheme.typography.labelLarge, color = glass.textSecondary, modifier = Modifier.padding(top = 12.dp))
                    sms.forEach { m ->
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp).glass(RoundedCornerShape(16.dp)).padding(12.dp)) {
                            Text(m.sender, style = MaterialTheme.typography.labelMedium, color = glass.textSecondary)
                            Text(m.body, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = true }, Modifier.weight(1f)) { Text("Edit") }
                    if (t.personToPerson && t.direction == Direction.DEBIT) {
                        OutlinedButton(onClick = { scope.launch { c.transactions.markOwn(t); onDismiss() } }, Modifier.weight(1f)) { Text("This is me") }
                    }
                    OutlinedButton(onClick = { confirmDelete = true }, Modifier.weight(1f)) { Text("Delete") }
                }
            } else {
                EditForm(t, categories, accounts, onCancel = { if (id == null) onDismiss() else editing = false }) { edited ->
                    scope.launch { c.transactions.save(edited); onDismiss() }
                }
            }
        }
    }
    if (pickCategory) CategoryPickerSheet(categories, t.categoryId, onDismiss = { pickCategory = false }) { newCat ->
        pickCategory = false
        recategoriseRequest = RecategoriseRequest(t.id, newCat)
        onDismiss()
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete transaction?") },
        text = { Text("The original SMS stays on your phone. A rescan will not bring this back.") },
        confirmButton = { TextButton(onClick = { scope.launch { c.transactions.delete(t.id); onDismiss() } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Spacer8() = Box(Modifier.size(8.dp))

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(0.35f), color = LocalGlass.current.textSecondary)
        Text(value, Modifier.weight(0.65f))
    }
}

@Composable
private fun EditForm(
    t: TransactionEntity, categories: List<CategoryEntity>, accounts: List<AccountEntity>,
    onCancel: () -> Unit, onSave: (TransactionEntity) -> Unit,
) {
    var amount by remember { mutableStateOf(if (t.amount == 0L) "" else (t.amount / 100.0).toString()) }
    var share by remember { mutableStateOf(t.myShare?.let { (it / 100.0).toString() }.orEmpty()) }
    var merchant by remember { mutableStateOf(t.merchant.orEmpty()) }
    var note by remember { mutableStateOf(t.note.orEmpty()) }
    var date by remember { mutableStateOf(t.date.toString()) }
    var direction by remember { mutableStateOf(t.direction) }
    var category by remember { mutableStateOf(t.categoryId) }
    var account by remember { mutableStateOf(t.accountId) }
    val valid = Amounts.toPaise(amount)?.let { it > 0 } == true && runCatching { LocalDate.parse(date) }.isSuccess && account != 0L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (t.id == 0L) "New transaction" else "Edit transaction", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(direction == Direction.DEBIT, { direction = Direction.DEBIT }, { Text("Spent") })
            FilterChip(direction == Direction.CREDIT, { direction = Direction.CREDIT }, { Text("Received") })
        }
        OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(share, { share = it }, label = { Text("My share (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / payee") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
        Text("Bucket", style = MaterialTheme.typography.labelLarge)
        ChipFlow(categories.map { it.id to it.name }, category) { category = it }
        Text("Account", style = MaterialTheme.typography.labelLarge)
        ChipFlow(accounts.map { it.id.toString() to it.name }, account.toString()) { account = it.toLong() }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, Modifier.weight(1f)) { Text("Cancel") }
            Button(enabled = valid, modifier = Modifier.weight(1f), onClick = {
                val m = merchant.trim().ifEmpty { null }
                onSave(
                    t.copy(
                        amount = Amounts.toPaise(amount)!!, myShare = Amounts.toPaise(share), merchant = m,
                        merchantKey = m?.let(MerchantNormaliser::key), note = note.trim().ifEmpty { null },
                        date = LocalDate.parse(date), time = LocalDate.parse(date).atTime(t.time.toLocalTime()),
                        direction = direction, categoryId = category, accountId = account,
                    )
                )
            }) { Text("Save") }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (id, label) -> FilterChip(selected == id, { onSelect(id) }, { Text(label) }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(categories: List<CategoryEntity>, current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Text("Move to bucket", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            items(categories.filterNot { it.hidden }, key = { it.id }) { cat ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).glass(RoundedCornerShape(16.dp), strong = cat.id == current)
                        .clickable { onPick(cat.id) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(12.dp).background(Color(cat.color.toInt()), CircleShape))
                    Text(cat.name, Modifier.weight(1f).padding(start = 12.dp))
                    if (cat.isIncome) Text("income", style = MaterialTheme.typography.labelSmall, color = LocalGlass.current.textSecondary)
                }
            }
        }
    }
}
