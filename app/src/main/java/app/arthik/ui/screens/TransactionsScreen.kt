package app.arthik.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import app.arthik.AppContainer
import app.arthik.core.model.Direction
import app.arthik.data.db.CategoryEntity
import app.arthik.data.db.TxnRow
import app.arthik.data.repo.Period
import app.arthik.data.repo.Periods
import app.arthik.data.repo.TxnFilter
import app.arthik.ui.arthikViewModel
import app.arthik.ui.components.Amount
import app.arthik.ui.components.ArthikIcons
import app.arthik.ui.components.EmptyState
import app.arthik.ui.components.ScreenList
import app.arthik.ui.components.glass
import app.arthik.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(val c: AppContainer, category: String?, account: Long?) : ViewModel() {
    private var startDay = 1
    val filter = MutableStateFlow(TxnFilter(Periods.containing(LocalDate.now(), 1), categoryId = category, accountId = account))

    init {
        viewModelScope.launch {
            startDay = c.settings.current().monthStartDay
            filter.update { it.copy(period = Periods.containing(LocalDate.now(), startDay)) }
        }
    }

    val rows = filter.flatMapLatest { c.transactions.paged(it) }.cachedIn(viewModelScope)
    val categories = c.categories.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accounts = c.accounts.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun shift(months: Int) = filter.update { f ->
        var p: Period = f.period
        repeat(kotlin.math.abs(months)) { p = if (months > 0) p.next(startDay) else p.previous(startDay) }
        f.copy(period = p)
    }
    fun setQuery(q: String) = filter.update { it.copy(query = q) }
    fun setCategory(id: String?) = filter.update { it.copy(categoryId = id) }
    fun setAccount(id: Long?) = filter.update { it.copy(accountId = id) }
    fun setDirection(d: Direction?) = filter.update { it.copy(direction = d) }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(initialCategory: String?, initialAccount: Long?) {
    val vm = arthikViewModel(key = "txns_${initialCategory}_$initialAccount") { TransactionsViewModel(it, initialCategory, initialAccount) }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val rows = vm.rows.collectAsLazyPagingItems()
    val glass = LocalGlass.current
    var openTxn by remember { mutableStateOf<Long?>(null) }
    var pickFor by remember { mutableStateOf<TxnRow?>(null) }
    var creating by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(filter.categoryId != null || filter.accountId != null) }

    Box(Modifier.fillMaxSize()) {
        ScreenList("Transactions", actions = {
            IconButton(onClick = { showFilters = !showFilters }) { Icon(ArthikIcons.Filter, "Filters") }
        }) {
            item(key = "month") {
                Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.shift(-1) }) { Icon(ArthikIcons.ChevronLeft, "Previous month") }
                    Text(
                        "${filter.period.from.format(DateTimeFormatter.ofPattern("d MMM"))} – ${filter.period.to.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                        Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { vm.shift(1) }) { Icon(ArthikIcons.ChevronRight, "Next month") }
                }
            }
            item(key = "search") {
                OutlinedTextField(
                    value = filter.query.orEmpty(), onValueChange = vm::setQuery, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search merchant or note") }, leadingIcon = { Icon(ArthikIcons.Search, null) },
                    singleLine = true, shape = RoundedCornerShape(20.dp),
                )
            }
            if (showFilters) {
                item(key = "filters") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { FilterChip(filter.direction == null, { vm.setDirection(null) }, { Text("All") }) }
                            item { FilterChip(filter.direction == Direction.DEBIT, { vm.setDirection(Direction.DEBIT) }, { Text("Spent") }) }
                            item { FilterChip(filter.direction == Direction.CREDIT, { vm.setDirection(Direction.CREDIT) }, { Text("Received") }) }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { FilterChip(filter.categoryId == null, { vm.setCategory(null) }, { Text("Any bucket") }) }
                            items(categories, key = { it.id }) { cat ->
                                FilterChip(filter.categoryId == cat.id, { vm.setCategory(if (filter.categoryId == cat.id) null else cat.id) }, { Text(cat.name) })
                            }
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item { FilterChip(filter.accountId == null, { vm.setAccount(null) }, { Text("Any account") }) }
                            items(accounts, key = { it.id }) { a ->
                                FilterChip(filter.accountId == a.id, { vm.setAccount(if (filter.accountId == a.id) null else a.id) }, { Text(a.name) })
                            }
                        }
                    }
                }
            }
            if (rows.itemCount == 0) {
                item(key = "empty") { EmptyState("Nothing here", "No transactions match this month and these filters.") }
            }
            items(rows.itemCount, key = rows.itemKey { it.id }, contentType = rows.itemContentType { "txn" }) { i ->
                val row = rows[i] ?: return@items
                val prev = if (i > 0) rows.peek(i - 1) else null
                if (prev == null || prev.date != row.date) {
                    Text(row.date.format(dayFmt), style = MaterialTheme.typography.labelLarge, color = glass.textSecondary,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                }
                SwipeableRow(row, onSwipe = { pickFor = row }, onClick = { openTxn = row.id })
            }
        }
        FloatingActionButton(
            onClick = { creating = true },
            modifier = Modifier.align(Alignment.BottomEnd).windowInsetsPadding(WindowInsets.navigationBars).padding(end = 20.dp, bottom = 96.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) { Icon(ArthikIcons.Add, "Add transaction") }
    }

    openTxn?.let { id -> TransactionSheet(id, vm.c, categories, accounts, onDismiss = { openTxn = null; rows.refresh() }) }
    pickFor?.let { row ->
        CategoryPickerSheet(categories, row.categoryId, onDismiss = { pickFor = null }) { catId ->
            pickFor = null
            openTxn = null
            recategoriseRequest = RecategoriseRequest(row.id, catId)
        }
    }
    RecategoriseFlow(vm.c, onDone = { rows.refresh() })
    if (creating) TransactionSheet(null, vm.c, categories, accounts, onDismiss = { creating = false; rows.refresh() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(row: TxnRow, onSwipe: () -> Unit, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v != SwipeToDismissBoxValue.Settled) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onSwipe() }
            false // never dismiss: swiping opens the bucket picker
        },
    )
    SwipeToDismissBox(state, backgroundContent = {
        Box(Modifier.fillMaxSize().glass(RoundedCornerShape(20.dp), strong = true).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
            Text("Change bucket", color = MaterialTheme.colorScheme.primary)
        }
    }) {
        TxnRowItem(row, onClick)
    }
}

