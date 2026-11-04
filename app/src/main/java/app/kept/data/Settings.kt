package app.kept.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val notificationsEnabled: Boolean = true,
    val notifyHour: Int = 9,
    val notifyMinute: Int = 0,
    val defaultReminderLead: Int = 7,
    val defaultBirthdayLead: Int = 3,
    /** Epoch day of the last day a daily notification batch was posted - prevents duplicates. */
    val lastNotifiedEpochDay: Long = -1,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val enabled = booleanPreferencesKey("notifications_enabled")
        val hour = intPreferencesKey("notify_hour")
        val minute = intPreferencesKey("notify_minute")
        val reminderLead = intPreferencesKey("default_reminder_lead")
        val birthdayLead = intPreferencesKey("default_birthday_lead")
        val lastNotified = longPreferencesKey("last_notified_epoch_day")
    }

    private fun Preferences.toSettings() = Settings(
        notificationsEnabled = this[K.enabled] ?: true,
        notifyHour = this[K.hour] ?: 9,
        notifyMinute = this[K.minute] ?: 0,
        defaultReminderLead = this[K.reminderLead] ?: 7,
        defaultBirthdayLead = this[K.birthdayLead] ?: 3,
        lastNotifiedEpochDay = this[K.lastNotified] ?: -1,
    )

    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }
    suspend fun get(): Settings = flow.first()

    suspend fun setEnabled(v: Boolean) { context.dataStore.edit { it[K.enabled] = v } }
    suspend fun setTime(h: Int, m: Int) { context.dataStore.edit { it[K.hour] = h; it[K.minute] = m } }
    suspend fun setReminderLead(v: Int) { context.dataStore.edit { it[K.reminderLead] = v } }
    suspend fun setBirthdayLead(v: Int) { context.dataStore.edit { it[K.birthdayLead] = v } }
    suspend fun setLastNotified(day: Long) { context.dataStore.edit { it[K.lastNotified] = day } }
}
