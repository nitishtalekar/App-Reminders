package app.kept.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EligibilityTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun reminder(date: LocalDate, lead: Int, cleared: Boolean = false, id: Long = 1) =
        Reminder(id, "R$id", "", date, lead, if (cleared) 1L else null)
    private fun bday(m: Int, day: Int, lead: Int, year: Int? = null, id: Long = 1) =
        Birthday(id, "B$id", m, day, year, lead)

    // ── reminders ──

    @Test fun reminderBecomesActiveExactlyOnLeadDate() {
        val r = reminder(d(2026, 12, 20), 7)
        assertEquals(d(2026, 12, 13), Eligibility.activeFrom(r))
        assertFalse(Eligibility.isReminderActive(r, d(2026, 12, 12)))
        assertTrue(Eligibility.isReminderActive(r, d(2026, 12, 13)))
    }

    @Test fun zeroLeadIsActiveOnlyFromTheDay() {
        val r = reminder(d(2026, 12, 20), 0)
        assertFalse(Eligibility.isReminderActive(r, d(2026, 12, 19)))
        assertTrue(Eligibility.isReminderActive(r, d(2026, 12, 20)))
        assertEquals(0, Eligibility.reminderItem(r, d(2026, 12, 20)).daysUntil)
    }

    @Test fun reminderStaysActiveAndOverdueUntilCleared() {
        val r = reminder(d(2026, 12, 20), 7)
        val later = d(2027, 3, 1)
        assertTrue(Eligibility.isReminderActive(r, later))
        val item = Eligibility.reminderItem(r, later)
        assertTrue(item.isOverdue)
        assertEquals("Overdue by 71 days", Phrases.status(item))
    }

    @Test fun clearedReminderIsNeverActive() {
        assertFalse(Eligibility.isReminderActive(reminder(d(2026, 12, 20), 7, cleared = true), d(2026, 12, 18)))
    }

    @Test fun leadCrossingYearAndMonthBoundaries() {
        assertEquals(d(2025, 12, 31), Eligibility.activeFrom(reminder(d(2026, 1, 3), 3)))
        assertEquals(d(2028, 2, 29), Eligibility.activeFrom(reminder(d(2028, 3, 1), 1)))
    }

    @Test fun statusWording() {
        val t = d(2026, 5, 10)
        fun s(days: Long) = Phrases.status(Eligibility.reminderItem(reminder(t.plusDays(days), 30), t))
        assertEquals("Due today", s(0))
        assertEquals("Due tomorrow", s(1))
        assertEquals("Due in 3 days", s(3))
        assertEquals("Overdue by 1 day", s(-1))
        assertEquals("Overdue by 2 days", s(-2))
    }

    // ── birthdays ──

    @Test fun birthdayWindowOpensLeadDaysBeforeAndIncludesTheDay() {
        val b = bday(11, 15, 5)
        assertFalse(Eligibility.isBirthdayInWindow(b, d(2026, 11, 9)))
        assertTrue(Eligibility.isBirthdayInWindow(b, d(2026, 11, 10)))
        assertTrue(Eligibility.isBirthdayInWindow(b, d(2026, 11, 15)))
    }

    @Test fun birthdayLeavesWindowTheDayAfterAndReturnsNextYear() {
        val b = bday(11, 15, 5)
        assertFalse(Eligibility.isBirthdayInWindow(b, d(2026, 11, 16)))
        assertFalse(Eligibility.isBirthdayInWindow(b, d(2027, 6, 1)))
        assertTrue(Eligibility.isBirthdayInWindow(b, d(2027, 11, 12)))
        assertEquals(d(2027, 11, 15), Eligibility.nextOccurrence(11, 15, d(2026, 11, 16)))
    }

    @Test fun windowCanSpanNewYear() {
        val b = bday(1, 2, 5)
        assertTrue(Eligibility.isBirthdayInWindow(b, d(2026, 12, 28)))
        assertEquals(5, Eligibility.birthdayItem(b, d(2026, 12, 28)).daysUntil)
    }

    @Test fun leapDayObservedOnFeb28InCommonYears() {
        assertEquals(d(2027, 2, 28), Eligibility.nextOccurrence(2, 29, d(2026, 6, 1)))
        assertEquals(d(2028, 2, 29), Eligibility.nextOccurrence(2, 29, d(2027, 3, 1)))
        assertEquals(d(2028, 2, 29), Eligibility.nextOccurrence(2, 29, d(2028, 2, 29)))
        assertEquals(d(2028, 2, 29), Eligibility.nextOccurrence(2, 29, d(2028, 1, 1)))
    }

    @Test fun leapDayWindowInCommonYear() {
        val b = bday(2, 29, 2)
        assertTrue(Eligibility.isBirthdayInWindow(b, d(2027, 2, 26)))
        assertEquals(0, Eligibility.birthdayItem(b, d(2027, 2, 28)).daysUntil)
        assertFalse(Eligibility.isBirthdayInWindow(b, d(2027, 3, 1)))
    }

    @Test fun ageIsComputedForNextOccurrenceOnlyWhenYearKnown() {
        assertEquals(34, Eligibility.birthdayItem(bday(11, 15, 3, 1992), d(2026, 11, 4)).turningAge)
        assertEquals(35, Eligibility.birthdayItem(bday(11, 15, 3, 1992), d(2026, 11, 16)).turningAge)
        assertNull(Eligibility.birthdayItem(bday(11, 15, 3), d(2026, 11, 4)).turningAge)
    }

    @Test fun birthdayPhrases() {
        val t = d(2026, 11, 10)
        fun s(day: Int) = Phrases.status(Eligibility.birthdayItem(bday(11, day, 9), t))
        assertEquals("Birthday today", s(10))
        assertEquals("Birthday tomorrow", s(11))
        assertEquals("Birthday in 5 days", s(15))
    }

    // ── agenda ──

    @Test fun agendaMixesAndOrdersOverdueFirstThenNearest() {
        val today = d(2026, 11, 4)
        val agenda = Eligibility.activeAgenda(
            reminders = listOf(
                reminder(d(2026, 11, 7), 7, id = 1),            // in 3 days
                reminder(d(2026, 11, 4), 0, id = 2),            // today
                reminder(d(2026, 11, 2), 3, id = 3),            // overdue 2
                reminder(d(2026, 12, 25), 7, id = 4),           // not yet
                reminder(d(2026, 11, 1), 3, cleared = true, id = 5), // cleared
            ),
            birthdays = listOf(bday(11, 5, 2, id = 6), bday(11, 20, 2, id = 7)),
            today = today,
        )
        assertEquals(listOf("REMINDER:3", "REMINDER:2", "BIRTHDAY:6", "REMINDER:1"), agenda.map { it.key })
    }

    @Test fun clearedReminderExpiresAfter72Hours() {
        val h = 3_600_000L
        val r = Reminder(1, "R", "", d(2026, 5, 1), 0, clearedAtMillis = 1_000_000L)
        assertFalse(Eligibility.isClearedExpired(r, 1_000_000L + 71 * h))
        assertTrue(Eligibility.isClearedExpired(r, 1_000_000L + 72 * h))
        assertFalse(Eligibility.isClearedExpired(r.copy(clearedAtMillis = null), Long.MAX_VALUE))
        assertEquals(3, Eligibility.clearedDaysLeft(r, 1_000_000L))
        assertEquals(1, Eligibility.clearedDaysLeft(r, 1_000_000L + 50 * h))
        assertEquals(0, Eligibility.clearedDaysLeft(r, 1_000_000L + 73 * h))
    }

    @Test fun calendarPlacesRemindersAndBirthdaysOnTheirDays() {
        val today = d(2027, 2, 10)
        val ev = Eligibility.calendarEvents(
            reminders = listOf(reminder(d(2027, 2, 14), 3, id = 1), reminder(d(2027, 2, 20), 3, cleared = true, id = 2), reminder(d(2027, 3, 1), 0, id = 3)),
            birthdays = listOf(bday(2, 29, 3, 2000, id = 4), bday(7, 4, 0, id = 5)),
            today = today, month = java.time.YearMonth.of(2027, 2),
        )
        assertEquals(listOf("REMINDER:1"), ev[d(2027, 2, 14)]!!.map { it.key })
        assertNull(ev[d(2027, 2, 20)])                       // cleared: not shown
        assertEquals(listOf("BIRTHDAY:4"), ev[d(2027, 2, 28)]!!.map { it.key }) // Feb 29 observed on the 28th
        assertEquals(27, ev[d(2027, 2, 28)]!![0].turningAge)
        assertEquals(2, ev.size)                              // March reminder and July birthday excluded
    }

    @Test fun emptyInputsGiveEmptyAgenda() {
        assertTrue(Eligibility.activeAgenda(emptyList(), emptyList(), d(2026, 1, 1)).isEmpty())
    }

    @Test fun calendarDaysAreNotAffectedByDstOrZone() {
        // Spring-forward week in the US: LocalDate arithmetic stays exact.
        assertEquals(1, Eligibility.daysBetween(d(2026, 3, 7), d(2026, 3, 8)))
        assertEquals(2, Eligibility.daysBetween(d(2026, 3, 8), d(2026, 3, 10)))
    }
}
