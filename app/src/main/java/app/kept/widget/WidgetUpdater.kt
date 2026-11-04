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
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.kept.MainActivity
import app.kept.R
import app.kept.app
import app.kept.domain.AgendaItem
import app.kept.domain.ItemKind
import app.kept.domain.Phrases
import java.time.format.DateTimeFormatter

enum class WidgetSize(val layout: Int, val layoutB: Int) {
    COMPACT(R.layout.widget_compact, R.layout.widget_compact_b),
    WIDE(R.layout.widget_wide, R.layout.widget_wide_b),
    LARGE(R.layout.widget_large, R.layout.widget_large_b);

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
 * How the widget works:
 *
 *  1. Rotation. Every text element (marker, counter, title, status, date, ...) lives in its own
 *     [android.widget.ViewFlipper] (autoStart, 14 s), plus one for the card background. They all start
 *     together and flip together, so the card changes as a whole, but each element has a slightly
 *     different animation delay - that stagger is the "micro animation" of the text. The flipping runs in
 *     the launcher's own process: nothing of ours runs while it rotates, and it does not depend on the
 *     launcher supporting `autoAdvanceViewId` (the stock Pixel launcher on Android 15 does not).
 *
 *  2. Content refresh: [refresh] - only when data changes, at midnight, on resize, or hourly.
 *
 *  3. Manual previous/next: [flip]. The launcher replays child-adding actions on partial updates, so we
 *     rebuild the widget showing the current item and step the flippers (showNext/showPrevious) in the same
 *     update, which plays the slide. A twin layout (A/B) forces a fresh flipper so its 14 s timer restarts.
 */
object WidgetUpdater {
    /** Keeps the RemoteViews payload comfortably below the Binder transaction limit. */
    private const val MAX_ITEMS = 15
    private const val FLIP_INTERVAL_MS = 14_000L
    private const val APPLY_LATENCY_MS = 900L
    private const val PREFS = "widget_state"
    private val dateFmt = DateTimeFormatter.ofPattern("EEE, MMM d")

    private const val ACCENT = "#5EC8F2"
    private const val VIOLET = "#B8A2FF"
    private const val DANGER = "#FF7D8E"

    /** Colours and background layouts for one widget variant (colour or monochrome). */
    private class Theme(val mono: Boolean) {
        val text = Color.parseColor(if (mono) "#F2F2F2" else "#E8EEF6")
        val muted = Color.parseColor(if (mono) "#8C8C8C" else "#8FA0B8")
        private val reminder = Color.parseColor(if (mono) "#CFCFCF" else ACCENT)
        private val birthday = Color.parseColor(if (mono) "#9E9E9E" else VIOLET)
        private val overdue = Color.parseColor(if (mono) "#FFFFFF" else DANGER)
        val rootBg = if (mono) R.drawable.widget_bg_mono else R.drawable.widget_bg
        fun bgLayout(isBirthday: Boolean) = when {
            mono -> if (isBirthday) R.layout.wi_bg_mono_birthday else R.layout.wi_bg_mono
            else -> if (isBirthday) R.layout.wi_bg_birthday else R.layout.wi_bg_reminder
        }
        fun tone(item: AgendaItem) = when {
            item.isOverdue -> overdue
            item.kind == ItemKind.BIRTHDAY -> birthday
            else -> reminder
        }
    }

    /** Which variant a placed widget is, from the provider it was added as. */
    private fun themeFor(mgr: AppWidgetManager, id: Int) =
        Theme(mgr.getAppWidgetInfo(id)?.provider?.className == MonoWidgetProvider::class.java.name)

    /** Rebuilds all placed widgets (or just [onlyIds]) from the database, keeping each one's current position. */
    suspend fun refresh(context: Context, onlyIds: IntArray? = null) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = onlyIds ?: (mgr.getAppWidgetIds(ComponentName(context, RotatingWidgetProvider::class.java)) +
            mgr.getAppWidgetIds(ComponentName(context, MonoWidgetProvider::class.java)))
        if (ids.isEmpty()) return
        val items = context.app.activeAgenda().take(MAX_ITEMS)
        ids.forEach { update(context, mgr, it, items, startAt = currentIndex(context, it, items.size), step = 0) }
    }

    /** Manual previous/next from the widget's buttons (see class comment). */
    suspend fun flip(context: Context, id: Int, forward: Boolean) {
        val mgr = AppWidgetManager.getInstance(context)
        val items = context.app.activeAgenda().take(MAX_ITEMS)
        if (items.size < 2) return
        val cur = currentIndex(context, id, items.size)
        val step = if (forward) 1 else -1
        update(context, mgr, id, items, startAt = cur, step = step)
    }

