package app.kept

import app.kept.domain.Birthday
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Portable export. Built with org.json so every string is escaped and the output is always valid JSON. */
object BirthdayJson {
    const val SCHEMA_VERSION = 1

    fun export(birthdays: List<Birthday>, exportedOn: LocalDate = LocalDate.now()): String {
        val list = JSONArray()
        birthdays.sortedWith(compareBy({ it.month }, { it.day }, { it.name.lowercase() })).forEach { b ->
            list.put(JSONObject().apply {
                put("name", b.name)
                // "--MM-DD": ISO 8601 year-less date. Always present.
                put("date", "--%02d-%02d".format(b.month, b.day))
                put("month", b.month)
                put("day", b.day)
                put("birthYear", b.birthYear ?: JSONObject.NULL)
                put("reminderLeadDays", b.leadDays)
                put("notes", if (b.notes.isBlank()) JSONObject.NULL else b.notes)
            })
        }
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedOn", exportedOn.toString())
            put("count", birthdays.size)
            put("birthdays", list)
        }.toString(2)
    }
}
