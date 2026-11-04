package app.kept.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.kept.app
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Two *inexact* alarms - no SCHEDULE_EXACT_ALARM permission, battery friendly:
 *  - DAILY: next occurrence of the user's notification time. The receiver posts the batch
 *    and schedules the next one.
 *  - MIDNIGHT: shortly after local midnight, so the widget re-evaluates "days left"
 *    even if the DATE_CHANGED broadcast is missed.
 * Both are recomputed from the *current* zone whenever the data, settings, time or zone change.
 * Android may deliver them a little late in Doze; that is acceptable for a daily reminder.
 */
object Scheduler {
    const val ACTION_DAILY = "app.kept.action.DAILY"
    const val ACTION_MIDNIGHT = "app.kept.action.MIDNIGHT"

    suspend fun scheduleAll(context: Context) {
        val s = context.app.settings.get()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)

        val am = context.getSystemService(AlarmManager::class.java)

        if (s.notificationsEnabled) {
            val time = LocalTime.of(s.notifyHour, s.notifyMinute)
            var next = LocalDateTime.of(now.toLocalDate(), time)
            if (!next.isAfter(now)) next = next.plusDays(1)
            set(context, am, ACTION_DAILY, 1, next.atZone(zone).toInstant().toEpochMilli())
        } else {
            cancel(context, am, ACTION_DAILY, 1)
        }

        val midnight = LocalDateTime.of(LocalDate.now(zone).plusDays(1), LocalTime.of(0, 0, 30))
        set(context, am, ACTION_MIDNIGHT, 2, midnight.atZone(zone).toInstant().toEpochMilli())
    }

    private fun pending(context: Context, action: String, code: Int, flags: Int = 0) =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, ReminderReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or flags,
        )

    private fun set(context: Context, am: AlarmManager, action: String, code: Int, atMillis: Long) {
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending(context, action, code, PendingIntent.FLAG_UPDATE_CURRENT))
    }

    private fun cancel(context: Context, am: AlarmManager, action: String, code: Int) {
        am.cancel(pending(context, action, code))
    }
}
