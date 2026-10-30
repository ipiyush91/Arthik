package app.arthik.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.arthik.container
import app.arthik.data.db.CategoryEntity
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.ScreenList
import app.arthik.ui.components.SectionTitle
import app.arthik.ui.theme.LocalGlass
import kotlinx.coroutines.launch

private val palette = listOf(
    0xFFFF8A65, 0xFF81C784, 0xFFBA68C8, 0xFF4FC3F7, 0xFFFFD54F, 0xFFE57373, 0xFF4DB6AC, 0xFFF06292,
    0xFF7986CB, 0xFFA1887F, 0xFF90A4AE, 0xFF26A69A, 0xFFB0BEC5, 0xFF2DD4BF, 0xFFA78BFA, 0xFFFBBF24,
)

@Composable
fun CategoriesScreen(nav: NavController) {
    val c = LocalContext.current.container
    val categories by c.categories.categories.collectAsStateWithLifecycle(emptyList())
    val rules by c.categories.rules.collectAsStateWithLifecycle(emptyList())
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }

    ScreenList("Buckets", onBack = { nav.popBackStack() }, actions = { TextButton(onClick = { adding = true }) { Text("Add") } }) {
        categories.forEach { cat ->
            item(key = cat.id) {
                GlassCard(onClick = { editing = cat }, padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(14.dp).background(Color(cat.color.toInt()), CircleShape))
                        Text(cat.name + if (cat.hidden) " (hidden)" else "", Modifier.weight(1f).padding(start = 12.dp))
                        Text(if (cat.isIncome) "income" else "spend", style = MaterialTheme.typography.labelSmall, color = glass.textSecondary)
                    }
                }
            }
        }
        item(key = "rules_title") { SectionTitle("Learned rules") }
        if (rules.isEmpty()) item(key = "rules_empty") {
            Text("When you move a transaction to another bucket, Arthik remembers the merchant here.", color = glass.textSecondary)
        }
        rules.forEach { r ->
            item(key = "rule_${r.merchantKey}") {
                GlassCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.merchant)
                            Text("→ " + (categories.firstOrNull { it.id == r.categoryId }?.name ?: r.categoryId), style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                        }
                        TextButton(onClick = { scope.launch { c.categories.deleteRule(r.merchantKey) } }) { Text("Forget") }
                    }
                }
            }
        }
    }

    editing?.let { cat -> CategoryDialog(cat.name, cat.color, cat.hidden, onDismiss = { editing = null }) { name, color, hidden ->
        scope.launch { c.categories.upsert(cat.copy(name = name, color = color, hidden = hidden)) }; editing = null
    } }
    if (adding) CategoryDialog("", palette.first(), false, onDismiss = { adding = false }) { name, color, _ ->
        scope.launch { c.categories.add(name, color, isIncome = false) }; adding = false
    }
}

@Composable
private fun CategoryDialog(name0: String, color0: Long, hidden0: Boolean, onDismiss: () -> Unit, onSave: (String, Long, Boolean) -> Unit) {
    var name by remember { mutableStateOf(name0) }
    var color by remember { mutableStateOf(color0) }
    var hidden by remember { mutableStateOf(hidden0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (name0.isEmpty()) "New bucket" else "Edit bucket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { p ->
                        Box(Modifier.size(if (p == color) 34.dp else 28.dp).background(Color(p.toInt()), CircleShape).clickable { color = p })
                    }
                }
                if (name0.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { Text("Hide", Modifier.weight(1f)); Switch(hidden, { hidden = it }) }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color, hidden) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
