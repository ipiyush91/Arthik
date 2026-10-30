package app.arthik.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.arthik.container
import app.arthik.core.model.AccountType
import app.arthik.core.text.Amounts
import app.arthik.data.repo.Periods
import app.arthik.ui.MainActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private data class WidgetState(
    val showAmounts: Boolean,
    val spend: Long,
    val top: List<Pair<String, Long>>,
    val nextDue: String?,
)

/**
 * Month-to-date spend, top 3 buckets and the next card due date. Amounts are hidden by default
 * ("₹ •••") because the widget sits outside the app lock.
 */
class ArthikWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { GlanceTheme { Content(state) } }
    }

    private suspend fun load(context: Context): WidgetState {
        val c = context.container
        if (!c.databaseAvailable()) return WidgetState(false, 0, emptyList(), null)
        val settings = c.settings.current()
        val period = Periods.containing(LocalDate.now(), settings.monthStartDay)
        val cats = c.insights.spendByCategory(period)
        val due = c.db.accounts().all().filter { it.type == AccountType.CREDIT_CARD && !it.hidden && it.dueDate != null && !it.dueDate.isBefore(LocalDate.now()) }
            .minByOrNull { it.dueDate!! }
        return WidgetState(
            showAmounts = settings.widgetShowAmounts,
            spend = cats.sumOf { it.total },
            top = cats.take(3).map { it.name to it.total },
            nextDue = due?.let { "${it.name} · ${it.dueDate!!.format(DateTimeFormatter.ofPattern("d MMM"))}" },
        )
    }

    @Composable
    private fun Content(s: WidgetState) {
        val white = ColorProvider(Color.White)
        val dim = ColorProvider(Color(0xB3FFFFFF))
        fun money(v: Long) = if (s.showAmounts) Amounts.formatInr(v) else "₹ •••"
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Color(0xE6141420)).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Text("This month", style = TextStyle(color = dim, fontSize = 12.sp))
            Text(money(s.spend), style = TextStyle(color = white, fontSize = 24.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.height(6.dp))
            s.top.forEach { (name, v) ->
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(name, style = TextStyle(color = dim, fontSize = 12.sp), modifier = GlanceModifier.defaultWeight())
                    Text(money(v), style = TextStyle(color = white, fontSize = 12.sp))
                }
            }
            s.nextDue?.let {
                Spacer(GlanceModifier.height(6.dp))
                Text("Due: $it", style = TextStyle(color = ColorProvider(Color(0xFF2DD4BF)), fontSize = 12.sp))
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { ArthikWidget().updateAll(context) }
    }
}

class ArthikWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ArthikWidget()
}
