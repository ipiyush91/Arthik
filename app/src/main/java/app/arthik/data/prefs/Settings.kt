package app.arthik.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("arthik_settings")

data class AppSettings(
    val onboardingDone: Boolean = false,
    val appLockEnabled: Boolean = true,
    val lockTimeoutSeconds: Int = 60,
    /** 1 = calendar month; otherwise the month starts on this day (salary cycle). */
    val monthStartDay: Int = 1,
    val widgetShowAmounts: Boolean = false,
    val autoBackupEnabled: Boolean = false,
    val autoBackupFolder: String? = null,
    val lastBackupAt: Long = 0,
    val initialScanDone: Boolean = false,
    val reminderDaysBefore: List<Int> = listOf(5, 1, 0),
)

class Settings(private val context: Context) {
    private object K {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val appLock = booleanPreferencesKey("app_lock")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val monthStart = intPreferencesKey("month_start")
        val widgetAmounts = booleanPreferencesKey("widget_amounts")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val autoBackupFolder = stringPreferencesKey("auto_backup_folder")
        val lastBackup = longPreferencesKey("last_backup")
        val initialScan = booleanPreferencesKey("initial_scan")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        onboardingDone = this[K.onboardingDone] ?: false,
        appLockEnabled = this[K.appLock] ?: true,
        lockTimeoutSeconds = this[K.lockTimeout] ?: 60,
        monthStartDay = this[K.monthStart] ?: 1,
        widgetShowAmounts = this[K.widgetAmounts] ?: false,
        autoBackupEnabled = this[K.autoBackup] ?: false,
        autoBackupFolder = this[K.autoBackupFolder],
        lastBackupAt = this[K.lastBackup] ?: 0,
        initialScanDone = this[K.initialScan] ?: false,
    )

    suspend fun setOnboardingDone(v: Boolean) = context.dataStore.edit { it[K.onboardingDone] = v }
    suspend fun setAppLock(v: Boolean) = context.dataStore.edit { it[K.appLock] = v }
    suspend fun setLockTimeout(seconds: Int) = context.dataStore.edit { it[K.lockTimeout] = seconds }
    suspend fun setMonthStartDay(day: Int) = context.dataStore.edit { it[K.monthStart] = day.coerceIn(1, 28) }
    suspend fun setWidgetShowAmounts(v: Boolean) = context.dataStore.edit { it[K.widgetAmounts] = v }
    suspend fun setAutoBackup(enabled: Boolean, folder: String?) = context.dataStore.edit {
        it[K.autoBackup] = enabled
        if (folder != null) it[K.autoBackupFolder] = folder
    }
    suspend fun setLastBackup(at: Long) = context.dataStore.edit { it[K.lastBackup] = at }
    suspend fun setInitialScanDone(v: Boolean) = context.dataStore.edit { it[K.initialScan] = v }
    suspend fun clear() = context.dataStore.edit { it.clear() }
}
