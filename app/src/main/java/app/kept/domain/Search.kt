package app.kept.domain

import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/**
 * Free-text search over reminders and birthdays/anniversaries.
 *
 * The query is split on spaces and EVERY word must match, so "alex nov" finds Alex's November entry.
 * A word matches when it appears in a name/title, or (three letters or more) is the start of the
 * item's month, so "nov", "novem" and "november" all find November. One or two letters are
 * deliberately names only: "ma" should not mean March.
 */
object Search {
    private const val MIN_MONTH_PREFIX = 3

    fun matches(query: String, month: Int, vararg texts: String, locale: Locale = Locale.getDefault()): Boolean {
        val words = query.trim().lowercase(locale).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val monthName = Month.of(month).getDisplayName(TextStyle.FULL, locale).lowercase(locale)
        val haystack = texts.map { it.lowercase(locale) }
        return words.all { w ->
            haystack.any { it.contains(w) } || (w.length >= MIN_MONTH_PREFIX && monthName.startsWith(w))
        }
    }

    fun reminder(r: Reminder, query: String) = matches(query, r.date.monthValue, r.title)
    fun birthday(b: Birthday, query: String) =
        matches(query, b.month, b.name, if (b.anniversary) "anniversary" else "birthday")
}
