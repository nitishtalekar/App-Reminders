package app.kept.domain

import java.time.LocalDate

/** A one-off reminder with a target date. Active from [activeFrom] until cleared. */
data class Reminder(
    val id: Long = 0,
    val title: String,
    val notes: String = "",
    val date: LocalDate,
    val leadDays: Int,
    val clearedAtMillis: Long? = null,
)

/** A yearly recurring birthday or anniversary. [birthYear] (the start year for anniversaries) is optional. */
data class Birthday(
    val id: Long = 0,
    val name: String,
    val month: Int,
    val day: Int,
    val birthYear: Int? = null,
    val leadDays: Int,
    val notes: String = "",
    /** The tab is still "Birthdays"; an entry can also be an anniversary. */
    val anniversary: Boolean = false,
)

enum class ItemKind { REMINDER, BIRTHDAY }

/**
 * One entry of the "agenda": something that is relevant *today*.
 * The app overview, the widget and notifications all consume this one type,
 * so they can never disagree about what is active.
 */
data class AgendaItem(
    val kind: ItemKind,
    val sourceId: Long,
    val title: String,
    /** Date of the occurrence this item is about (target date, or the next/this birthday). */
    val date: LocalDate,
    /** Calendar days from today to [date]. Negative = overdue (reminders only). */
    val daysUntil: Int,
    val notes: String = "",
    /** Birthdays only: the age being reached on [date], when the birth year is known. */
    val turningAge: Int? = null,
    /** Birthdays tab only: this yearly entry is an anniversary. [turningAge] then means "years". */
    val anniversary: Boolean = false,
) {
    val isOverdue: Boolean get() = daysUntil < 0
    val isToday: Boolean get() = daysUntil == 0
    val key: String get() = "${kind.name}:$sourceId"
}
