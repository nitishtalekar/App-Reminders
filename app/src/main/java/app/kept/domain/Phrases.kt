package app.kept.domain

import kotlin.math.abs

/** Human wording for an item's timing. Shared by app, widget and notifications. */
object Phrases {

    fun status(item: AgendaItem): String = when (item.kind) {
        ItemKind.REMINDER -> when {
            item.daysUntil < -1 -> "Overdue by ${-item.daysUntil} days"
            item.daysUntil == -1 -> "Overdue by 1 day"
            item.daysUntil == 0 -> "Due today"
            item.daysUntil == 1 -> "Due tomorrow"
            else -> "Due in ${item.daysUntil} days"
        }
        ItemKind.BIRTHDAY -> {
            val w = noun(item.anniversary)
            when (item.daysUntil) {
                0 -> "$w today"
                1 -> "$w tomorrow"
                else -> "$w in ${item.daysUntil} days"
            }
        }
    }

    fun noun(anniversary: Boolean) = if (anniversary) "Anniversary" else "Birthday"

    /** "turns 34" for a birthday, "5 years" for an anniversary; null when no year is known. */
    fun ageNote(item: AgendaItem): String? = item.turningAge?.let { n ->
        if (item.anniversary) (if (n == 1) "1 year" else "$n years") else "turns $n"
    }

    /** Short form used where the context already says what it is: "Today", "In 3 days", "2 days late". */
    fun short(daysUntil: Int, birthday: Boolean = false): String = when {
        daysUntil < 0 -> "${abs(daysUntil)}d late"
        daysUntil == 0 -> "Today"
        daysUntil == 1 -> "Tomorrow"
        else -> "In $daysUntil days"
    }

    fun leadLabel(days: Int): String = when (days) {
        0 -> "On the day"
        1 -> "1 day before"
        else -> "$days days before"
    }
}
