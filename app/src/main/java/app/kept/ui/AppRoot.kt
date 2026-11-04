package app.kept.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kept.domain.Birthday
import app.kept.domain.Reminder
import app.kept.notify.Notifier
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var route by rememberSaveable { mutableStateOf("home") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var notifAllowed by remember { mutableStateOf(Notifier.canPost(context)) }

    // Re-evaluate "today" and the permission when coming back to the app.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshToday()
        notifAllowed = Notifier.canPost(context)
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifAllowed = Notifier.canPost(context)
    }
    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }
    /** Ask once, in context, the first time the user creates something - not at cold start. */
    fun askPermissionIfNeeded() {
        if (state.settings.notificationsEnabled && !notifAllowed && Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun toast(message: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(message, action, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }

    fun home() { route = "home" }
    BackHandler(enabled = route != "home") { home() }

    Box(Modifier.fillMaxSize().background(C.Bg).windowInsetsPadding(WindowInsets.systemBars)) {
        Crossfade(route, animationSpec = tween(140), label = "route") { r ->
            when {
                r == "home" -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            0 -> TodayScreen(
                                state,
                                onOpenReminder = { route = "reminder/$it" },
                                onOpenBirthday = { route = "birthday/$it" },
                                onSettings = { route = "settings" },
                            )
                            1 -> RemindersScreen(
                                state,
                                onOpen = { route = "reminder/$it" },
                                onClear = { rem ->
                                    vm.setCleared(rem.id, true)
                                    toast("Cleared “${rem.title}”", "Undo") { vm.setCleared(rem.id, false) }
                                },
                                onRestore = { rem ->
                                    vm.setCleared(rem.id, false)
                                    toast("Restored “${rem.title}”")
                                },
                            )
                            else -> BirthdaysScreen(
                                state,
                                onOpen = { route = "birthday/$it" },
                                onCopyJson = {
                                    scope.launch {
                                        val (json, n) = vm.birthdayJson()
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Kept birthdays", json))
                                        toast("Copied $n ${if (n == 1) "birthday" else "birthdays"} as JSON")
                                    }
                                },
                            )
                        }
                        AddButton(
                            if (tab == 2) "Add birthday" else "Add reminder",
                            onClick = { route = if (tab == 2) "birthday/new" else "reminder/new" },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
                        )
                    }
                    BottomBar(tab) { tab = it }
                }

                r.startsWith("reminder/") -> {
                    val id = r.removePrefix("reminder/").toLongOrNull()
                    val existing = id?.let { i -> state.reminders.firstOrNull { it.id == i } }
                    if (id != null && existing == null && state.loaded) { LaunchedEffect(Unit) { home() } } else {
                        ReminderEditor(
                            initial = existing, today = state.today, defaultLead = state.settings.defaultReminderLead,
                            onSave = { vm.save(it); askPermissionIfNeeded(); home(); toast(if (existing == null) "Reminder added" else "Saved") },
                            onDelete = { vm.deleteReminder(it); home(); toast("Deleted") },
                            onToggleCleared = { rem ->
                                val clearing = rem.clearedAtMillis == null
                                vm.setCleared(rem.id, clearing); home()
                                toast(if (clearing) "Cleared “${rem.title}”" else "Restored “${rem.title}”")
                            },
                            onBack = ::home,
                        )
                    }
                }

                r.startsWith("birthday/") -> {
                    val id = r.removePrefix("birthday/").toLongOrNull()
                    val existing = id?.let { i -> state.birthdays.firstOrNull { it.id == i } }
                    if (id != null && existing == null && state.loaded) { LaunchedEffect(Unit) { home() } } else {
                        BirthdayEditor(
                            initial = existing, today = state.today, defaultLead = state.settings.defaultBirthdayLead,
                            onSave = { vm.save(it); askPermissionIfNeeded(); home(); toast(if (existing == null) "Birthday added" else "Saved") },
                            onDelete = { vm.deleteBirthday(it); home(); toast("Deleted") },
                            onBack = ::home,
                        )
                    }
                }

                else -> SettingsScreen(
                    settings = state.settings,
                    notificationsAllowed = notifAllowed,
                    onRequestPermission = ::requestNotifications,
                    onEnabled = { vm.setNotificationsEnabled(it); if (it) askPermissionIfNeeded() },
                    onTime = vm::setNotifyTime,
                    onReminderLead = vm::setReminderLead,
                    onBirthdayLead = vm::setBirthdayLead,
                    onBack = ::home,
                )
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (route == "home") 156.dp else 96.dp)) { data ->
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(C.Raised).padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(data.visuals.message, style = T.Body, color = C.Text, modifier = Modifier.weight(1f).padding(vertical = 14.dp))
                data.visuals.actionLabel?.let { label ->
                    Box(
                        Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { data.performAction() }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, style = T.Heading, color = C.Accent) }
                }
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf("Today", "Reminders", "Birthdays")
    Column {
        Hairline()
        Row(Modifier.fillMaxWidth().background(C.Bg)) {
            items.forEachIndexed { i, label ->
                Column(
                    Modifier.weight(1f).heightIn(min = 64.dp).clickable(role = Role.Tab, onClick = { onSelect(i) }),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    // Active tab carries its own marker: square for reminders, circle for birthdays, bar for today.
                    Box(Modifier.height(8.dp), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.then(when (i) { 0 -> Modifier.size(width = 16.dp, height = 4.dp); else -> Modifier.size(8.dp) })
                                .background(if (i == selected) C.Accent else C.Line, if (i == 2) androidx.compose.foundation.shape.CircleShape else androidx.compose.ui.graphics.RectangleShape)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(label, style = T.Label.copy(fontSize = 14.sp), color = if (i == selected) C.Text else C.Muted)
                }
            }
        }
    }
}
