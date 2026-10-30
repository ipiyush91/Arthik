package app.arthik.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.arthik.container
import app.arthik.core.categorise.CategoryIds
import app.arthik.core.text.Amounts
import app.arthik.data.db.OVERALL_BUDGET
import app.arthik.data.repo.Periods
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.Meter
import app.arthik.ui.components.ScreenList
import app.arthik.ui.theme.LocalGlass
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun BudgetsScreen(nav: NavController) {
    val c = LocalContext.current.container
    val budgets by c.budgets.budgets.collectAsStateWithLifecycle(emptyList())
    val categories by c.categories.categories.collectAsStateWithLifecycle(emptyList())
    val spend by remember {
        c.settings.flow.flatMapLatest { c.insights.byCategory(Periods.containing(LocalDate.now(), it.monthStartDay)) }
    }.collectAsStateWithLifecycle(emptyList())
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }

    val used = spend.associate { it.categoryId to it.total }
    val limits = budgets.associate { it.categoryId to it.amount }
    val rows = listOf(OVERALL_BUDGET to "Overall") + categories
        .filter { !it.isIncome && it.id !in CategoryIds.NEUTRAL && !it.hidden }.map { it.id to it.name }

    ScreenList("Budgets", onBack = { nav.popBackStack() }) {
        item(key = "hint") {
            Text("Alerts at 80% and 100%, once per bucket per month. Budgets don't roll over.",
                style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
        }
        rows.forEach { (id, name) ->
            item(key = id) {
                val u = if (id == OVERALL_BUDGET) spend.sumOf { it.total } else used[id] ?: 0
                val limit = limits[id]
                GlassCard(onClick = { editing = id to name }, padding = 14.dp) {
                    Row {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(if (limit != null) "${Amounts.formatInr(u)} / ${Amounts.formatInr(limit)}" else "${Amounts.formatInr(u)} · no limit",
                            color = glass.textSecondary)
                    }
                    if (limit != null) Meter(u.toFloat() / limit, Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    editing?.let { (id, name) ->
        var text by remember(id) { mutableStateOf(limits[id]?.let { (it / 100).toString() }.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("$name budget") },
            text = {
                OutlinedTextField(text, { text = it }, label = { Text("Monthly limit (₹), empty = none") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            },
            confirmButton = { TextButton(onClick = { scope.launch { c.budgets.set(id, Amounts.toPaise(text) ?: 0) }; editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
