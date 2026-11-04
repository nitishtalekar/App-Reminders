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
        ItemKind.BIRTHDAY -> when (item.daysUntil) {
            0 -> "Birthday today"
            1 -> "Birthday tomorrow"
            else -> "Birthday in ${item.daysUntil} days"
        }
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
