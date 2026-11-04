package app.kept

import android.app.Application
import android.content.Context
import app.kept.data.AppDatabase
import app.kept.data.Repository
import app.kept.data.SettingsStore
import app.kept.domain.AgendaItem
import app.kept.domain.Eligibility
import app.kept.notify.Notifier
import app.kept.notify.Scheduler
import app.kept.widget.WidgetUpdater
import java.time.LocalDate

/** Tiny manual "DI container": one database, one settings store, one repository. */
class KeptApp : Application() {
    lateinit var db: AppDatabase
    lateinit var settings: SettingsStore
    lateinit var repository: Repository

    /** Process-wide scope for work that must finish even if the Activity goes away (saves, syncs). */
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.build(this)
        settings = SettingsStore(this)
        repository = Repository(db) { syncSystem() }
        Notifier.ensureChannel(this)
    }

    /**
     * Pushes the current data out to everything that lives outside the app UI:
     * stale notifications are removed, the next daily alarm is (re)scheduled and the widget is refreshed.
     */
    suspend fun syncSystem() {
        repository.purgeExpiredCleared()
        Notifier.pruneStale(this)
        Scheduler.scheduleAll(this)
        WidgetUpdater.refresh(this)
    }

    /** Today's relevant items, computed with the shared eligibility rules. */
    suspend fun activeAgenda(today: LocalDate = LocalDate.now()): List<AgendaItem> =
        Eligibility.activeAgenda(repository.allReminders(), repository.allBirthdays(), today)
}

val Context.app: KeptApp get() = applicationContext as KeptApp