    /** Best estimate of the item the launcher is showing now: where we last started, plus auto-advances since. */
    private fun currentIndex(context: Context, id: Int, count: Int): Int {
        if (count == 0) return 0
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = p.getInt("pos_$id", 0)
        val since = p.getLong("t_$id", System.currentTimeMillis())
        // The launcher applies our update a moment after we stamp it, so its first flip lands slightly after 14 s.
        val steps = ((System.currentTimeMillis() - since - APPLY_LATENCY_MS) / FLIP_INTERVAL_MS).coerceAtLeast(0)
        return Math.floorMod(start + steps, count.toLong()).toInt()
    }

    /**
     * [startAt] is the item shown first. With [step] != 0 the flippers are then advanced by one in the same
     * update, so the launcher animates from [startAt] to its neighbour.
     */
    private fun update(context: Context, mgr: AppWidgetManager, id: Int, items: List<AgendaItem>, startAt: Int, step: Int) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val shownIndex = if (items.isEmpty()) 0 else Math.floorMod(startAt + step, items.size)
        val variantB = p.getBoolean("b_$id", false) xor (step != 0)
        val theme = themeFor(mgr, id)
        p.edit().putInt("pos_$id", shownIndex).putLong("t_$id", System.currentTimeMillis()).putBoolean("b_$id", variantB).apply()

