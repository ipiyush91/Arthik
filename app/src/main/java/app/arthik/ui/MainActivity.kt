package app.arthik.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.arthik.container
import app.arthik.data.prefs.AppSettings
import app.arthik.ui.components.BlobBackground
import app.arthik.ui.screens.OnboardingScreen
import app.arthik.ui.screens.RestoreScreen
import app.arthik.ui.theme.ArthikTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var settings = AppSettings()
    private lateinit var lock: AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Every screen shows money: keep it out of screenshots and the recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        val c = container
        lock = AppLock({ settings.lockTimeoutSeconds }, { settings.appLockEnabled && settings.onboardingDone })
        ProcessLifecycleOwner.get().lifecycle.addObserver(lock)
        lifecycleScope.launch { c.settings.flow.collect { settings = it } }

        setContent {
            ArthikTheme {
                BlobBackground {
                    val s by c.settings.flow.collectAsState(initial = null)
                    val locked by lock.locked.collectAsState()
                    var dbOk by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(Unit) {
                        settings = c.settings.flow.first()
                        dbOk = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { c.databaseAvailable() }
                        if (!settings.appLockEnabled || !settings.onboardingDone) lock.unlockedWithoutPrompt()
                    }
                    val current = s
                    when {
                        current == null || dbOk == null -> Unit
                        dbOk == false -> RestoreScreen(onDone = { recreate() })
                        !current.onboardingDone -> OnboardingScreen()
                        locked -> LockScreen(onUnlock = { lock.prompt(this@MainActivity) {} })
                        else -> ArthikNavHost()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (lock.locked.value && settings.onboardingDone && settings.appLockEnabled) lock.prompt(this) {}
    }
}

@androidx.compose.runtime.Composable
private fun LockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Arthik is locked", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onUnlock, modifier = Modifier.padding(top = 24.dp)) { Text("Unlock") }
    }
}
