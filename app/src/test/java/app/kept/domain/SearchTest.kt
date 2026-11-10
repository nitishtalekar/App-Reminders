package app.kept.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class SearchTest {
    private val en = Locale.ENGLISH
    private fun m(q: String, month: Int, vararg t: String) = Search.matches(q, month, *t, locale = en)

    @Test fun blankQueryMatchesEverything() {
        assertTrue(m("", 3, "x"))
        assertTrue(m("   ", 3, "x"))
    }

    @Test fun matchesNameCaseInsensitivelyAnywhereInTheName() {
        assertTrue(m("ALEX", 1, "Alexandra"))
        assertTrue(m("andra", 1, "Alexandra"))
        assertFalse(m("sam", 1, "Alexandra"))
    }

    @Test fun matchesMonthByNameOrPrefix() {
        assertTrue(m("november", 11, "Alex"))
        assertTrue(m("nov", 11, "Alex"))
        assertTrue(m("sept", 9, "Alex"))
        assertFalse(m("nov", 10, "Alex"))
    }

    @Test fun monthPrefixNeedsThreeLetters() {
        assertFalse(m("ma", 3, "Alex")) // not March
        assertTrue(m("mar", 3, "Alex"))
    }

    @Test fun everyWordMustMatch() {
        assertTrue(m("alex nov", 11, "Alex"))
        assertFalse(m("sam nov", 11, "Alex"))
        assertFalse(m("alex dec", 11, "Alex"))
    }

    @Test fun reminderUsesDueDateMonth() {
        val r = Reminder(1, "Renew passport", "", LocalDate.of(2027, 3, 4), 7)
        assertTrue(Search.reminder(r, "march"))
        assertTrue(Search.reminder(r, "passport"))
        assertFalse(Search.reminder(r, "april"))
    }

    @Test fun anniversariesAreFoundByKindWord() {
        val a = Birthday(1, "Alex & Sam", 6, 2, 2015, 7, anniversary = true)
        val b = Birthday(2, "Jo", 6, 9, null, 7)
        assertTrue(Search.birthday(a, "anniversary"))
        assertFalse(Search.birthday(b, "anniversary"))
        assertTrue(Search.birthday(b, "birthday"))
    }

    @Test fun anniversaryItemsSayYearsNotTurns() {
        val d = LocalDate.of(2026, 11, 10)
        val a = Eligibility.birthdayItem(Birthday(1, "A & B", 11, 12, 2020, 3, anniversary = true), d)
        assertTrue(a.anniversary)
        assertEquals("6 years", Phrases.ageNote(a))
        assertEquals("Anniversary in 2 days", Phrases.status(a))
        val one = Eligibility.birthdayItem(Birthday(2, "C", 11, 12, 2025, 3, anniversary = true), d)
        assertEquals("1 year", Phrases.ageNote(one))
        val b = Eligibility.birthdayItem(Birthday(3, "D", 11, 12, 1990, 3), d)
        assertEquals("turns 36", Phrases.ageNote(b))
        assertEquals("Birthday in 2 days", Phrases.status(b))
    }
}
