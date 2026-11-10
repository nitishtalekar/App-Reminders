package app.kept.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The single source of truth for "what is relevant today?".
 *
 * All arithmetic is on [LocalDate] (calendar days) – never on millisecond differences –
 * so DST changes and time-zone shifts cannot produce off-by-one days.
 *
 * Rules
 *  - Reminder: active from (date − leadDays) until it is cleared. It never expires by itself;
 *    after its date it is simply "overdue".
 *  - Birthday: recurs yearly. Each year it is relevant from (birthday − leadDays) through the
 *    birthday itself (inclusive). The day after, the next occurrence is a year away, so it drops
 *    out of the window automatically and returns next cycle.
 *  - Feb 29 rule: in non-leap years a Feb 29 birthday is observed on **Feb 28**.
 */
object Eligibility {

    fun activeFrom(reminder: Reminder): LocalDate = reminder.date.minusDays(reminder.leadDays.toLong())

    /** Cleared reminders stay in the Cleared list for 72 hours, then are deleted for good. */
    const val CLEARED_RETENTION_MILLIS = 3L * 24 * 60 * 60 * 1000

    fun isClearedExpired(reminder: Reminder, nowMillis: Long): Boolean =
        reminder.clearedAtMillis?.let { nowMillis - it >= CLEARED_RETENTION_MILLIS } ?: false

    /** Whole days left before a cleared reminder is removed (3, 2, 1, or 0 = under a day). */
    fun clearedDaysLeft(reminder: Reminder, nowMillis: Long): Int {
        val left = (reminder.clearedAtMillis ?: return 0) + CLEARED_RETENTION_MILLIS - nowMillis
        return if (left <= 0) 0 else ((left + 86_399_999L) / 86_400_000L).toInt().coerceAtMost(3)
    }

    fun daysBetween(today: LocalDate, target: LocalDate): Int =
        ChronoUnit.DAYS.between(today, target).toInt()

    fun isReminderActive(reminder: Reminder, today: LocalDate): Boolean =
        reminder.clearedAtMillis == null && !today.isBefore(activeFrom(reminder))

    /** Date on which a (month, day) birthday is observed in [year]. */
    fun observedDate(month: Int, day: Int, year: Int): LocalDate {
        val d = if (month == 2 && day == 29 && !java.time.Year.isLeap(year.toLong())) 28 else day
        return LocalDate.of(year, month, d)
    }

    /** The first observed birthday on or after [today]. */
    fun nextOccurrence(month: Int, day: Int, today: LocalDate): LocalDate {
        val thisYear = observedDate(month, day, today.year)
        return if (thisYear.isBefore(today)) observedDate(month, day, today.year + 1) else thisYear
    }

    fun isBirthdayInWindow(birthday: Birthday, today: LocalDate): Boolean =
        daysBetween(today, nextOccurrence(birthday.month, birthday.day, today)) <= birthday.leadDays

    fun reminderItem(reminder: Reminder, today: LocalDate) = AgendaItem(
        kind = ItemKind.REMINDER,
        sourceId = reminder.id,
        title = reminder.title,
        date = reminder.date,
        daysUntil = daysBetween(today, reminder.date),
        notes = reminder.notes,
    )

    fun birthdayItem(birthday: Birthday, today: LocalDate): AgendaItem {
        val next = nextOccurrence(birthday.month, birthday.day, today)
        return AgendaItem(
            kind = ItemKind.BIRTHDAY,
            sourceId = birthday.id,
            title = birthday.name,
            date = next,
            daysUntil = daysBetween(today, next),
            notes = birthday.notes,
            turningAge = birthday.birthYear?.let { next.year - it },
            anniversary = birthday.anniversary,
        )
    }

    /** Priority order: overdue (most overdue first), then nearest first; reminders before birthdays on ties. */
    val agendaOrder: Comparator<AgendaItem> =
        compareBy<AgendaItem> { it.daysUntil }.thenBy { it.kind.ordinal }.thenBy { it.title.lowercase() }

    /** Everything that is relevant today – the widget's and notifications' content. */
    fun activeAgenda(reminders: List<Reminder>, birthdays: List<Birthday>, today: LocalDate): List<AgendaItem> =
        (reminders.filter { isReminderActive(it, today) }.map { reminderItem(it, today) } +
            birthdays.filter { isBirthdayInWindow(it, today) }.map { birthdayItem(it, today) })
            .sortedWith(agendaOrder)

    /**
     * Everything that lands on a day of [month]: uncleared reminders on their due date, and birthdays on
     * the day they are observed that year (Feb 29 -> Feb 28 in non-leap years). Used by the calendar view.
     */
    fun calendarEvents(
        reminders: List<Reminder>, birthdays: List<Birthday>, today: LocalDate, month: java.time.YearMonth,
    ): Map<LocalDate, List<AgendaItem>> {
        val out = HashMap<LocalDate, MutableList<AgendaItem>>()
        reminders.filter { it.clearedAtMillis == null && java.time.YearMonth.from(it.date) == month }
            .forEach { out.getOrPut(it.date) { mutableListOf() } += reminderItem(it, today) }
        birthdays.forEach { b ->
            val d = observedDate(b.month, b.day, month.year)
            if (java.time.YearMonth.from(d) == month) {
                out.getOrPut(d) { mutableListOf() } += AgendaItem(
                    kind = ItemKind.BIRTHDAY, sourceId = b.id, title = b.name, date = d,
                    daysUntil = daysBetween(today, d), notes = b.notes, turningAge = b.birthYear?.let { d.year - it }, anniversary = b.anniversary,
                )
            }
        }
        return out.mapValues { (_, v) -> v.sortedWith(agendaOrder) }
    }

    /** Reminders that are not yet in their window (not cleared). */
    fun upcomingReminders(reminders: List<Reminder>, today: LocalDate): List<AgendaItem> =
        reminders.filter { it.clearedAtMillis == null && !isReminderActive(it, today) }
            .map { reminderItem(it, today) }.sortedWith(agendaOrder)

    /** All birthdays by next occurrence. */
    fun birthdaysByNext(birthdays: List<Birthday>, today: LocalDate): List<AgendaItem> =
        birthdays.map { birthdayItem(it, today) }.sortedWith(agendaOrder)
}
