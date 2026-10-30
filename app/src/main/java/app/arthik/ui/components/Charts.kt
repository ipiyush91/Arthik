package app.arthik.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.arthik.core.text.Amounts
import app.arthik.ui.theme.Accent
import app.arthik.ui.theme.LocalGlass
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

@Immutable
data class Slice(val key: String, val label: String, val value: Long, val color: Color)

/**
 * Animated donut. Animation state lives in [Animatable]s read only inside the draw phase, so the
 * sweep animation never recomposes. Tap a slice to select it (haptic tick); tap again to drill down.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    centerLabel: String,
    centerValue: Long,
    onSliceClick: (Slice) -> Unit,
) {
    val sweep = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(slices) {
        if (still) sweep.snapTo(1f) else { sweep.snapTo(0f); sweep.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    }
    var selected by remember(slices) { mutableIntStateOf(-1) }
    val haptics = LocalHapticFeedback.current
    val total = slices.sumOf { it.value }.coerceAtLeast(1)

    Box(modifier.fillMaxWidth().aspectRatio(1.6f), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1.6f).pointerInput(slices) {
                detectTapGestures { p ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(size.width, size.height) / 2f
                    val d = hypot(p.x - c.x, p.y - c.y)
                    if (d < radius * 0.55f || d > radius) return@detectTapGestures
                    var angle = (atan2(p.y - c.y, p.x - c.x) * 180 / PI).toFloat() + 90f
                    if (angle < 0) angle += 360f
                    var acc = 0f
                    val idx = slices.indexOfFirst { s -> acc += s.value * 360f / total; angle <= acc }
                    if (idx >= 0) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (selected == idx) onSliceClick(slices[idx]) else selected = idx
                    }
                }
            },
        ) {
            val stroke = size.minDimension * 0.13f
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(Color.White.copy(alpha = 0.06f), 0f, 360f, false, topLeft, Size(diameter, diameter), style = Stroke(stroke))
            var start = -90f
            slices.forEachIndexed { i, s ->
                val angle = s.value * 360f / total * sweep.value
                val w = if (i == selected) stroke * 1.25f else stroke
                drawArc(s.color, start, (angle - 1.2f).coerceAtLeast(0.1f), false, topLeft, Size(diameter, diameter), style = Stroke(w))
                start += angle
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val s = slices.getOrNull(selected)
            Text(s?.label ?: centerLabel, style = MaterialTheme.typography.labelLarge, color = LocalGlass.current.textSecondary)
            Text(Amounts.formatInr(s?.value ?: centerValue), style = MaterialTheme.typography.headlineSmall)
            if (s != null) Text("${s.value * 100 / total}% · tap again to open", style = MaterialTheme.typography.labelSmall,
                color = LocalGlass.current.textSecondary)
        }
    }
}

@Immutable
data class Bar(val label: String, val value: Long)

/** Animated bar chart with a tap tooltip. */
@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier, color: Color = Accent.primary, highlightLast: Boolean = true) {
    val grow = remember { Animatable(0f) }
    val still = reducedMotion()
    LaunchedEffect(bars) {
        if (still) grow.snapTo(1f) else { grow.snapTo(0f); grow.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }
    }
    var selected by remember(bars) { mutableIntStateOf(-1) }
    val max = (bars.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1)
    Column(modifier) {
        val s = bars.getOrNull(selected)
        Text(
            if (s != null) "${s.label}: ${Amounts.formatInr(s.value)}" else " ",
            style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textSecondary,
        )
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).pointerInput(bars) {
                detectTapGestures { p ->
                    if (bars.isEmpty()) return@detectTapGestures
                    selected = (p.x / (size.width / bars.size)).toInt().coerceIn(0, bars.lastIndex)
                }
            },
        ) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            bars.forEachIndexed { i, b ->
                val h = (b.value.toFloat() / max) * size.height * grow.value
                val emphasised = i == selected || (selected < 0 && highlightLast && i == bars.lastIndex)
                drawRoundRect(
                    color = if (emphasised) color else color.copy(alpha = 0.45f),
                    topLeft = Offset(i * slot + (slot - barW) / 2f, size.height - h),
                    size = Size(barW, h.coerceAtLeast(2f)),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
            }
        }
    }
}

/** Two series side by side (this month vs last month) per bucket. */
@Composable
fun CompareBars(rows: List<Triple<String, Long, Long>>, modifier: Modifier = Modifier) {
    val max = rows.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1) ?: 1
    val grow = remember { Animatable(0f) }
    LaunchedEffect(rows) { grow.snapTo(0f); grow.animateTo(1f, tween(700)) }
    Column(modifier) {
        rows.forEach { (label, now, before) ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = LocalGlass.current.textSecondary)
            Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                val h = size.height / 2.6f
                drawRoundRect(Accent.primary, Offset.Zero, Size(size.width * now / max * grow.value, h), CornerRadius(h / 2, h / 2))
                drawRoundRect(Accent.secondary.copy(alpha = 0.6f), Offset(0f, h * 1.4f), Size(size.width * before / max * grow.value, h),
                    CornerRadius(h / 2, h / 2))
            }
        }
    }
}
