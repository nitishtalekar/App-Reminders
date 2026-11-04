package app.kept.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kept.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Single entry point for everything that happens while the UI is closed:
 * the daily alarm, midnight rollover, boot, app update, clock/zone changes and the
 * "Mark done" notification action.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.app
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Scheduler.ACTION_DAILY -> {
                        Notifier.postToday(app)
                        app.syncSystem()
                    }
                    Notifier.ACTION_CLEAR -> {
                        val id = intent.getLongExtra(Notifier.EXTRA_ID, -1)
                        if (id >= 0) app.repository.setCleared(id, true) // also prunes + refreshes widget
                    }
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED -> {
                        // Alarms are cleared by reboot and depend on the zone: rebuild them.
                        // If today's notification time already passed while the phone was off, catch up now.
                        val s = app.settings.get()
                        if (LocalDateTime.now().toLocalTime() >= LocalTime.of(s.notifyHour, s.notifyMinute)) Notifier.postToday(app)
                        app.syncSystem()
                    }
                    else -> app.syncSystem() // MIDNIGHT, DATE_CHANGED
                }
            } finally {
                pending.finish()
            }
        }
    }
}
