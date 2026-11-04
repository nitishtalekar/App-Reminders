package app.kept.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.kept.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
private fun EditorFrame(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Glyph.Back, "Back", onBack)
            Text(title, style = T.Heading, color = C.Text, modifier = Modifier.weight(1f).padding(start = 4.dp))
            trailing()
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ScreenPadding)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 12.dp)) { footer() }
    }
}

@Composable
private fun FieldLabel(text: String) =
    Text(text.uppercase(), style = T.Caps, color = C.Muted, modifier = Modifier.padding(top = 28.dp, bottom = 8.dp))

@Composable
private fun LeadPicker(value: Int, onChange: (Int) -> Unit, presets: List<Int>) {
    ChipRow {
        presets.forEach { d -> Chip(if (d == 0) "On the day" else "$d d", value == d, { onChange(d) }) }
    }
    Spacer(Modifier.height(8.dp))
    Stepper(value, onChange, 0..365, label = { Phrases.leadLabel(it) })
}

/** Equal-width cells in rows of [cols]; the last row is padded with empty cells so columns line up. */
@Composable
private fun Grid(cols: Int, count: Int, cell: @Composable (Int) -> Unit) {
    Column {
        for (r in 0 until (count + cols - 1) / cols) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (c in 0 until cols) {
                    val i = r * cols + c
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (i < count) cell(i) }
                }
            }
        }
    }
}

private fun utcMillis(d: LocalDate) = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun fromUtcMillis(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()

// ───────────────────────────── Reminder ─────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditor(
    initial: Reminder?,
    today: LocalDate,
    defaultLead: Int,
    presetDate: LocalDate? = null,
    onSave: (Reminder) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleCleared: (Reminder) -> Unit,
    onBack: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var epochDay by rememberSaveable { mutableLongStateOf((initial?.date ?: presetDate ?: today).toEpochDay()) }
    var lead by rememberSaveable { mutableIntStateOf(initial?.leadDays ?: defaultLead) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(epochDay)

    EditorFrame(
        title = if (initial == null) "New reminder" else "Edit reminder",
        onBack = onBack,
        trailing = {
            if (initial != null) IconAction(Glyph.Trash, "Delete reminder", { confirmDelete = true }, C.Danger)
        },
        footer = {
            PrimaryButton(if (initial == null) "Add reminder" else "Save changes", enabled = title.isNotBlank(), onClick = {
                onSave(
                    Reminder(
                        id = initial?.id ?: 0, title = title.trim(), notes = notes.trim(), date = date,
                        leadDays = lead, clearedAtMillis = initial?.clearedAtMillis,
                    )
                )
            })
            if (initial != null) {
                TextAction(
                    if (initial.clearedAtMillis == null) "Mark as done" else "Restore to active",
                    color = C.Accent, onClick = { onToggleCleared(initial) },
                )
            }
        },
    ) {
        Spacer(Modifier.height(16.dp))
        KeptField(title, { title = it }, "What needs remembering?", style = T.Title,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

        FieldLabel("Date")
        ChipRow {
            Chip("Today", date == today, { epochDay = today.toEpochDay() })
            Chip("Tomorrow", date == today.plusDays(1), { epochDay = today.plusDays(1).toEpochDay() })
            Chip("In a week", date == today.plusWeeks(1), { epochDay = today.plusWeeks(1).toEpochDay() })
            Chip("In a month", date == today.plusMonths(1), { epochDay = today.plusMonths(1).toEpochDay() })
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .clip(RoundedCornerShape(20.dp)).background(C.Surface)
                .border(BorderStroke(1.dp, C.Line), RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClickLabel = "Pick a date") { picking = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(date.longLabel(), style = T.Heading, color = C.Text, modifier = Modifier.weight(1f))
            Text("Change", style = T.Label, color = C.Accent)
        }

        FieldLabel("Start reminding me")
        LeadPicker(lead, { lead = it }, listOf(0, 1, 3, 7, 14, 30))
        Spacer(Modifier.height(12.dp))
        val start = Eligibility.activeFrom(Reminder(title = "", date = date, leadDays = lead))
        val startIn = Eligibility.daysBetween(today, start)
        Text(
            when {
                lead == 0 -> "Shows up on ${date.shortLabel(today)}, then stays until you clear it."
                startIn <= 0 -> "Already showing since ${start.shortLabel(today)}. Stays until you clear it."
                startIn == 1 -> "Starts showing tomorrow, ${start.shortLabel(today)}. Stays until you clear it."
                else -> "Starts showing ${start.shortLabel(today)}, in $startIn days. Stays until you clear it."
            },
            style = T.Body, color = C.Muted,
        )

        FieldLabel("Note (optional)")
        KeptField(notes, { notes = it }, "Add details", singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
    }

    if (picking) {
        val ps = rememberDatePickerState(initialSelectedDateMillis = utcMillis(date))
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton({ ps.selectedDateMillis?.let { epochDay = fromUtcMillis(it).toEpochDay() }; picking = false }) { Text("OK", color = C.Accent) } },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel", color = C.Muted) } },
        ) { DatePicker(ps) }
    }
    if (confirmDelete && initial != null) ConfirmDelete("Delete “${initial.title}”?", { confirmDelete = false }) { onDelete(initial.id) }
}

@Composable
private fun ConfirmDelete(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Raised,
        title = { Text(message, style = T.Heading, color = C.Text) },
        text = { Text("This can’t be undone.", style = T.Body, color = C.Muted) },
        confirmButton = { TextButton(onConfirm) { Text("Delete", color = C.Danger) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = C.Muted) } },
    )
}

