package app.kept.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kept.data.Settings
import app.kept.domain.Phrases
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    notificationsAllowed: Boolean,
    onRequestPermission: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onTime: (Int, Int) -> Unit,
    onReminderLead: (Int) -> Unit,
    onBirthdayLead: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var pickingTime by remember { mutableStateOf(false) }
    val time = LocalTime.of(settings.notifyHour, settings.notifyMinute)
    val timeLabel = time.format(
        if (DateFormat.is24HourFormat(context)) DateTimeFormatter.ofPattern("HH:mm") else DateTimeFormatter.ofPattern("h:mm a")
    )

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(Glyph.Back, "Back", onBack)
            Text("Settings", style = T.Heading, color = C.Text, modifier = Modifier.padding(start = 4.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            SectionLabel("Notifications")
            Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Switch) { onEnabled(!settings.notificationsEnabled) }
                    .padding(horizontal = ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily notification", style = T.Heading, color = C.Text)
                    Text("Once a day for each active item, until cleared", style = T.Label, color = C.Muted)
                }
                Switch(
                    checked = settings.notificationsEnabled, onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = C.Bg, checkedTrackColor = C.Accent,
                        uncheckedThumbColor = C.Muted, uncheckedTrackColor = C.Bg, uncheckedBorderColor = C.Muted,
                    ),
                )
            }
            Hairline(Modifier.padding(horizontal = ScreenPadding))
            if (settings.notificationsEnabled) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Button, onClickLabel = "Change notification time") { pickingTime = true }
                        .padding(horizontal = ScreenPadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Time", style = T.Heading, color = C.Text, modifier = Modifier.weight(1f))
                    Text(timeLabel, style = T.Numeral.copy(fontSize = 26.sp), color = C.Accent)
                }
                Hairline(Modifier.padding(horizontal = ScreenPadding))
            }
            if (settings.notificationsEnabled && !notificationsAllowed) {
                Column(Modifier.padding(horizontal = ScreenPadding, vertical = 16.dp)) {
                    Text("Notifications are blocked for Kept in Android.", style = T.Body, color = C.Danger)
                    TextAction("Allow notifications", C.Accent, onRequestPermission)
                }
                Hairline(Modifier.padding(horizontal = ScreenPadding))
            }

            SectionLabel("Defaults for new items")
            LeadSetting("Reminders", settings.defaultReminderLead, onReminderLead)
            LeadSetting("Birthdays", settings.defaultBirthdayLead, onBirthdayLead)

            SectionLabel("Widget")
            Text(
                "Shows one item at a time and moves to the next about every 14 seconds while it is on screen. " +
                    "The rotation runs inside your launcher, so Kept does no background work for it. " +
                    "The widget updates itself whenever your reminders or the date change.",
                style = T.Body, color = C.Muted, modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 8.dp),
            )
            val manager = remember { android.appwidget.AppWidgetManager.getInstance(context) }
            if (manager.isRequestPinAppWidgetSupported) {
                TextAction(
                    "Add widget to home screen", C.Accent,
                    onClick = {
                        manager.requestPinAppWidget(
                            android.content.ComponentName(context, app.kept.widget.RotatingWidgetProvider::class.java), null, null,
                        )
                    },
                    modifier = Modifier.padding(horizontal = ScreenPadding - 4.dp),
                )
            }
        }
    }

    if (pickingTime) {
        val ts = rememberTimePickerState(settings.notifyHour, settings.notifyMinute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            containerColor = C.Raised,
            text = { TimePicker(ts) },
            confirmButton = { TextButton({ onTime(ts.hour, ts.minute); pickingTime = false }) { Text("OK", color = C.Accent) } },
            dismissButton = { TextButton({ pickingTime = false }) { Text("Cancel", color = C.Muted) } },
        )
    }
}

@Composable
private fun LeadSetting(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp)) {
        Text(label, style = T.Label, color = C.Muted)
        Spacer(Modifier.height(6.dp))
        Stepper(value, onChange, 0..365, label = { Phrases.leadLabel(it) })
    }
}
