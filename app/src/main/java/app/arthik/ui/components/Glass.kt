package app.arthik.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.arthik.core.text.Amounts
import app.arthik.ui.theme.Accent
import app.arthik.ui.theme.LocalGlass

val GlassShape = RoundedCornerShape(24.dp)

/**
 * "Cheap glass": a translucent fill with a top-left sheen and a 1px light border over the soft
 * blob background. No real-time blur, so lists and animations stay smooth.
 */
fun Modifier.glass(shape: Shape = GlassShape, strong: Boolean = false): Modifier = this
    .clip(shape)
    .background(if (strong) Color.White.copy(alpha = 0.11f) else Color.White.copy(alpha = 0.07f))
    .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), end = Offset(400f, 400f)))
    .border(1.dp, Color.White.copy(alpha = 0.15f), shape)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .glass()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** True when the user turned animations off (Developer options / Accessibility). */
@Composable
fun reducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Near-black background with three slowly drifting radial-gradient blobs. Movement is applied in
 * the draw/graphics layer only, so it never recomposes the content above it.
 */
@Composable
fun BlobBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glass = LocalGlass.current
    val still = reducedMotion()
    val transition = rememberInfiniteTransition(label = "blobs")
    val t by if (still) remember { androidx.compose.runtime.mutableFloatStateOf(0.3f) } else transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse), label = "t",
    )
    Box(modifier.fillMaxSize().background(glass.background)) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f }) {
            val w = size.width
            val h = size.height
            val r = maxOf(w, h) * 0.55f
            val centres = listOf(
                Offset(w * (0.15f + 0.25f * t), h * (0.12f + 0.08f * t)),
                Offset(w * (0.95f - 0.30f * t), h * (0.40f + 0.12f * t)),
                Offset(w * (0.30f + 0.20f * t), h * (0.92f - 0.15f * t)),
            )
            centres.forEachIndexed { i, c ->
                drawCircle(Brush.radialGradient(listOf(glass.blobs[i], Color.Transparent), center = c, radius = r), r, c)
            }
        }
        content()
    }
}

@Composable
fun Amount(paise: Long, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
           color: Color = MaterialTheme.colorScheme.onSurface, withPaise: Boolean = false, approximate: Boolean = false) {
    Text((if (approximate) "≈ " else "") + Amounts.formatInr(paise, withPaise), modifier, style = style, color = color, maxLines = 1)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = LocalGlass.current.textSecondary, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, color: Color = Accent.primary) {
    val c = when {
        fraction >= 1f -> LocalGlass.current.negative
        fraction >= 0.8f -> LocalGlass.current.warning
        else -> color
    }
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = c, trackColor = Color.White.copy(alpha = 0.10f), strokeCap = StrokeCap.Round, gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    GlassCard(modifier.padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = LocalGlass.current.textSecondary, overflow = TextOverflow.Ellipsis)
    }
}
