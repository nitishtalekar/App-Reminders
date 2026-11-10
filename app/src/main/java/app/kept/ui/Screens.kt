package app.kept.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
    onClear: (Reminder) -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
) {
    val today = state.today
    val agenda = Eligibility.activeAgenda(state.reminders, state.birthdays, today)
    val activeKeys = agenda.map { it.key }.toSet()
    val coming = (Eligibility.upcomingReminders(state.reminders, today) +
        Eligibility.birthdaysByNext(state.birthdays, today).filter { it.key !in activeKeys })
        .filter { it.daysUntil in 0..7 }
        .sortedWith(Eligibility.agendaOrder)
    val remindersById = state.reminders.associateBy { it.id }

    // Reminders swipe away to be marked done; birthdays are display-only and drop off once the day is over.
    val done: (AgendaItem) -> (() -> Unit)? = { i ->
        if (i.kind == ItemKind.REMINDER) ({ remindersById[i.sourceId]?.let(onClear); Unit }) else null
    }

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
            item(key = "n" + agenda.first().key) {
                SwipeToDone(done(agenda.first()), agenda.first().title) { NextUp(agenda.first(), today) }
            }
            if (agenda.size > 1) {
                item { SectionLabel("Also active · ${agenda.size - 1}") }
                items(agenda.drop(1), key = { it.key }) { i ->
                    SwipeToDone(done(i), i.title) { AgendaRow(i, subtitleFor(i, today), active = true, onClick = null) }
                }
            }
        }

        if (coming.isNotEmpty()) {
            item { SectionLabel("Coming up") }
            items(coming, key = { "c" + it.key }) { i ->
                AgendaRow(i, subtitleFor(i, today), active = false, onClick = null)
            }
        }
    }
}

/** Swipe either way to mark done. With a null [onDone] (birthdays) the swipe is shown greyed out and snaps back. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDone(onDone: (() -> Unit)?, title: String, content: @Composable () -> Unit) {
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (onDone == null || v == androidx.compose.material3.SwipeToDismissBoxValue.Settled) false else { onDone(); true }
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = state,
        modifier = if (onDone != null) Modifier.semantics { contentDescription = "Swipe to mark $title as done" } else Modifier,
        backgroundContent = {
            Box(Modifier.fillMaxSize().background(if (onDone != null) C.Accent else C.Line).padding(horizontal = ScreenPadding), contentAlignment = Alignment.CenterStart) {
                Text(if (onDone != null) "Done" else "Clears itself after the day", style = T.Heading, color = if (onDone != null) C.Bg else C.Muted)
            }
        },
        content = { Box(Modifier.background(C.Bg)) { content() } },
    )
}

/** The single most pressing item, set large - the app's version of the widget's one-at-a-time display. */
@Composable
private fun NextUp(item: AgendaItem, today: java.time.LocalDate) {
    val tone = toneOf(item)
    Column(
        Modifier.fillMaxWidth().padding(top = 28.dp)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
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
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val today = state.today
    val all = state.reminders.filter { Search.reminder(it, query) }
    val active = all.filter { Eligibility.isReminderActive(it, today) }
        .map { Eligibility.reminderItem(it, today) }.sortedWith(Eligibility.agendaOrder)
    val later = Eligibility.upcomingReminders(all, today)
    val nowMillis = System.currentTimeMillis()
    // Expired ones are hidden immediately; the database row is deleted on the next sync.
    val cleared = all.filter { it.clearedAtMillis != null && !Eligibility.isClearedExpired(it, nowMillis) }
        .sortedByDescending { it.clearedAtMillis }
    val byId = state.reminders.associateBy { it.id }
    val filtering = query.isNotBlank()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Reminders") {
            IconAction(Glyph.Search, if (searching) "Close search" else "Search reminders", {
                searching = !searching; if (!searching) query = ""
            }, if (searching) C.Accent else C.Text)
        }
        if (searching) SearchPanel(query, { query = it }, "Search by name or month", C.Accent)
        UnderlineTabs(listOf("Active · ${active.size + later.size}", "Cleared · ${cleared.size}"), tab) { tab = it }
        if (!state.loaded) return@Column

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
            if (tab == 0) {
                if (active.isEmpty() && later.isEmpty()) {
                    item {
                        if (filtering) EmptyState("No matches.", "Nothing active matches “${query.trim()}”. Try a name, or a month like “nov”.")
                        else EmptyState("Nothing pending.", "Add a reminder with a date and how many days ahead you want to hear about it.")
                    }
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
                if (cleared.isEmpty()) item {
                    if (filtering) EmptyState("No matches.", "Nothing cleared matches “${query.trim()}”.")
                    else EmptyState("Nothing cleared yet.", "Reminders you mark done are kept here, and can be restored.")
                }
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
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val today = state.today
    val list = Eligibility.birthdaysByNext(state.birthdays.filter { Search.birthday(it, query) }, today)
    val byId = state.birthdays.associateBy { it.id }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Birthdays") {
            if (state.birthdays.isNotEmpty() && !searching) TextActionWithIcon("Copy JSON", onCopyJson)
            IconAction(Glyph.Search, if (searching) "Close search" else "Search birthdays and anniversaries", {
                searching = !searching; if (!searching) query = ""
            }, if (searching) C.Violet else C.Text)
        }
        if (searching) SearchPanel(query, { query = it }, "Search by name or month", C.Violet)
        if (!state.loaded) return@Column
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = ListBottomPadding)) {
            if (list.isEmpty()) {
                item {
                    if (query.isNotBlank()) EmptyState("No matches.", "Nothing matches “${query.trim()}”. Try a name, or a month like “nov”.")
                    else EmptyState("Nothing here yet.", "Add birthdays and anniversaries for the people you want to remember. They come back every year on their own.")
                }
            }
            items(list, key = { it.key }) { i ->
                val b = byId[i.sourceId]
                val inWindow = b != null && i.daysUntil <= b.leadDays
                val sub = buildString {
                    if (i.anniversary) append("Anniversary  ·  ")
                    append("${monthAbbr(i.date.monthValue)} ${i.date.dayOfMonth}")
                    if (b != null && b.month == 2 && b.day == 29 && i.date.dayOfMonth == 28) append(" (Feb 29)")
                    Phrases.ageNote(i)?.let { append("  ·  $it") }
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
            .clickable(role = Role.Button, onClickLabel = "Copy all birthdays and anniversaries as JSON", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Copy, C.Violet, size = 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, style = T.Label.copy(fontSize = 14.sp), color = C.Violet)
    }
}
