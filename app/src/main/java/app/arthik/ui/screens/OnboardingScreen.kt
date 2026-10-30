package app.arthik.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.arthik.container
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.Meter
import app.arthik.ui.theme.LocalGlass
import app.arthik.work.ScanWorker
import kotlinx.coroutines.launch

/** Privacy → SMS permission → notifications → app lock → 12-month scan with progress. */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var smsDenied by remember { mutableStateOf(false) }
    var lockOn by remember { mutableStateOf(true) }
    BackHandler(enabled = step > 0) { step-- }

    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) step = 2 else smsDenied = true
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { step = 3 }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedContent(step, transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut() }, label = "step") { s ->
            when (s) {
                0 -> Step(
                    "Your money, on your phone only",
                    "Arthik reads bank, card, UPI and investment SMS and turns them into a clear monthly picture.\n\n" +
                        "It has no internet permission. Nothing ever leaves this phone. Data is encrypted and locked behind your fingerprint or PIN.",
                    "Get started",
                ) { step = 1 }
                1 -> Column {
                    Step(
                        "Allow reading SMS",
                        "Arthik needs SMS access to find transactions from the last 12 months and to record new ones as they arrive. " +
                            "OTPs and personal messages are skipped and never stored.",
                        "Allow SMS access",
                    ) { smsPermission.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }
                    if (smsDenied) GlassCard(Modifier.padding(top = 12.dp)) {
                        Text("Permission was not granted", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Android blocks SMS access for apps installed from outside the Play Store until you allow it: " +
                                "App info → ⋮ (top right) → Allow restricted settings, then come back and try again.",
                            style = MaterialTheme.typography.bodyMedium, color = LocalGlass.current.textSecondary,
                        )
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        }) { Text("Open app info") }
                        TextButton(onClick = { step = 2 }) { Text("Continue without SMS (manual entries only)") }
                    }
                }
                2 -> Step(
                    "Reminders",
                    "Get a nudge before card due dates, subscriptions and EMIs, and when a budget reaches 80% or 100%. Amounts are hidden on the lock screen.",
                    "Allow notifications",
                    secondary = "Not now" to { step = 3 },
                ) { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
                3 -> Column {
                    Step(
                        "Lock Arthik",
                        "Ask for fingerprint, face or your device PIN when opening Arthik, and after a minute in the background.",
                        "Use app lock",
                        secondary = "Skip" to { lockOn = false; step = 4 },
                    ) { lockOn = true; step = 4 }
                }
                else -> ScanStep(onFinish = {
                    scope.launch { c.settings.setAppLock(lockOn); c.settings.setOnboardingDone(true) }
                })
            }
        }
    }
}

@Composable
private fun Step(title: String, body: String, primary: String, secondary: Pair<String, () -> Unit>? = null, onPrimary: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge, color = LocalGlass.current.textSecondary)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(primary) }
        secondary?.let { (label, action) -> OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(label) } }
    }
}

@Composable
private fun ScanStep(onFinish: () -> Unit) {
    val context = LocalContext.current
    val hasSms = remember {
        context.checkSelfPermission(Manifest.permission.READ_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(Unit) { if (hasSms) ScanWorker.start(context, 12) }
    val infos by remember { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(ScanWorker.NAME) }
        .collectAsStateWithLifecycle(emptyList())
    val info = infos.firstOrNull()
    val done = !hasSms || info?.state == WorkInfo.State.SUCCEEDED || info?.state == WorkInfo.State.FAILED
    val processed = info?.progress?.getInt(ScanWorker.KEY_DONE, 0) ?: 0
    val total = info?.progress?.getInt(ScanWorker.KEY_TOTAL, 0) ?: 0
    val found = info?.outputData?.getInt(ScanWorker.KEY_TXNS, -1)?.takeIf { it >= 0 } ?: info?.progress?.getInt(ScanWorker.KEY_TXNS, 0) ?: 0
    val fraction by animateFloatAsState(if (done) 1f else if (total > 0) processed.toFloat() / total else 0.05f, tween(400), label = "scan")

    Column(Modifier.fillMaxWidth()) {
        Text(if (done) "All set" else "Reading the last 12 months…", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Meter(fraction)
        Spacer(Modifier.height(8.dp))
        Text(
            if (!hasSms) "SMS access is off — you can add transactions by hand and turn it on later."
            else "$found transactions found" + if (!done && total > 0) " · $processed of $total messages" else "",
            color = LocalGlass.current.textSecondary,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onFinish, enabled = done, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open Arthik") }
        if (!done) TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Continue — scanning carries on in the background") }
    }
}
