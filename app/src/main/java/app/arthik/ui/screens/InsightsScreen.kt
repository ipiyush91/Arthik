package app.arthik.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import app.arthik.AppContainer
import app.arthik.data.db.MerchantTotal
import app.arthik.data.db.RecurringEntity
import app.arthik.data.db.RecurringSource
import app.arthik.data.db.RecurringStatus
import app.arthik.data.repo.Periods
import app.arthik.ui.Routes
import app.arthik.ui.arthikViewModel
import app.arthik.ui.components.Amount
import app.arthik.ui.components.CompareBars
import app.arthik.ui.components.EmptyState
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.ScreenList
import app.arthik.ui.components.SectionTitle
import app.arthik.ui.theme.LocalGlass
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class InsightsState(
    val compare: List<Triple<String, Long, Long>> = emptyList(),
    val merchants: List<MerchantTotal> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModel(val c: AppContainer) : ViewModel() {
    val state = c.settings.flow.flatMapLatest { s ->
        val now = Periods.containing(LocalDate.now(), s.monthStartDay)
        val prev = now.previous(s.monthStartDay)
        combine(c.insights.byCategory(now), c.insights.byCategory(prev), c.insights.topMerchants(now), c.recurring.active) { a, b, m, r ->
            val before = b.associate { it.categoryId to it.total }
            val names = (a + b).associate { it.categoryId to it.name }
            val ids = (a.map { it.categoryId } + b.map { it.categoryId }).distinct()
            InsightsState(
                compare = ids.map { Triple(names[it] ?: it, a.firstOrNull { x -> x.categoryId == it }?.total ?: 0, before[it] ?: 0) }
                    .sortedByDescending { it.second },
                merchants = m,
                recurring = r,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    fun setStatus(key: String, status: RecurringStatus) = viewModelScope.launch { c.recurring.setStatus(key, status) }
}

@Composable
fun InsightsScreen(nav: NavController) {
    val vm = arthikViewModel { InsightsViewModel(it) }
    val s by vm.state.collectAsStateWithLifecycle()
    val glass = LocalGlass.current
    ScreenList("Insights", actions = { TextButton(onClick = { nav.navigate(Routes.BUDGETS) }) { Text("Budgets") } }) {
        item(key = "mom") {
            GlassCard {
                Text("This month vs last month", style = MaterialTheme.typography.titleMedium)
                Text("Teal = this month · violet = last month", style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                if (s.compare.isEmpty()) Text("No spending recorded yet.", color = glass.textSecondary, modifier = Modifier.padding(top = 8.dp))
                else CompareBars(s.compare, Modifier.padding(top = 10.dp))
            }
        }
        item(key = "merchants_title") { SectionTitle("Top merchants") }
        if (s.merchants.isEmpty()) item(key = "merchants_empty") { EmptyState("No merchants yet", "Your biggest payees this month will show here.") }
        s.merchants.forEach { m ->
            item(key = "m_${m.merchant}") {
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.merchant, style = MaterialTheme.typography.bodyLarge)
                            Text("${m.count} payment${if (m.count == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                        }
                        Amount(m.total)
                    }
                }
            }
        }
        item(key = "recurring_title") { SectionTitle("Recurring & subscriptions") }
        if (s.recurring.isEmpty()) item(key = "recurring_empty") {
            EmptyState("Nothing recurring yet", "Payments to the same merchant with a similar amount every week, month or year are detected automatically.")
        }
        s.recurring.forEach { r ->
            item(key = "r_${r.merchantKey}") {
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.merchant, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                r.cadence.lowercase().replaceFirstChar { it.uppercase() } +
                                    (r.nextDate?.let { " · next ${it.format(DateTimeFormatter.ofPattern("d MMM"))}" } ?: "") +
                                    if (r.source == RecurringSource.MANDATE) " · auto-debit" else "",
                                style = MaterialTheme.typography.bodySmall, color = glass.textSecondary,
                            )
                        }
                        Amount(r.amount)
                    }
                    if (r.status == RecurringStatus.DETECTED) Row {
                        TextButton(onClick = { vm.setStatus(r.merchantKey, RecurringStatus.CONFIRMED) }) { Text("Confirm") }
                        TextButton(onClick = { vm.setStatus(r.merchantKey, RecurringStatus.DISMISSED) }) { Text("Not recurring") }
                    }
                }
            }
        }
    }
}
