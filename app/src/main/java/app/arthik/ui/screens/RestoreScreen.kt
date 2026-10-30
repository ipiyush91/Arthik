package app.arthik.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.arthik.container
import app.arthik.ui.theme.LocalGlass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown when the encrypted database can't be opened (Keystore key lost after a reset or on a new
 * phone). Restore from a backup file, or start fresh.
 */
@Composable
fun RestoreScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri = it }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Your data can't be opened", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Arthik's data is encrypted with a key kept by this phone. After a reset or on a new phone that key is gone. " +
                "Restore a backup file to get your data back, or start fresh.",
            color = LocalGlass.current.textSecondary, modifier = Modifier.padding(vertical = 16.dp),
        )
        if (uri == null) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth()) { Text("Choose backup file") }
        } else {
            OutlinedTextField(password, { password = it }, label = { Text("Backup password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Button(enabled = password.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = {
                scope.launch {
                    error = runCatching {
                        withContext(Dispatchers.IO) {
                            // Check the password before wiping anything.
                            val bytes = context.contentResolver.openInputStream(uri!!)!!.use { it.readBytes() }
                            c.backup.decrypt(bytes, password.toCharArray())
                            c.databaseKey.reset()
                            c.backup.restore(uri!!, password.toCharArray())
                            c.settings.setOnboardingDone(true)
                        }
                    }.exceptionOrNull()?.let { it.message ?: "Restore failed" }
                    if (error == null) onDone()
                }
            }) { Text("Restore") }
        }
        error?.let { Text(it, color = LocalGlass.current.negative, modifier = Modifier.padding(top = 8.dp)) }
        OutlinedButton(onClick = {
            scope.launch { withContext(Dispatchers.IO) { c.databaseKey.reset(); c.settings.clear() }; onDone() }
        }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) { Text("Start fresh") }
    }
}
