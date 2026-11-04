package app.kept.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kept.domain.AgendaItem
import app.kept.domain.Eligibility
import app.kept.domain.ItemKind
import app.kept.domain.Phrases
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.WeekFields
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/**
 * A bird's-eye month view, opened from the calendar icon on Today (it is deliberately not a bottom tab).
 * Dots under each day: cyan = reminder due, violet = birthday, coral = overdue reminder.
 * Tap a day to see what is on it; swipe or use the arrows to change month.
 */
@Composable
fun CalendarScreen(
    state: UiState,
    onBack: () -> Unit,
    onOpenReminder: (Long) -> Unit,
    onOpenBirthday: (Long) -> Unit,
    onAddReminder: (LocalDate) -> Unit,
) {
    val today = state.today
    var monthIdx by rememberSaveable { mutableIntStateOf(today.year * 12 + today.monthValue - 1) }
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val month = YearMonth.of(monthIdx / 12, monthIdx % 12 + 1)
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val events = remember(state.reminders, state.birthdays, month, today) {
        Eligibility.calendarEvents(state.reminders, state.birthdays, today, month)
    }
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val leading = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7
    val weeks = (leading + month.lengthOfMonth() + 6) / 7

    fun go(delta: Int) {
        monthIdx += delta
        val m = YearMonth.of(monthIdx / 12, monthIdx % 12 + 1)
        // keep the selection inside the visible month
        selectedEpoch = if (YearMonth.from(selected) == m) selectedEpoch else m.atDay(minOf(selected.dayOfMonth, m.lengthOfMonth())).toEpochDay()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Glyph.Back, "Back", onBack)
            Text(
                "${month.month.getDisplayName(DateTextStyle.FULL, Locale.getDefault())} ${month.year}",
                style = T.Heading.copy(fontSize = 20.sp), color = C.Text, modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (month != YearMonth.from(today) || selected != today) {
                Box(
                    Modifier.heightIn(min = 40.dp).clip(CircleShape).background(C.Raised)
                        .clickable(role = Role.Button, onClickLabel = "Jump to today") {
                            monthIdx = today.year * 12 + today.monthValue - 1; selectedEpoch = today.toEpochDay()
                        }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Today", style = T.Label.copy(fontSize = 14.sp), color = C.Accent) }
                Spacer(Modifier.width(4.dp))
            }
            IconAction(Glyph.ChevLeft, "Previous month", { go(-1) })
            IconAction(Glyph.ChevRight, "Next month", { go(1) })
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).pointerInput(monthIdx) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx > 110) go(-1) else if (dx < -110) go(1) },
                ) { _, d -> dx += d }
            },
        ) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val dow = first.plus(i.toLong())
                    Text(
                        dow.getDisplayName(DateTextStyle.NARROW, Locale.getDefault()), style = T.Caps, color = C.Muted,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    )
                }
            }
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val dayNum = w * 7 + c - leading + 1
                        Box(Modifier.weight(1f).height(54.dp), contentAlignment = Alignment.Center) {
                            if (dayNum in 1..month.lengthOfMonth()) {
                                val date = month.atDay(dayNum)
                                DayCell(date, today, selected == date, events[date].orEmpty()) { selectedEpoch = date.toEpochDay() }
                            }
                        }
                    }
                }
            }
        }

        // ── selected day ──
        val dayItems = events[selected].orEmpty()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 24.dp)) {
            Text(
                dayTitle(selected, today), style = T.Heading, color = C.Text,
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 10.dp),
            )
            if (dayItems.isEmpty()) {
                Text("Nothing on this day.", style = T.Body, color = C.Muted, modifier = Modifier.padding(horizontal = ScreenPadding))
            }
            dayItems.forEach { i ->
                CalendarRow(i, today) { if (i.kind == ItemKind.REMINDER) onOpenReminder(i.sourceId) else onOpenBirthday(i.sourceId) }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp).clip(CircleShape)
                    .clickable(role = Role.Button) { onAddReminder(selected) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text("+  Add a reminder for this day", style = T.Label.copy(fontSize = 15.sp), color = C.Accent) }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, today: LocalDate, isSelected: Boolean, items: List<AgendaItem>, onClick: () -> Unit) {
    val isToday = date == today
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxSize().padding(2.dp).clip(shape)
            .background(if (isSelected) C.Accent.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (isToday) Modifier.border(BorderStroke(1.5.dp, C.Accent), shape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${date.dayOfMonth}, ${items.size} ${if (items.size == 1) "item" else "items"}"
            },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${date.dayOfMonth}", style = T.Body.copy(fontSize = 16.sp),
            color = when { isSelected || isToday -> C.Text; items.isNotEmpty() -> C.Text; else -> C.Muted },
        )
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(toneOf(it))) }
        }
    }
}

@Composable
private fun CalendarRow(item: AgendaItem, today: LocalDate, onClick: () -> Unit) {
    val tone = toneOf(item)
    val sub = when (item.kind) {
        ItemKind.REMINDER -> if (item.date.isBefore(today)) Phrases.status(item) else "Reminder due"
        ItemKind.BIRTHDAY -> if (item.turningAge != null) "Birthday  ·  turns ${item.turningAge}" else "Birthday"
    }
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) { Marker(item.kind, tone) }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = T.Heading, color = C.Text, maxLines = 2)
            Text(sub, style = T.Label, color = if (item.isOverdue && item.kind == ItemKind.REMINDER) C.Danger else C.Muted)
        }
    }
}

private fun dayTitle(d: LocalDate, today: LocalDate): String {
    val base = "${d.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault())}, ${d.dayOfMonth} ${monthFull(d.monthValue)}"
    return when (Eligibility.daysBetween(today, d)) {
        0 -> "Today  ·  $base"
        1 -> "Tomorrow  ·  $base"
        -1 -> "Yesterday  ·  $base"
        else -> base
    }
}
