package app.kept.data

import android.content.Context
import androidx.room.*
import app.kept.domain.Birthday
import app.kept.domain.Reminder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String,
    /** LocalDate.toEpochDay() - a zone-independent calendar date. */
    val dateEpochDay: Long,
    val leadDays: Int,
    val clearedAt: Long?,
)

@Entity(tableName = "birthdays")
data class BirthdayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val month: Int,
    val day: Int,
    val birthYear: Int?,
    val leadDays: Int,
    val notes: String,
)

fun ReminderEntity.toDomain() = Reminder(id, title, notes, LocalDate.ofEpochDay(dateEpochDay), leadDays, clearedAt)
fun Reminder.toEntity() = ReminderEntity(id, title, notes, date.toEpochDay(), leadDays, clearedAtMillis)
fun BirthdayEntity.toDomain() = Birthday(id, name, month, day, birthYear, leadDays, notes)
fun Birthday.toEntity() = BirthdayEntity(id, name, month, day, birthYear, leadDays, notes)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders") fun observeAll(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders") suspend fun getAll(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun get(id: Long): ReminderEntity?
    @Upsert suspend fun upsert(e: ReminderEntity): Long
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE reminders SET clearedAt = :at WHERE id = :id") suspend fun setCleared(id: Long, at: Long?)
    @Query("DELETE FROM reminders WHERE clearedAt IS NOT NULL AND clearedAt <= :cutoff") suspend fun deleteClearedBefore(cutoff: Long): Int
}

@Dao
interface BirthdayDao {
    @Query("SELECT * FROM birthdays") fun observeAll(): Flow<List<BirthdayEntity>>
    @Query("SELECT * FROM birthdays") suspend fun getAll(): List<BirthdayEntity>
    @Query("SELECT * FROM birthdays WHERE id = :id") suspend fun get(id: Long): BirthdayEntity?
    @Upsert suspend fun upsert(e: BirthdayEntity): Long
    @Query("DELETE FROM birthdays WHERE id = :id") suspend fun delete(id: Long)
}

// The schema is exported to app/schemas. When it changes: bump `version` and add a Migration
// in AppDatabase.build(). Never use destructive migration - this is the user's personal data.
@Database(entities = [ReminderEntity::class, BirthdayEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun birthdays(): BirthdayDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kept.db")
                .build()
    }
}
