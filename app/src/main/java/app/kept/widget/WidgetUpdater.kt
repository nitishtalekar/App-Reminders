package app.kept.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import app.kept.MainActivity
import app.kept.R
import app.kept.app
import app.kept.domain.AgendaItem
import app.kept.domain.ItemKind
import app.kept.domain.Phrases
import java.time.format.DateTimeFormatter

enum class WidgetSize(val layout: Int, val itemLayout: Int) {
    COMPACT(R.layout.widget_compact, R.layout.widget_item_compact),
    WIDE(R.layout.widget_wide, R.layout.widget_item_wide),
    LARGE(R.layout.widget_large, R.layout.widget_item_large);

    companion object {
        // Breakpoints in dp (the launcher reports the widget's current size).
        fun forDp(widthDp: Float, heightDp: Float): WidgetSize = when {
            widthDp >= 220 && heightDp >= 170 -> LARGE
            widthDp >= 220 -> WIDE
            else -> COMPACT
        }
    }
}

/**
 * How rotation works - two separate concerns:
 *
 *  1. Cycling through items already on the widget: a plain [android.widget.ViewFlipper] with
 *     autoStart + flipInterval. The items are added to it as child RemoteViews, so the flipping is done
 *     by the launcher's own view hierarchy and Handler timer. None of our code runs while it rotates,
 *     it pauses when the widget isn't visible, and it does not depend on the launcher implementing
 *     `autoAdvanceViewId` (the stock Pixel/AOSP launcher on Android 15 does not - verified on an emulator).
 *
 *  2. Refreshing *what* is in the flipper: [refresh], called only when data changes, the date rolls over,
 *     the widget is resized, or the system's (hourly) update period fires.
 */
object WidgetUpdater {
    /** Keeps the RemoteViews payload comfortably below the Binder transaction limit. */
    private const val MAX_ITEMS = 15
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")

    /** Rebuilds all placed widgets (or just [onlyIds]) from the database. */
    suspend fun refresh(context: Context, onlyIds: IntArray? = null) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = onlyIds ?: mgr.getAppWidgetIds(ComponentName(context, RotatingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val items = context.app.activeAgenda()
        ids.forEach { update(context, mgr, it, items) }
    }

    private fun update(context: Context, mgr: AppWidgetManager, id: Int, items: List<AgendaItem>) {
        val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The launcher picks the entry that fits the widget's current size - and re-picks on resize.
            RemoteViews(
                mapOf(
                    SizeF(110f, 110f) to build(context, WidgetSize.COMPACT, items),
                    SizeF(220f, 110f) to build(context, WidgetSize.WIDE, items),
                    SizeF(220f, 170f) to build(context, WidgetSize.LARGE, items),
                )
            )
        } else {
            val o: Bundle = mgr.getAppWidgetOptions(id)
            build(
                context,
                WidgetSize.forDp(
                    o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).toFloat(),
                    o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).toFloat(),
                ),
                items,
            )
        }
        mgr.updateAppWidget(id, views)
    }

    private fun build(context: Context, size: WidgetSize, items: List<AgendaItem>): RemoteViews {
        val rv = RemoteViews(context.packageName, size.layout)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val shown = items.take(MAX_ITEMS)
        if (shown.isEmpty()) {
            rv.setViewVisibility(R.id.flipper, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setOnClickPendingIntent(R.id.empty, open)
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.flipper, View.VISIBLE)
        rv.removeAllViews(R.id.flipper)
        shown.forEachIndexed { i, item ->
            rv.addView(R.id.flipper, itemView(context, size, item, i, shown.size, open))
        }
        return rv
    }

    private fun itemView(context: Context, size: WidgetSize, item: AgendaItem, pos: Int, total: Int, open: PendingIntent): RemoteViews {
        val rv = RemoteViews(context.packageName, size.itemLayout)
        val tone = if (item.isOverdue) Color.parseColor("#FF7A6B") else Color.parseColor("#F0B24B")
        val isBirthday = item.kind == ItemKind.BIRTHDAY

        rv.setTextViewText(R.id.title, item.title)
        rv.setTextViewText(R.id.status, Phrases.status(item))
        rv.setTextColor(R.id.status, tone)
        rv.setTextViewText(R.id.position, if (total > 1) "${pos + 1}/$total" else "")

        // Shape language: reminders are squares, birthdays are circles. Same in the app.
        rv.setViewVisibility(R.id.marker_square, if (isBirthday) View.GONE else View.VISIBLE)
        rv.setViewVisibility(R.id.marker_circle, if (isBirthday) View.VISIBLE else View.GONE)
        rv.setInt(R.id.marker_square, "setColorFilter", tone)
        rv.setInt(R.id.marker_circle, "setColorFilter", tone)

        if (size != WidgetSize.COMPACT) {
            val d = item.date.format(dateFmt)
            rv.setTextViewText(R.id.date, if (isBirthday && item.turningAge != null) "$d  ·  turns ${item.turningAge}" else d)
        }
        if (size == WidgetSize.WIDE) {
            val d = item.daysUntil
            rv.setTextViewText(R.id.big_number, kotlin.math.abs(d).toString())
            rv.setTextColor(R.id.big_number, tone)
            rv.setTextViewText(
                R.id.big_label,
                when {
                    d < 0 -> if (d == -1) "day late" else "days late"
                    d == 0 -> "today"
                    d == 1 -> "day"
                    else -> "days"
                },
            )
        }
        if (size == WidgetSize.LARGE) {
            val has = item.notes.isNotBlank()
            rv.setViewVisibility(R.id.notes, if (has) View.VISIBLE else View.INVISIBLE)
            if (has) rv.setTextViewText(R.id.notes, item.notes)
        }
        rv.setOnClickPendingIntent(R.id.item_root, open)
        return rv
    }
}
