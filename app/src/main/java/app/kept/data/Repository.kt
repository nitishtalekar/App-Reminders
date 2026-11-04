package app.kept.data

import app.kept.domain.Birthday
import app.kept.domain.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists reminders/birthdays and tells the rest of the system (widget, notifications)
 * to catch up after every mutation via [onChanged].
 */
class Repository(
    private val db: AppDatabase,
    private val onChanged: suspend () -> Unit,
) {
    val reminders: Flow<List<Reminder>> = db.reminders().observeAll().map { l -> l.map { it.toDomain() } }
    val birthdays: Flow<List<Birthday>> = db.birthdays().observeAll().map { l -> l.map { it.toDomain() } }

    suspend fun allReminders() = db.reminders().getAll().map { it.toDomain() }
    suspend fun allBirthdays() = db.birthdays().getAll().map { it.toDomain() }
    suspend fun reminder(id: Long) = db.reminders().get(id)?.toDomain()
    suspend fun birthday(id: Long) = db.birthdays().get(id)?.toDomain()

    suspend fun save(r: Reminder) { db.reminders().upsert(r.toEntity()); onChanged() }
    suspend fun deleteReminder(id: Long) { db.reminders().delete(id); onChanged() }
    suspend fun setCleared(id: Long, cleared: Boolean) {
        db.reminders().setCleared(id, if (cleared) System.currentTimeMillis() else null)
        onChanged()
    }

    /** Permanently removes reminders that were cleared more than 72 hours ago. */
    suspend fun purgeExpiredCleared(nowMillis: Long = System.currentTimeMillis()) {
        db.reminders().deleteClearedBefore(nowMillis - app.kept.domain.Eligibility.CLEARED_RETENTION_MILLIS)
    }

    suspend fun save(b: Birthday) { db.birthdays().upsert(b.toEntity()); onChanged() }
    suspend fun deleteBirthday(id: Long) { db.birthdays().delete(id); onChanged() }
}