@Composable
fun TxnRowItem(row: TxnRow, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(Color(row.categoryColor.toInt()), CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(row.merchant ?: "Unknown", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${row.categoryName} · ${row.accountName}", style = MaterialTheme.typography.bodySmall, color = glass.textSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val credit = row.direction == Direction.CREDIT.name
        Amount(
            row.myShare ?: row.amount, approximate = row.approximate,
            color = if (credit) glass.positive else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** Pending "apply to past?" question after a recategorisation. */
data class RecategoriseRequest(val txnId: Long, val categoryId: String)

var recategoriseRequest by mutableStateOf<RecategoriseRequest?>(null)

@Composable
fun RecategoriseFlow(c: AppContainer, onDone: () -> Unit) {
    val req = recategoriseRequest ?: return
    val scope = rememberCoroutineScope()
    var askCount by remember(req) { mutableStateOf<Int?>(null) }
    var merchant by remember(req) { mutableStateOf("") }
    LaunchedEffect(req) {
        val txn = c.transactions.get(req.txnId) ?: run { recategoriseRequest = null; return@LaunchedEffect }
        val n = c.transactions.countSameMerchant(txn, req.categoryId)
        if (n == 0) { c.transactions.recategorise(txn, req.categoryId, applyToPast = false); recategoriseRequest = null; onDone() }
        else { merchant = txn.merchant ?: "this merchant"; askCount = n }
    }
    val n = askCount ?: return
    fun finish(applyToPast: Boolean) = scope.launch {
        c.transactions.get(req.txnId)?.let { c.transactions.recategorise(it, req.categoryId, applyToPast) }
        recategoriseRequest = null
        onDone()
    }
    AlertDialog(
        onDismissRequest = { finish(false) },
        title = { Text("Apply to past transactions?") },
        text = { Text("Arthik will remember this bucket for $merchant. Also move $n earlier transaction${if (n == 1) "" else "s"} from $merchant? Ones you edited by hand stay as they are.") },
        confirmButton = { TextButton(onClick = { finish(true) }) { Text("Apply to all") } },
        dismissButton = { TextButton(onClick = { finish(false) }) { Text("Only future") } },
    )
}
