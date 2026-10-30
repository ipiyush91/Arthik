package app.arthik.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.arthik.BuildConfig
import app.arthik.backup.AutoBackupSecret
import app.arthik.container
import androidx.room.withTransaction
import app.arthik.data.db.FxRateEntity
import app.arthik.data.db.clearAll
import app.arthik.data.prefs.AppSettings
import app.arthik.data.repo.SmsAnonymiser
import app.arthik.ui.Routes
import app.arthik.ui.components.GlassCard
import app.arthik.ui.components.ScreenList
import app.arthik.ui.components.SectionTitle
import app.arthik.ui.theme.LocalGlass
import app.arthik.widget.ArthikWidget
import app.arthik.work.ScanWorker
import java.time.LocalDate
import kotlinx.coroutines.launch

private enum class PasswordFor { EXPORT, RESTORE, AUTO }

@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val c = context.container
    val s by c.settings.flow.collectAsStateWithLifecycle(AppSettings())
    val unrecognised by c.sms.unrecognisedCount.collectAsStateWithLifecycle(0)
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf<Pair<PasswordFor, Uri?>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var showFx by remember { mutableStateOf(false) }
    val noScreenLock = remember {
        (context as? androidx.fragment.app.FragmentActivity)?.let {
            BiometricManager.from(it).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        } ?: false
    }

    fun run(label: String, block: suspend () -> String) = scope.launch {
        message = runCatching { block() }.getOrElse { "$label failed: ${it.message}" }
    }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) password = PasswordFor.EXPORT to uri
    }
    val restoreBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) password = PasswordFor.RESTORE to uri
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            password = PasswordFor.AUTO to uri
        }
    }
    val exportWf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) run("Export") { "Exported ${c.csv.exportWealthfolio(uri)} activities" }
    }
    val importWf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run("Import") { "Imported ${c.csv.importWealthfolio(uri)} activities" }
    }
    val exportTxns = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) run("Export") { "Exported ${c.csv.exportTransactions(uri)} transactions" }
    }
    val importRules = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) run("Rule pack") {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            "Imported ${c.importRulePack(uri.lastPathSegment ?: "imported", text)} rules. Rescan to apply them to old SMS."
        }
    }
    val exportUnrecognised = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) run("Export") {
            val list = c.sms.allUnrecognised()
            c.csv.writeText(uri, list.joinToString("\n\n") { "[${it.sender}] ${SmsAnonymiser.anonymise(it.body)}" })
            "Exported ${list.size} anonymised messages"
        }
    }

    ScreenList("Settings") {
        item(key = "data") { SectionTitle("Your data") }
        item(key = "rescan") {
            SettingRow("Rescan SMS", "Reads the last 12 months again. Your edits are kept.") {
                ScanWorker.start(context, 12); message = "Rescan started"
            }
        }
        item(key = "unrecognised") {
            SettingRow("Unrecognised SMS", if (unrecognised == 0) "Nothing to review" else "$unrecognised to review") { nav.navigate(Routes.UNRECOGNISED) }
        }
        item(key = "categories") { SettingRow("Buckets & learned rules", "Add, rename or recolour buckets") { nav.navigate(Routes.CATEGORIES) } }
        item(key = "accounts") { SettingRow("Accounts", "Rename, hide or merge accounts") { nav.navigate(Routes.ACCOUNTS) } }
        item(key = "month") {
            GlassCard(padding = 14.dp) {
                Text("Month starts on day ${s.monthStartDay}", style = MaterialTheme.typography.bodyLarge)
                Text(if (s.monthStartDay == 1) "Calendar month" else "Salary cycle: ${s.monthStartDay} → ${s.monthStartDay - 1} of next month",
                    style = MaterialTheme.typography.bodySmall, color = glass.textSecondary)
                Slider(s.monthStartDay.toFloat(), { v -> scope.launch { c.settings.setMonthStartDay(v.toInt()) } }, valueRange = 1f..28f, steps = 26)
            }
        }
        item(key = "fx") { SettingRow("Foreign currency rates", "Used for card spends abroad (shown as ≈)") { showFx = true } }

        item(key = "security") { SectionTitle("Security & privacy") }
        if (noScreenLock) item(key = "nolock") {
            GlassCard(padding = 14.dp) { Text("Your phone has no screen lock, so Arthik can't lock itself. Set a PIN or fingerprint in Android settings.", color = glass.warning) }
        }
        item(key = "lock") { ToggleRow("App lock", "Fingerprint, face or device PIN", s.appLockEnabled) { scope.launch { c.settings.setAppLock(it) } } }
        item(key = "timeout") {
            GlassCard(padding = 14.dp) {
                Text("Lock after ${s.lockTimeoutSeconds} s in the background", style = MaterialTheme.typography.bodyLarge)
                Slider(s.lockTimeoutSeconds.toFloat(), { v -> scope.launch { c.settings.setLockTimeout(v.toInt()) } }, valueRange = 0f..300f, steps = 9)
            }
        }
        item(key = "widget") {
            ToggleRow("Show amounts on widget", "Off: the widget shows ₹ ••• until you open the app", s.widgetShowAmounts) {
                scope.launch { c.settings.setWidgetShowAmounts(it); ArthikWidget.refresh(context) }
            }
        }

        item(key = "backup") { SectionTitle("Backup") }
        item(key = "export") { SettingRow("Back up now", "Password-protected file. A forgotten password cannot be recovered.") { exportBackup.launch("arthik-${LocalDate.now()}.arthikbk") } }
        item(key = "restore") { SettingRow("Restore from backup", "Replaces everything on this phone") { restoreBackup.launch(arrayOf("*/*")) } }
        item(key = "auto") {
            ToggleRow("Weekly automatic backup", s.autoBackupFolder?.let { "Into the folder you picked" } ?: "Pick a folder", s.autoBackupEnabled) { on ->
                if (on) pickFolder.launch(null) else scope.launch { c.settings.setAutoBackup(false, null) }
            }
        }

        item(key = "io") { SectionTitle("Import & export") }
        item(key = "wf_out") { SettingRow("Export to Wealthfolio (CSV)", "Investment accounts and activities") { exportWf.launch("arthik-wealthfolio.csv") } }
        item(key = "wf_in") { SettingRow("Import from Wealthfolio (CSV)", "Bring holdings over from desktop") { importWf.launch(arrayOf("text/*")) } }
        item(key = "txn_out") { SettingRow("Export transactions (CSV)", "All transactions for spreadsheets") { exportTxns.launch("arthik-transactions.csv") } }
        item(key = "rules_in") { SettingRow("Import rule pack (JSON)", "Add SMS formats without updating the app") { importRules.launch(arrayOf("application/json", "text/*")) } }
        item(key = "unrec_out") { SettingRow("Export unrecognised SMS (anonymised)", "Names, numbers and UPI IDs are masked") { exportUnrecognised.launch("arthik-unrecognised.txt") } }

        item(key = "danger") { SectionTitle("Danger zone") }
        item(key = "clear") { SettingRow("Clear all data", "Deletes everything Arthik stored. Your SMS stay untouched.") { confirmClear = true } }
        item(key = "about") {
            Text("Arthik ${BuildConfig.VERSION_NAME} · works offline, no internet permission",
                style = MaterialTheme.typography.bodySmall, color = glass.textSecondary, modifier = Modifier.padding(top = 16.dp))
        }
    }

    message?.let { m ->
        AlertDialog(onDismissRequest = { message = null }, text = { Text(m) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } })
    }
    password?.let { (mode, uri) ->
        PasswordDialog(confirm = mode != PasswordFor.RESTORE, onDismiss = { password = null }) { pw ->
            password = null
            when (mode) {
                PasswordFor.EXPORT -> run("Backup") { c.backup.export(uri!!, pw); "Backup saved" }
                PasswordFor.RESTORE -> run("Restore") { c.backup.restore(uri!!, pw); ArthikWidget.refresh(context); "Restored" }
                PasswordFor.AUTO -> run("Auto-backup") {
                    AutoBackupSecret(context).set(pw); c.settings.setAutoBackup(true, uri.toString()); "Weekly backups turned on"
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all data?") },
        text = { Text("This deletes all transactions, accounts, budgets and rules from Arthik. Make a backup first if you may want them back.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; run("Clear") { c.db.withTransaction { c.db.maintenance().clearAll() }; c.settings.setInitialScanDone(false); "All data cleared" } }) { Text("Delete everything") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
    if (showFx) FxDialog(onDismiss = { showFx = false })
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(onClick = onClick, padding = 14.dp) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textSecondary)
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LocalGlass.current.textSecondary)
            }
            Switch(checked, onChange)
        }
    }
}

@Composable
private fun PasswordDialog(confirm: Boolean, onDismiss: () -> Unit, onOk: (CharArray) -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val ok = p1.length >= 6 && (!confirm || p1 == p2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(p1, { p1 = it }, label = { Text("Password (6+ characters)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (confirm) OutlinedTextField(p2, { p2 = it }, label = { Text("Repeat password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (confirm) Text("If you forget this password the backup cannot be opened.", style = MaterialTheme.typography.bodySmall,
                    color = LocalGlass.current.warning)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onOk(p1.toCharArray()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FxDialog(onDismiss: () -> Unit) {
    val c = LocalContext.current.container
    val scope = rememberCoroutineScope()
    val rates by c.db.fx().observeAll().collectAsStateWithLifecycle(emptyList())
    var currency by remember { mutableStateOf("USD") }
    var rate by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency rates (₹ per unit)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                rates.forEach { Text("${it.currency}: ₹${it.inrPerUnit}") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Code") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(rate, { rate = it }, label = { Text("₹ rate") }, singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = currency.length == 3 && rate.toDoubleOrNull() != null, onClick = {
                scope.launch { c.db.fx().upsert(FxRateEntity(currency, rate.toDouble())) }; rate = ""
            }) { Text("Save rate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
