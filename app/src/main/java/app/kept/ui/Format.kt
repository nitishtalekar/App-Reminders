package app.kept.ui

import app.kept.domain.AgendaItem
import app.kept.domain.ItemKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val short = DateTimeFormatter.ofPattern("EEE, MMM d")
private val shortWithYear = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")
private val long = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")

fun LocalDate.shortLabel(today: LocalDate): String = if (year == today.year) format(short) else format(shortWithYear)
fun LocalDate.longLabel(): String = format(long)
fun monthAbbr(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault())
fun monthFull(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())

fun subtitleFor(item: AgendaItem, today: LocalDate): String {
    val d = item.date.shortLabel(today)
    val note = if (item.kind == ItemKind.BIRTHDAY) app.kept.domain.Phrases.ageNote(item) else null
    return if (note != null) "$d  ·  $note" else d
}