// ───────────────────────────── Birthday ─────────────────────────────

@Composable
fun BirthdayEditor(
    initial: Birthday?,
    today: LocalDate,
    defaultLead: Int,
    onSave: (Birthday) -> Unit,
    onDelete: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var notes by rememberSaveable { mutableStateOf(initial?.notes ?: "") }
    var month by rememberSaveable { mutableIntStateOf(initial?.month ?: today.monthValue) }
    var day by rememberSaveable { mutableIntStateOf(initial?.day ?: today.dayOfMonth) }
    var yearText by rememberSaveable { mutableStateOf(initial?.birthYear?.toString() ?: "") }
    var lead by rememberSaveable { mutableIntStateOf(initial?.leadDays ?: defaultLead) }
    var confirmDelete by remember { mutableStateOf(false) }

    val maxDay = java.time.Month.of(month).maxLength() // Feb = 29
    if (day > maxDay) day = maxDay
    val year: Int? = yearText.toIntOrNull()
    val yearInvalid = yearText.isNotEmpty() && (year == null || year !in 1900..today.year)
    val valid = name.isNotBlank() && !yearInvalid

    EditorFrame(
        title = if (initial == null) "New birthday" else "Edit birthday",
        onBack = onBack,
        trailing = { if (initial != null) IconAction(Glyph.Trash, "Delete birthday", { confirmDelete = true }, C.Danger) },
        footer = {
            PrimaryButton(if (initial == null) "Add birthday" else "Save changes", enabled = valid, onClick = {
                onSave(
                    Birthday(
                        id = initial?.id ?: 0, name = name.trim(), month = month, day = day,
                        birthYear = year.takeIf { yearText.isNotEmpty() }, leadDays = lead, notes = notes.trim(),
                    )
                )
            })
        },
    ) {
        Spacer(Modifier.height(16.dp))
        KeptField(name, { name = it }, "Whose birthday?", style = T.Title,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))

        FieldLabel("Month")
        Grid(cols = 4, count = 12) { i -> Chip(monthAbbr(i + 1), month == i + 1, { month = i + 1 }, Modifier.fillMaxWidth()) }

        FieldLabel("Day")
        Grid(cols = 7, count = maxDay) { i -> Chip("${i + 1}", day == i + 1, { day = i + 1 }, Modifier.fillMaxWidth()) }
        if (month == 2 && day == 29) {
            Spacer(Modifier.height(8.dp))
            Text("In years without a Feb 29 this birthday is observed on Feb 28.", style = T.Label, color = C.Muted)
        }

        FieldLabel("Birth year (optional)")
        KeptField(yearText, { yearText = it.filter(Char::isDigit).take(4) }, "e.g. 1992",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (yearInvalid) {
            Spacer(Modifier.height(6.dp))
            Text("Enter a year between 1900 and ${today.year}.", style = T.Label, color = C.Danger)
        }

        FieldLabel("Start reminding me")
        LeadPicker(lead, { lead = it }, listOf(0, 1, 3, 5, 7, 14))

        Spacer(Modifier.height(12.dp))
        val next = Eligibility.nextOccurrence(month, day, today)
        val n = Eligibility.daysBetween(today, next)
        val windowStart = next.minusDays(lead.toLong())
        Text(
            buildString {
                append("Next one is ${if (n == 0) "today" else if (n == 1) "tomorrow" else "in $n days"}, ${next.shortLabel(today)}")
                if (year != null && !yearInvalid) append(", turning ${next.year - year}")
                append(". ")
                append(if (lead == 0) "You'll be reminded on the day, every year." else "You'll be reminded from ${windowStart.shortLabel(today)}, and again every year.")
            },
            style = T.Body, color = C.Muted,
        )

        FieldLabel("Note (optional)")
        KeptField(notes, { notes = it }, "Gift ideas, how you know them…", singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
    }

    if (confirmDelete && initial != null) ConfirmDelete("Delete ${initial.name}?", { confirmDelete = false }) { onDelete(initial.id) }
}
