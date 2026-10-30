package app.arthik.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.arthik.AppContainer
import app.arthik.core.model.AccountType
import app.arthik.core.text.Amounts
import app.arthik.data.db.AccountEntity
import app.arthik.data.db.OVERALL_BUDGET
import app.arthik.data.repo.NetWorth
import app.arthik.data.repo.Periods
import app.arthik.ui.Routes
import app.arthik.ui.arthikViewModel
import app.arthik.ui.components.Amount
import app.arthik.ui.components.Bar
import app.arthik.ui.components.BarChart
import app.arthik.ui.components.DonutChart
import app.arthik.ui.components.EmptyState
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.Meter
import app.arthik.ui.components.ScreenList
import app.arthik.ui.components.SectionTitle
import app.arthik.ui.components.Slice
import app.arthik.ui.theme.AmountStyle
import app.arthik.ui.theme.LocalGlass
import app.arthik.work.ScanWorker
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class DueItem(val accountId: Long, val name: String, val amount: Long?, val minimum: Long?, val due: LocalDate?, val needsInput: Boolean)

@Immutable
data class BudgetLine(val label: String, val used: Long, val limit: Long)

@Immutable
data class DashboardState(
    val periodLabel: String = "",
    val spend: Long = 0,
    val income: Long = 0,
    val lastSpend: Long = 0,
    val slices: List<Slice> = emptyList(),
    val bars: List<Bar> = emptyList(),
    val netWorth: NetWorth = NetWorth(0, 0, 0, 0),
    val dues: List<DueItem> = emptyList(),
    val budgets: List<BudgetLine> = emptyList(),
    val unrecognised: Int = 0,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val c: AppContainer) : ViewModel() {
    private val labelFmt = DateTimeFormatter.ofPattern("d MMM")

    val state = c.settings.flow.flatMapLatest { s ->
        val period = Periods.containing(LocalDate.now(), s.monthStartDay)
        val prev = period.previous(s.monthStartDay)
        val head = combine(c.insights.dashboard(period, prev), c.insights.daily(period), c.accounts.netWorth) { d, daily, nw ->
            Triple(d, daily, nw)
        }
        combine(head, c.accounts.accounts, c.budgets.budgets, c.sms.unrecognisedCount) { (d, daily, nw), accounts, budgets, unrec ->
            val byDay = daily.associate { it.date to it.total }
            val today = LocalDate.now()
            val days = generateSequence(period.from) { it.plusDays(1) }.takeWhile { !it.isAfter(minOf(period.to, today)) }.toList()
            val catTotals = d.byCategory.associate { it.categoryId to it.total }
            val catNames = d.byCategory.associate { it.categoryId to it.name }
            DashboardState(
                periodLabel = if (s.monthStartDay == 1) period.from.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                    else "${period.from.format(labelFmt)} – ${period.to.format(labelFmt)}",
                spend = d.spend, income = d.income, lastSpend = d.lastPeriodSpend,
                slices = d.byCategory.map { Slice(it.categoryId, it.name, it.total, Color(it.color.toInt())) },
                bars = days.map { Bar(it.format(labelFmt), (byDay[it] ?: 0L).coerceAtLeast(0L)) },
                netWorth = nw,
                dues = accounts.filter { it.type == AccountType.CREDIT_CARD && !it.hidden && (it.dueDate != null || it.statementNeedsInput) }
                    .sortedBy { it.dueDate ?: LocalDate.MAX }.take(3).map { it.toDue() },
                budgets = budgets.sortedBy { it.categoryId != OVERALL_BUDGET }.take(4).map { b ->
                    val used = if (b.categoryId == OVERALL_BUDGET) d.spend else catTotals[b.categoryId] ?: 0L
                    BudgetLine(if (b.categoryId == OVERALL_BUDGET) "Overall" else catNames[b.categoryId] ?: b.categoryId, used, b.amount)
                },
                unrecognised = unrec,
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private fun AccountEntity.toDue() = DueItem(id, name, statementTotal, statementMin, dueDate, statementNeedsInput)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(nav: NavController) {
    val vm = arthikViewModel { DashboardViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val glass = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            ScanWorker.start(context, months = 1)
            scope.launch { delay(1200); refreshing = false }
        },
    ) {
        ScreenList("Arthik") {
            item(key = "summary", contentType = "card") {
                GlassCard {
                    Text(s.periodLabel, style = MaterialTheme.typography.labelLarge, color = glass.textSecondary)
                    Amount(s.spend, style = AmountStyle)
                    Text("spent", style = MaterialTheme.typography.bodyMedium, color = glass.textSecondary)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Income", style = MaterialTheme.typography.labelMedium, color = glass.textSecondary)
                            Amount(s.income, color = glass.positive)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Last month", style = MaterialTheme.typography.labelMedium, color = glass.textSecondary)
                            Amount(s.lastSpend)
                            if (s.lastSpend > 0) {
                                val diff = (s.spend - s.lastSpend) * 100 / s.lastSpend
                                Text(if (diff >= 0) "▲ $diff%" else "▼ ${-diff}%", style = MaterialTheme.typography.labelMedium,
                                    color = if (diff > 0) glass.negative else glass.positive)
                            }
                        }
                    }
                }
            }
            if (s.loaded && s.slices.isEmpty()) {
                item(key = "empty") { EmptyState("No spending yet this month", "New bank and card SMS will appear here automatically. Pull down to rescan.") }
            }
            if (s.slices.isNotEmpty()) {
                item(key = "donut", contentType = "card") {
                    GlassCard {
                        Text("Where it went", style = MaterialTheme.typography.titleMedium)
                        DonutChart(s.slices, centerLabel = "Total", centerValue = s.spend,
                            onSliceClick = { nav.navigate(Routes.transactions(category = it.key)) })
                        s.slices.take(5).forEach { sl ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.foundation.layout.Box(
                                    Modifier.padding(end = 8.dp).size(10.dp).background(sl.color, CircleShape)
                                )
                                Text(sl.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Amount(sl.value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item(key = "bars", contentType = "card") {
                    GlassCard {
                        Text("Daily spend", style = MaterialTheme.typography.titleMedium)
                        BarChart(s.bars, Modifier.padding(top = 8.dp))
                    }
                }
            }
            item(key = "networth", contentType = "card") {
                GlassCard(onClick = { nav.navigate(Routes.ACCOUNTS) }) {
                    Text("Net worth", style = MaterialTheme.typography.labelLarge, color = glass.textSecondary)
                    Amount(s.netWorth.total, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Bank ${Amounts.formatInr(s.netWorth.bank)} · Investments ${Amounts.formatInr(s.netWorth.investments)} · " +
                            "Cards −${Amounts.formatInr(s.netWorth.cardDues)}" +
                            if (s.netWorth.loans > 0) " · Loans −${Amounts.formatInr(s.netWorth.loans)}" else "",
                        style = MaterialTheme.typography.bodySmall, color = glass.textSecondary,
                    )
                }
            }
            if (s.dues.isNotEmpty()) {
                item(key = "dues_title") { SectionTitle("Upcoming card dues") }
                s.dues.forEach { d ->
                    item(key = "due_${d.accountId}", contentType = "due") {
                        GlassCard(onClick = { nav.navigate(Routes.account(d.accountId)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(d.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        when {
                                            d.needsInput -> "Statement ready — tap to enter amount"
                                            d.due == null -> ""
                                            else -> {
                                                val left = ChronoUnit.DAYS.between(LocalDate.now(), d.due)
                                                when { left < 0 -> "Due date passed"; left == 0L -> "Due today"; left == 1L -> "Due tomorrow"; else -> "Due in $left days" }
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall, color = glass.textSecondary,
                                    )
                                }
                                d.amount?.let { Amount(it) }
                            }
                        }
                    }
                }
            }
            item(key = "budgets_title") {
                SectionTitle("Budgets") { TextButton(onClick = { nav.navigate(Routes.BUDGETS) }) { Text(if (s.budgets.isEmpty()) "Set up" else "All") } }
            }
            if (s.budgets.isEmpty()) {
                item(key = "budgets_empty") { EmptyState("No budgets yet", "Set a monthly limit overall or per bucket and get alerts at 80% and 100%.") }
            }
            s.budgets.forEach { b ->
                item(key = "budget_${b.label}", contentType = "budget") {
                    GlassCard(onClick = { nav.navigate(Routes.BUDGETS) }) {
                        Row { Text(b.label, Modifier.weight(1f)); Text("${Amounts.formatInr(b.used)} / ${Amounts.formatInr(b.limit)}", color = glass.textSecondary) }
                        Meter(if (b.limit > 0) b.used.toFloat() / b.limit else 0f, Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (s.unrecognised > 0) {
                item(key = "unrecognised") {
                    GlassCard(onClick = { nav.navigate(Routes.UNRECOGNISED) }) {
                        Text("${s.unrecognised} SMS need a look", style = MaterialTheme.typography.titleMedium)
                        Text("Financial-looking messages Arthik could not read. Review, ignore or export them for new rules.",
                            style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                    }
                }
            }
        }
    }
}