        // Rotate so the first child is [startAt]; labels keep their true numbers.
        val order = items.indices.map { (startAt + it) % items.size }
        val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The launcher picks the entry that fits the widget's current size - and re-picks on resize.
            RemoteViews(
                mapOf(
                    SizeF(110f, 110f) to build(context, id, WidgetSize.COMPACT, variantB, items, order, step, theme),
                    SizeF(220f, 110f) to build(context, id, WidgetSize.WIDE, variantB, items, order, step, theme),
                    SizeF(220f, 170f) to build(context, id, WidgetSize.LARGE, variantB, items, order, step, theme),
                )
            )
        } else {
            build(context, id, sizeFor(mgr, id), variantB, items, order, step, theme)
        }
        mgr.updateAppWidget(id, views)
    }

    private fun sizeFor(mgr: AppWidgetManager, id: Int): WidgetSize {
        val o: Bundle = mgr.getAppWidgetOptions(id)
        return WidgetSize.forDp(
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).toFloat(),
            o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).toFloat(),
        )
    }

    const val ACTION_NEXT = "app.kept.widget.NEXT"
    const val ACTION_PREV = "app.kept.widget.PREV"

    private fun flipIntent(context: Context, id: Int, action: String): PendingIntent = PendingIntent.getBroadcast(
        context, id * 2 + if (action == ACTION_NEXT) 1 else 0,
        Intent(context, RotatingWidgetProvider::class.java).setAction(action).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Flippers present in each layout (all are filled with one child per item, in the same order). */
    private fun flippersFor(size: WidgetSize): List<Int> = buildList {
        add(R.id.bg_flipper); add(R.id.marker_flipper); add(R.id.counter_flipper); add(R.id.title_flipper)
        add(R.id.status_flipper)
        if (size != WidgetSize.COMPACT) add(R.id.date_flipper)
        if (size == WidgetSize.WIDE) { add(R.id.number_flipper); add(R.id.label_flipper) }
        if (size == WidgetSize.LARGE) add(R.id.notes_flipper)
    }

    private fun build(
        context: Context, id: Int, size: WidgetSize, variantB: Boolean,
        items: List<AgendaItem>, order: List<Int>, step: Int, theme: Theme,
    ): RemoteViews {
        val rv = RemoteViews(context.packageName, if (variantB) size.layoutB else size.layout)
        val pkg = context.packageName
        rv.setInt(R.id.widget_root, "setBackgroundResource", theme.rootBg)
        rv.setTextColor(R.id.empty_title, theme.text)
        rv.setTextColor(R.id.empty_sub, theme.muted)
        rv.setOnClickPendingIntent(R.id.btn_prev, flipIntent(context, id, ACTION_PREV))
        rv.setOnClickPendingIntent(R.id.btn_next, flipIntent(context, id, ACTION_NEXT))
        // Nothing to flip through with 0 or 1 items.
        rv.setViewVisibility(R.id.flip_buttons, if (items.size > 1) View.VISIBLE else View.GONE)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        rv.setOnClickPendingIntent(R.id.tap_area, open)
        rv.setOnClickPendingIntent(R.id.empty, open)

        if (items.isEmpty()) {
            rv.setViewVisibility(R.id.content, View.GONE)
            rv.setViewVisibility(R.id.bg_flipper, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            return rv
        }
        rv.setViewVisibility(R.id.empty, View.GONE)
        rv.setViewVisibility(R.id.content, View.VISIBLE)
        rv.setViewVisibility(R.id.bg_flipper, View.VISIBLE)

        val flippers = flippersFor(size)
        flippers.forEach { rv.removeAllViews(it) }
        order.forEach { i -> addItem(rv, pkg, size, items[i], i, items.size, theme) }
        if (step != 0) flippers.forEach { if (step > 0) rv.showNext(it) else rv.showPrevious(it) }
        return rv
    }

    /** Adds one child (this item's piece) to every flipper. */
    private fun addItem(rv: RemoteViews, pkg: String, size: WidgetSize, item: AgendaItem, pos: Int, total: Int, theme: Theme) {
        val isBirthday = item.kind == ItemKind.BIRTHDAY
        // Reminders: ice cyan, rounded-square marker, arc pattern. Birthdays: violet, circle, dot pattern, bigger name.
        val tone = theme.tone(item)
        val sp = TypedValue.COMPLEX_UNIT_SP
        val (titleSp, statusSp) = when (size) {
            WidgetSize.COMPACT -> if (isBirthday) 25f to 15f else 22f to 14f
            WidgetSize.WIDE -> if (isBirthday) 26f to 16f else 24f to 15f
            WidgetSize.LARGE -> if (isBirthday) 42f to 22f else 34f to 21f
        }

        rv.addView(R.id.bg_flipper, RemoteViews(pkg, theme.bgLayout(isBirthday)))

        RemoteViews(pkg, if (isBirthday) R.layout.wi_marker_ci else R.layout.wi_marker_sq).also {
            it.setInt(R.id.wi_marker, "setColorFilter", tone)
            rv.addView(R.id.marker_flipper, it)
        }
        RemoteViews(pkg, R.layout.wi_counter).also {
            it.setTextViewText(R.id.wi_text, if (total > 1) "${pos + 1}/$total" else "")
            it.setTextColor(R.id.wi_text, theme.muted)
            it.setTextViewTextSize(R.id.wi_text, sp, if (size == WidgetSize.LARGE) 13f else 12f)
            rv.addView(R.id.counter_flipper, it)
        }
        RemoteViews(pkg, R.layout.wi_title).also {
            it.setTextViewText(R.id.wi_text, item.title)
            it.setTextColor(R.id.wi_text, theme.text)
            it.setTextViewTextSize(R.id.wi_text, sp, titleSp)
            it.setInt(R.id.wi_text, "setMaxLines", if (size == WidgetSize.WIDE) 1 else 2)
            rv.addView(R.id.title_flipper, it)
        }
        RemoteViews(pkg, R.layout.wi_status).also {
            it.setTextViewText(R.id.wi_text, Phrases.status(item))
            it.setTextColor(R.id.wi_text, tone)
            it.setTextViewTextSize(R.id.wi_text, sp, statusSp)
            rv.addView(R.id.status_flipper, it)
        }
        if (size != WidgetSize.COMPACT) {
            val d = item.date.format(dateFmt)
            RemoteViews(pkg, R.layout.wi_date).also {
                it.setTextViewText(R.id.wi_text, if (isBirthday && item.turningAge != null) "$d  ·  turns ${item.turningAge}" else d)
                it.setTextViewTextSize(R.id.wi_text, sp, if (size == WidgetSize.LARGE) 15f else 13f)
                it.setTextColor(R.id.wi_text, theme.muted)
                rv.addView(R.id.date_flipper, it)
            }
        }
        if (size == WidgetSize.WIDE) {
            val d = item.daysUntil
            RemoteViews(pkg, R.layout.wi_number).also {
                it.setTextViewText(R.id.wi_text, kotlin.math.abs(d).toString())
                it.setTextColor(R.id.wi_text, tone)
                rv.addView(R.id.number_flipper, it)
            }
            RemoteViews(pkg, R.layout.wi_label).also {
                it.setTextViewText(
                    R.id.wi_text,
                    when {
                        d < 0 -> if (d == -1) "day late" else "days late"
                        d == 0 -> "today"
                        d == 1 -> "day"
                        else -> "days"
                    },
                )
                it.setTextViewTextSize(R.id.wi_text, sp, 13f)
                it.setTextColor(R.id.wi_text, theme.muted)
                rv.addView(R.id.label_flipper, it)
            }
        }
        if (size == WidgetSize.LARGE) {
            RemoteViews(pkg, R.layout.wi_notes).also {
                it.setTextViewText(R.id.wi_text, item.notes)
                it.setTextViewTextSize(R.id.wi_text, sp, 16f)
                it.setTextColor(R.id.wi_text, theme.muted)
                rv.addView(R.id.notes_flipper, it)
            }
        }
    }
}
