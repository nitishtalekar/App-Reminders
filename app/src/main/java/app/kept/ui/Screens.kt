package app.kept.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kept.domain.*
import java.time.format.TextStyle
import java.util.Locale

private val ListBottomPadding = 112.dp // clears the add button

// ───────────────────────────── Today ─────────────────────────────

@Composable
fun TodayScreen(
    state: UiState,
    onOpenReminder: (Long) -> Unit,
    onOpenBirthday: (Long) -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
) {
    val today = state.today
    val agenda = Eligibility.activeAgenda(state.reminders, state.birthdays, today)
    val activeKeys = agenda.map { it.key }.toSet()
    val coming = (Eligibility.upcomingReminders(state.reminders, today) +
        Eligibility.birthdaysByNext(state.birthdays, today).filter { it.key !in activeKeys && it.daysUntil <= 60 })
        .sortedWith(Eligibility.agendaOrder).take(6)

    fun open(i: AgendaItem) = if (i.kind == ItemKind.REMINDER) onOpenReminder(i.sourceId) else onOpenBirthday(i.sourceId)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding - 12.dp, top = 20.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = T.Display, color = C.Text)
                    Text(
                        "${today.dayOfMonth} ${monthFull(today.monthValue)} ${today.year}",
                        style = T.Body, color = C.Muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconAction(Glyph.Calendar, "Calendar", onCalendar)
                IconAction(Glyph.Sliders, "Settings", onSettings)
            }
        }

        if (!state.loaded) return@LazyColumn

        if (agenda.isEmpty()) {
            item { EmptyState("All clear.", "Nothing needs you today. New reminders appear here once their window opens.") }
        } else {
            item { NextUp(agenda.first(), today) { open(agenda.first()) } }
            if (agenda.size > 1) {
                item { SectionLabel("Also active · ${agenda.size - 1}") }
                items(agenda.drop(1), key = { it.key }) { i ->
                    AgendaRow(i, subtitleFor(i, today), active = true, onClick = { open(i) })
                }
            }
        }

        if (coming.isNotEmpty()) {
            item { SectionLabel("Coming up") }
            items(coming, key = { "c" + it.key }) { i ->
                AgendaRow(i, subtitleFor(i, today), active = false, onClick = { open(i) })
            }
        }
    }
}

/** The single most pressing item, set large - the app's version of the widget's one-at-a-time display. */
@Composable
private fun NextUp(item: AgendaItem, today: java.time.LocalDate, onClick: () -> Unit) {
    val tone = toneOf(item)
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Marker(item.kind, tone, 12.dp)
            Spacer(Modifier.width(10.dp))
            Text(Phrases.status(item), style = T.Heading, color = tone)
        }
        Text(item.title, style = T.Title.copy(fontSize = 36.sp, lineHeight = 40.sp), color = C.Text, modifier = Modifier.padding(top = 10.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(subtitleFor(item, today), style = T.Body, color = C.Muted, modifier = Modifier.padding(top = 6.dp))
        if (item.notes.isNotBlank()) {
            Text(item.notes, style = T.Body, color = C.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ───────────────────────────── Reminders ─────────────────────────────

@Composable
fun RemindersScreen(
    state: UiState,
    onOpen: (Long) -> Unit,
    onClear: (Reminder) -> Unit,
    onRestore: (Reminder) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val today = state.today
    val active = state.reminders.filter { Eligibility.isReminderActive(it, today) }
        .map { Eligibility.reminderItem(it, today) }.sortedWith(Eligibility.agendaOrder)
    val later = Eligibility.upcomingReminders(state.reminders, today)
    val nowMillis = System.currentTimeMillis()
    // Expired ones are hidden immediately; the database row is deleted on the next sync.
    val cleared = state.reminders.filter { it.clearedAtMillis != null && !Eligibility.isClearedExpired(it, nowMillis) }
        .sortedByDescending { it.clearedAtMillis }
    val byId = state.reminders.associateBy { it.id }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Reminders")
        UnderlineTabs(listOf("Active · ${active.size + later.size}", "Cleared · ${cleared.size}"), tab) { tab = it }
        if (!state.loaded) return@Column

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
            if (tab == 0) {
                if (active.isEmpty() && later.isEmpty()) {
                    item { EmptyState("Nothing pending.", "Add a reminder with a date and how many days ahead you want to hear about it.") }
                }
                items(active, key = { it.key }) { i ->
                    AgendaRow(i, subtitleFor(i, today), active = true, onClick = { onOpen(i.sourceId) },
                        leading = { CheckBox(i.title) { byId[i.sourceId]?.let(onClear) } })
                }
                if (later.isNotEmpty()) {
                    item { SectionLabel("Not yet active") }
                    items(later, key = { it.key }) { i ->
                        val r = byId[i.sourceId]
                        val sub = "${i.date.shortLabel(today)}  ·  starts ${r?.let { Eligibility.activeFrom(it).shortLabel(today) } ?: ""}"
                        AgendaRow(i, sub, active = false, onClick = { onOpen(i.sourceId) },
                            leading = { CheckBox(i.title) { r?.let(onClear) } })
                    }
                }
            } else {
                if (cleared.isEmpty()) item { EmptyState("Nothing cleared yet.", "Reminders you mark done are kept here, and can be restored.") }
                items(cleared, key = { "x" + it.id }) { r ->
                    val i = Eligibility.reminderItem(r, today)
                    val left = Eligibility.clearedDaysLeft(r, nowMillis)
                    val leftText = when (left) { 0 -> "removed within a day"; 1 -> "removed in 1 day"; else -> "removed in $left days" }
                    AgendaRow(i, "Was due ${r.date.shortLabel(today)}  ·  $leftText", active = false,
                        onClick = { onOpen(r.id) }, showDays = false,
                        leading = { IconAction(Glyph.Restore, "Restore ${r.title}", { onRestore(r) }, C.Accent) })
                }
            }
        }
    }
}

@Composable
private fun CheckBox(title: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clickable(role = Role.Button, onClickLabel = "Mark $title as done", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp).border(BorderStroke(2.dp, C.Muted), androidx.compose.foundation.shape.CircleShape))
    }
}

// ───────────────────────────── Birthdays ─────────────────────────────

@Composable
fun BirthdaysScreen(
    state: UiState,
    onOpen: (Long) -> Unit,
    onCopyJson: () -> Unit,
) {
    val today = state.today
    val list = Eligibility.birthdaysByNext(state.birthdays, today)
    val byId = state.birthdays.associateBy { it.id }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Birthdays") {
            if (list.isNotEmpty()) TextActionWithIcon("Copy JSON", onCopyJson)
        }
        if (!state.loaded) return@Column
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
            if (list.isEmpty()) {
                item { EmptyState("No birthdays yet.", "Add the people you want to remember. They come back every year on their own.") }
            }
            items(list, key = { it.key }) { i ->
                val b = byId[i.sourceId]
                val inWindow = b != null && i.daysUntil <= b.leadDays
                val sub = buildString {
                    append("${monthAbbr(i.date.monthValue)} ${i.date.dayOfMonth}")
                    if (b != null && b.month == 2 && b.day == 29 && i.date.dayOfMonth == 28) append(" (Feb 29)")
                    i.turningAge?.let { append("  ·  turns $it") }
                }
                AgendaRow(i, sub, active = inWindow, onClick = { onOpen(i.sourceId) })
            }
        }
    }
}

@Composable
private fun TextActionWithIcon(label: String, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Copy all birthdays as JSON", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Copy, C.Violet, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = T.Label.copy(fontSize = 14.sp), color = C.Violet)
    }
}
