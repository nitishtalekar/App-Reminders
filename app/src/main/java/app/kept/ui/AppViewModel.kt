package app.kept.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.kept.BirthdayJson
import app.kept.KeptApp
import app.kept.data.Settings
import app.kept.domain.Birthday
import app.kept.domain.Reminder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class UiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val reminders: List<Reminder> = emptyList(),
    val birthdays: List<Birthday> = emptyList(),
    val settings: Settings = Settings(),
)

class AppViewModel(private val app: KeptApp) : ViewModel() {
    private val today = MutableStateFlow(LocalDate.now())

    val state: StateFlow<UiState> = combine(
        app.repository.reminders, app.repository.birthdays, app.settings.flow, today,
    ) { r, b, s, t -> UiState(true, t, r, b, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Called on resume so "today" is right after the app sat in the background across midnight. */
    fun refreshToday() { today.value = LocalDate.now() }

    // Mutations run on the app scope so they finish (and sync widget/alarms) even if the UI closes.
    fun save(r: Reminder) { app.scope.launch { app.repository.save(r) } }
    fun save(b: Birthday) { app.scope.launch { app.repository.save(b) } }
    fun deleteReminder(id: Long) { app.scope.launch { app.repository.deleteReminder(id) } }
    fun deleteBirthday(id: Long) { app.scope.launch { app.repository.deleteBirthday(id) } }
    fun setCleared(id: Long, cleared: Boolean) { app.scope.launch { app.repository.setCleared(id, cleared) } }

    fun setNotificationsEnabled(v: Boolean) { app.scope.launch { app.settings.setEnabled(v); app.syncSystem() } }
    fun setNotifyTime(h: Int, m: Int) { app.scope.launch { app.settings.setTime(h, m); app.syncSystem() } }
    fun setReminderLead(v: Int) { app.scope.launch { app.settings.setReminderLead(v) } }
    fun setBirthdayLead(v: Int) { app.scope.launch { app.settings.setBirthdayLead(v) } }

    /** Reads straight from the database so the export is always complete, never a stale UI snapshot. */
    suspend fun birthdayJson(): Pair<String, Int> {
        val all = app.repository.allBirthdays()
        return BirthdayJson.export(all) to all.size
    }

    class Factory(private val app: KeptApp) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
    }
}
