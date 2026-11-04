package app.kept.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.kept.MainActivity
import app.kept.R
import app.kept.app
import app.kept.domain.AgendaItem
import app.kept.domain.ItemKind
import app.kept.domain.Phrases
import java.time.LocalDate

object Notifier {
    const val CHANNEL_ID = "daily"
    const val ACTION_CLEAR = "app.kept.action.CLEAR"
    const val EXTRA_ID = "id"
    private const val TAG = "kept"
    private const val SUMMARY_ID = 1
    private const val GROUP = "kept_group"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One notification per active reminder or birthday, once a day."
            }
        )
    }

    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Stable per item, so re-posting replaces instead of duplicating. Offset keeps clear of SUMMARY_ID. */
    private fun idFor(item: AgendaItem): Int = 1000 + (item.key.hashCode() and 0x3FFFFFFF)

    /**
     * Posts today's batch - at most once per calendar day (guarded by lastNotifiedEpochDay).
     * [force] skips the "already done today" guard (not the permission/enabled checks).
     */
    suspend fun postToday(context: Context, today: LocalDate = LocalDate.now()) {
        val app = context.app
        val settings = app.settings.get()
        if (!settings.notificationsEnabled || !canPost(context)) return
        if (settings.lastNotifiedEpochDay == today.toEpochDay()) return

        val items = app.activeAgenda(today)
        items.forEach { post(context, it, items.size > 1) }
        if (items.size > 1) postSummary(context, items)
        app.settings.setLastNotified(today.toEpochDay())
    }

    private fun contentIntent(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun post(context: Context, item: AgendaItem, grouped: Boolean) {
        val b = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(item.title)
            .setContentText(Phrases.status(item))
            .setContentIntent(contentIntent(context))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setColor(if (item.kind == ItemKind.BIRTHDAY) 0xFFB8A2FF.toInt() else 0xFF5EC8F2.toInt())
        if (item.notes.isNotBlank()) b.setStyle(NotificationCompat.BigTextStyle().bigText("${Phrases.status(item)}\n${item.notes}"))
        if (grouped) b.setGroup(GROUP)
        if (item.kind == ItemKind.REMINDER) {
            val clear = PendingIntent.getBroadcast(
                context, idFor(item),
                Intent(context, ReminderReceiver::class.java).setAction(ACTION_CLEAR).putExtra(EXTRA_ID, item.sourceId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "Mark done", clear)
        }
        notify(context, idFor(item), b.build())
    }

    private fun postSummary(context: Context, items: List<AgendaItem>) {
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${items.size} things need you")
            .setGroup(GROUP).setGroupSummary(true)
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setColor(0xFF5EC8F2.toInt())
            .build()
        notify(context, SUMMARY_ID, n)
    }

    @Suppress("MissingPermission")
    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        if (canPost(context)) NotificationManagerCompat.from(context).notify(TAG, id, n)
    }

    fun cancelFor(context: Context, item: AgendaItem) =
        NotificationManagerCompat.from(context).cancel(TAG, idFor(item))

    /** Removes notifications whose item is no longer active (cleared, deleted, edited out of its window). */
    suspend fun pruneStale(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val ours = nm.activeNotifications.filter { it.tag == TAG }
        if (ours.isEmpty()) return
        val agenda = context.app.activeAgenda()
        val activeIds = agenda.map { idFor(it) }.toSet()
        // Only items we already notified about remain relevant; the rest of today's agenda is left alone.
        val remaining = ours.count { it.id != SUMMARY_ID && it.id in activeIds }
        ours.forEach { sbn ->
            if (sbn.id != SUMMARY_ID && sbn.id !in activeIds) nm.cancel(TAG, sbn.id)
        }
        if (ours.any { it.id == SUMMARY_ID }) {
            if (remaining < 2) nm.cancel(TAG, SUMMARY_ID)
            else postSummary(context, agenda.filter { a -> ours.any { it.id == idFor(a) } })
        }
    }
}
