package app.kept.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.onFocusChanged
import app.kept.domain.AgendaItem
import app.kept.domain.ItemKind
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import kotlin.math.abs

val ScreenPadding = 24.dp

@Composable
fun Hairline(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(1.dp).background(C.Line))

/** Shape language: reminders are squares, birthdays are circles. Used in the app, widget and notifications. */
@Composable
fun Marker(kind: ItemKind, color: Color, size: Dp = 10.dp) {
    Box(
        Modifier.size(size).background(color, if (kind == ItemKind.BIRTHDAY) CircleShape else RectangleShape)
    )
}

@Composable
fun IconAction(glyph: Glyph, description: String, onClick: () -> Unit, tint: Color = C.Text) {
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(glyph, tint) }
}

@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding - 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = T.Title, color = C.Text, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), style = T.Caps, color = C.Muted,
        modifier = modifier.padding(start = ScreenPadding, end = ScreenPadding, top = 28.dp, bottom = 6.dp),
    )
}

@Composable
fun KeptField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    style: TextStyle = T.Body,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = style.copy(color = C.Text),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            cursorBrush = SolidColor(C.Accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp)
                .semantics { contentDescription = placeholder }
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = style, color = C.Muted)
                    inner()
                }
            },
        )
        Box(Modifier.fillMaxWidth().height(if (focused) 2.dp else 1.dp).background(if (focused) C.Accent else C.Line))
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).defaultMinSize(minWidth = 48.dp)
            .background(if (selected) C.Accent else Color.Transparent, RectangleShape)
            .border(BorderStroke(1.dp, if (selected) C.Accent else C.Line), RectangleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = T.Label.copy(fontSize = 14.sp), color = if (selected) C.Bg else C.Text, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** Minus / value / plus. Value is shown big; touch targets are 48dp. */
@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, label: (Int) -> String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", "Decrease", enabled = value > range.first) { onChange(value - 1) }
        Text(
            label(value), style = T.Heading, color = C.Text, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { contentDescription = label(value) },
        )
        StepButton("+", "Increase", enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).border(BorderStroke(1.dp, C.Line))
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = description, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = T.Heading, color = if (enabled) C.Text else C.Line) }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().height(56.dp)
            .background(if (enabled) C.Accent else C.Raised, RectangleShape)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.Heading, color = if (enabled) C.Bg else C.Muted) }
}

@Composable
fun TextAction(text: String, color: Color = C.Text, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) { Text(text, style = T.Label.copy(fontSize = 15.sp), color = color) }
}

@Composable
fun UnderlineTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        labels.forEachIndexed { i, l ->
            Column(Modifier.width(IntrinsicSize.Max).clickable(role = Role.Tab, onClick = { onSelect(i) })) {
                Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    Text(l, style = T.Heading, color = if (i == selected) C.Text else C.Muted)
                }
                Box(Modifier.height(2.dp).fillMaxWidth().background(if (i == selected) C.Accent else Color.Transparent))
            }
        }
    }
    Hairline()
}

fun toneOf(item: AgendaItem, active: Boolean = true): Color = when {
    item.isOverdue -> C.Danger
    active -> C.Accent
    else -> C.Muted
}

/** The countdown block on the right of a row: "12 / days", "0 / today", "2 / days late". */
@Composable
fun DaysBlock(item: AgendaItem, tone: Color) {
    val d = item.daysUntil
    val unit = when {
        d < 0 -> if (d == -1) "day late" else "days late"
        d == 0 -> "today"
        d == 1 -> "day"
        else -> "days"
    }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(abs(d).toString(), style = T.Numeral, color = tone)
        Text(unit, style = T.Label, color = C.Muted)
    }
}

@Composable
fun AgendaRow(
    item: AgendaItem,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
    showDays: Boolean = true,
) {
    val tone = toneOf(item, active)
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = ScreenPadding, vertical = 14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) } else {
            Box(Modifier.width(22.dp), contentAlignment = Alignment.CenterStart) { Marker(item.kind, tone) }
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, style = T.Heading, color = if (active) C.Text else C.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = T.Label, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showDays) {
            Spacer(Modifier.width(16.dp))
            DaysBlock(item, tone)
        }
    }
    Hairline(Modifier.padding(horizontal = ScreenPadding))
}

@Composable
fun EmptyState(headline: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 56.dp)) {
        Canvas(Modifier.size(width = 72.dp, height = 40.dp)) {
            val s = size.height
            drawRect(C.Accent, size = androidx.compose.ui.geometry.Size(s * 0.55f, s * 0.55f))
            drawCircle(C.Line, radius = s * 0.3f, center = androidx.compose.ui.geometry.Offset(s * 1.15f, s * 0.7f))
            drawRect(C.Line, topLeft = androidx.compose.ui.geometry.Offset(s * 1.75f, s * 0.35f), size = androidx.compose.ui.geometry.Size(s * 0.6f, s * 0.12f))
        }
        Spacer(Modifier.height(20.dp))
        Text(headline, style = T.Title, color = C.Text)
        Spacer(Modifier.height(6.dp))
        Text(body, style = T.Body, color = C.Muted)
    }
}

/** Flat square add button, bottom-right. */
@Composable
fun AddButton(description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.size(60.dp).background(C.Accent, RectangleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { GlyphIcon(Glyph.Plus, C.Bg, size = 28.dp) }
}
