package app.arthik.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.arthik.container
import app.arthik.ui.components.EmptyState
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.ScreenList
import app.arthik.ui.theme.LocalGlass
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * Financial-looking SMS no rule understood. "Ignore" hides one; Settings → "Export unrecognised"
 * shares them anonymised so new rules can be written (spec 14.5).
 */
@Composable
fun UnrecognisedScreen(nav: NavController) {
    val c = LocalContext.current.container
    val list by c.sms.unrecognised.collectAsStateWithLifecycle(emptyList())
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    ScreenList("Unrecognised SMS", onBack = { nav.popBackStack() }) {
        item(key = "hint") {
            Text("Use Settings → Export unrecognised SMS to send these (anonymised) for new parsing rules.",
                style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
        }
        if (list.isEmpty()) item(key = "empty") { EmptyState("All clear", "Every financial SMS was understood.") }
        list.forEach { m ->
            item(key = m.id) {
                GlassCard(padding = 14.dp) {
                    Text("${m.sender} · ${m.receivedAt.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                        style = MaterialTheme.typography.labelMedium, color = glass.textSecondary)
                    Text(m.body, style = MaterialTheme.typography.bodyMedium)
                    Row { TextButton(onClick = { scope.launch { c.sms.ignore(m.id) } }) { Text("Ignore") } }
                }
            }
        }
    }
}
