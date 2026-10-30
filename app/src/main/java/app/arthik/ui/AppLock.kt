package app.arthik.ui

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Locks after the app has been in the background longer than the timeout (default 60 s).
 * Biometrics (strong) with device PIN/pattern fallback.
 */
class AppLock(private val timeoutSeconds: () -> Int, private val enabled: () -> Boolean) : DefaultLifecycleObserver {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked
    private var backgroundedAt = 0L

    override fun onStop(owner: LifecycleOwner) { backgroundedAt = SystemClock.elapsedRealtime() }

    override fun onStart(owner: LifecycleOwner) {
        if (!enabled()) { _locked.value = false; return }
        if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > timeoutSeconds() * 1000L) _locked.value = true
    }

    fun unlockedWithoutPrompt() { _locked.value = false }

    fun canAuthenticate(activity: FragmentActivity): Int =
        BiometricManager.from(activity).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)

    fun prompt(activity: FragmentActivity, onError: (String) -> Unit) {
        if (!enabled() || canAuthenticate(activity) == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            // No screen lock at all: the Settings screen shows a warning; don't trap the user.
            _locked.value = false
            return
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { _locked.value = false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onError(errString.toString()) }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Arthik")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
        )
    }
}
