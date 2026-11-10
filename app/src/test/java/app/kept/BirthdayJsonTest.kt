package app.kept

import app.kept.domain.Birthday
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class BirthdayJsonTest {
    @Test fun producesValidJsonWithSchemaAndAllFields() {
        val json = BirthdayJson.export(
            listOf(
                Birthday(1, "Alex", 11, 15, 1990, 5, "Likes \"cake\"\nand tea"),
                Birthday(2, "Sam", 2, 29, null, 0, ""),
            ),
            LocalDate.of(2026, 11, 4),
        )
        val o = JSONObject(json) // throws if invalid
        assertEquals(2, o.getInt("schemaVersion"))
        assertEquals(2, o.getInt("count"))
        val arr = o.getJSONArray("birthdays")
        assertEquals(2, arr.length())
        val sam = arr.getJSONObject(0) // sorted by month/day
        assertEquals("Sam", sam.getString("name"))
        assertEquals("--02-29", sam.getString("date"))
        assertTrue(sam.isNull("birthYear"))
        assertTrue(sam.isNull("notes"))
        val alex = arr.getJSONObject(1)
        assertEquals(1990, alex.getInt("birthYear"))
        assertEquals(5, alex.getInt("reminderLeadDays"))
        assertEquals("Likes \"cake\"\nand tea", alex.getString("notes"))
    }

    @Test fun emptyListIsStillValid() {
        val o = JSONObject(BirthdayJson.export(emptyList()))
        assertEquals(0, o.getJSONArray("birthdays").length())
    }
}
