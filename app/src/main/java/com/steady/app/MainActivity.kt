package com.steady.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import java.time.LocalTime
import java.util.UUID

data class LaunchableApp(val packageName: String, val label: String)

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

class MainActivity : ComponentActivity() {
    private lateinit var store: FocusStore
    private var refreshStatus: (() -> Unit)? = null
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        store.setFocusNotificationsEnabled(granted)
        if (granted) store.loadSession()?.let { FocusNotifications.showActive(this, it) }
        refreshStatus?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = FocusStore(this)
        FocusNotifications.ensureChannels(this)
        RoutineScheduler.rescheduleAll(this)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF176B62),
                    onPrimary = Color.White,
                    secondary = Color(0xFFBD6B47),
                    background = Color(0xFFF2F5F1),
                    surface = Color(0xFFFFFEFC),
                    onSurface = Color(0xFF172A27),
                    surfaceVariant = Color(0xFFE5ECE7),
                    onSurfaceVariant = Color(0xFF50645E),
                    primaryContainer = Color(0xFFD9EEE8),
                    onPrimaryContainer = Color(0xFF123F39),
                    secondaryContainer = Color(0xFFF5E7DF),
                    onSecondaryContainer = Color(0xFF633B2A),
                ),
                shapes = androidx.compose.material3.Shapes(
                    small = RoundedCornerShape(14.dp),
                    medium = RoundedCornerShape(22.dp),
                    large = RoundedCornerShape(30.dp),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SteadyApp(
                        context = this,
                        store = store,
                        isAccessibilityEnabled = ::isAccessibilityEnabled,
                        openAccessibilitySettings = ::openAccessibilitySettings,
                        isExactAlarmAccessEnabled = ::isExactAlarmAccessEnabled,
                        openExactAlarmSettings = ::openExactAlarmSettings,
                        startSession = ::startSession,
                        endSession = ::endSession,
                        setNotificationsEnabled = ::setNotificationsEnabled,
                        saveRoutine = ::saveRoutine,
                        deleteRoutine = ::deleteRoutine,
                        setRoutineEnabled = ::setRoutineEnabled,
                        registerRefresh = { refreshStatus = it },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        RoutineScheduler.rescheduleAll(this)
        refreshStatus?.invoke()
    }

    private fun isAccessibilityEnabled(): Boolean {
        return FocusPermissions.isAccessibilityEnabled(this)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isExactAlarmAccessEnabled(): Boolean = RoutineScheduler.canScheduleExactAlarms(this)

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            store.setFocusNotificationsEnabled(true)
            store.loadSession()?.let { FocusNotifications.showActive(this, it) }
            refreshStatus?.invoke()
        }
    }

    private fun setNotificationsEnabled(enabled: Boolean) {
        if (enabled) {
            requestNotifications()
        } else {
            store.setFocusNotificationsEnabled(false)
            FocusNotifications.cancelActive(this)
            refreshStatus?.invoke()
        }
    }

    private fun startSession(durationMinutes: Int, blockedPackages: Set<String>, intention: String) {
        val session = FocusSession.create(System.currentTimeMillis(), durationMinutes, blockedPackages, intention)
        store.saveSession(session)
        FocusNotifications.scheduleSessionEnd(this, session)
        if (store.areFocusNotificationsEnabled()) FocusNotifications.showActive(this, session)
    }

    private fun endSession() {
        store.finishSession(completed = false)
        FocusNotifications.cancelSessionAlarm(this)
        FocusNotifications.cancelActive(this)
    }

    private fun saveRoutine(routine: FocusRoutine) {
        store.saveRoutine(routine)
        RoutineScheduler.schedule(this, routine)
        refreshStatus?.invoke()
    }

    private fun deleteRoutine(id: String) {
        store.deleteRoutine(id)
        RoutineScheduler.cancel(this, id)
        refreshStatus?.invoke()
    }

    private fun setRoutineEnabled(routine: FocusRoutine, enabled: Boolean) {
        val updated = routine.copy(enabled = enabled)
        store.saveRoutine(updated)
        RoutineScheduler.schedule(this, updated)
        refreshStatus?.invoke()
    }
}

@androidx.compose.runtime.Composable
private fun SteadyApp(
    context: Context,
    store: FocusStore,
    isAccessibilityEnabled: () -> Boolean,
    openAccessibilitySettings: () -> Unit,
    isExactAlarmAccessEnabled: () -> Boolean,
    openExactAlarmSettings: () -> Unit,
    startSession: (Int, Set<String>, String) -> Unit,
    endSession: () -> Unit,
    setNotificationsEnabled: (Boolean) -> Unit,
    saveRoutine: (FocusRoutine) -> Unit,
    deleteRoutine: (String) -> Unit,
    setRoutineEnabled: (FocusRoutine, Boolean) -> Unit,
    registerRefresh: ((() -> Unit) -> Unit),
) {
    var refresh by remember { mutableIntStateOf(0) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var duration by remember { mutableIntStateOf(25) }
    var intention by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    val notificationsEnabled = remember(refresh) { store.areFocusNotificationsEnabled() }
    var selected by remember { mutableStateOf(store.loadSelectedApps()) }
    val savedSession = remember(refresh) { store.loadSession() }
    val session = savedSession?.takeIf { it.isActive(nowMillis) }
    val accessibilityEnabled = remember(refresh) { isAccessibilityEnabled() }
    val exactAlarmAccessEnabled = remember(refresh) { isExactAlarmAccessEnabled() }
    val apps = remember { loadLaunchableApps(context) }
    val visibleApps = remember(apps, search) {
        apps.filter { it.label.contains(search, ignoreCase = true) }
    }
    val routines = remember(refresh) { store.loadRoutines() }
    val history = store.loadHistory().asReversed()
    var editingRoutine by remember { mutableStateOf<FocusRoutine?>(null) }
    var showRoutineEditor by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<FocusRoutine?>(null) }
    SideEffect { registerRefresh { refresh++ } }
    LaunchedEffect(savedSession?.endsAtMillis) {
        while (savedSession != null && nowMillis < savedSession.endsAtMillis) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_steady_mark),
                contentDescription = "Steady logo",
                modifier = Modifier.size(48.dp),
            )
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "steady",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text("FOCUS AT YOUR PACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        Text("Make room for what matters.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Your routines", style = MaterialTheme.typography.headlineSmall)
                        Text("Automatic focus sessions, on your schedule.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { editingRoutine = null; showRoutineEditor = true }) { Text("Add") }
                }
                if (!accessibilityEnabled) {
                    Text(
                        "Scheduled routines cannot block apps until Steady's Accessibility service is enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = openAccessibilitySettings) { Text("Enable app blocking") }
                }
                if ((routines.any { it.enabled } || session != null) && !exactAlarmAccessEnabled) {
                    Text(
                        "Exact alarm access is off. Android may delay routine starts or session-end alerts, especially in battery-saving modes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = openExactAlarmSettings) { Text("Allow exact routine times") }
                }
                if (routines.isEmpty()) {
                    Text("Create a routine for study, reading, or any time you want to protect.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    routines.forEach { routine ->
                        RoutineRow(
                            routine = routine,
                            onToggle = { enabled -> setRoutineEnabled(routine, enabled); refresh++ },
                            onEdit = { editingRoutine = routine; showRoutineEditor = true },
                            onDelete = { routineToDelete = routine },
                        )
                    }
                }
            }
        }

        if (session != null) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("FOCUS IN PROGRESS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("Focus is on", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (session.intention.isNotBlank()) Text("“${session.intention}”", style = MaterialTheme.typography.titleMedium)
                    Text("${remainingMinutes(session.endsAtMillis)} min left · ${session.blockedPackages.size} apps paused")
                    val progress = ((nowMillis - session.startedAtMillis).toFloat() /
                        (session.endsAtMillis - session.startedAtMillis).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    OutlinedButton(onClick = { endSession(); refresh++ }) { Text("End focus") }
                }
            }
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("One-time focus", style = MaterialTheme.typography.headlineSmall)
                    Text("Choose what to focus on and which apps to pause. You can always end a session early.")
                    OutlinedTextField(
                        value = intention,
                        onValueChange = { intention = it.take(FocusSession.MAX_INTENTION_LENGTH) },
                        label = { Text("What do you want to focus on?") },
                        placeholder = { Text("Study biology, read, take a break…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$duration minutes", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 25, 45).forEach { minutes ->
                            OutlinedButton(onClick = { duration = minutes }) { Text(if (minutes == duration) "✓ $minutes" else "$minutes min") }
                        }
                    }
                    Slider(
                        value = duration.toFloat(),
                        onValueChange = { duration = it.roundToInt().coerceIn(FocusSession.MIN_DURATION_MINUTES, FocusSession.MAX_DURATION_MINUTES) },
                        valueRange = FocusSession.MIN_DURATION_MINUTES.toFloat()..FocusSession.MAX_DURATION_MINUTES.toFloat(),
                        steps = 174,
                    )
                    Text("Custom range: 5–180 minutes", style = MaterialTheme.typography.bodySmall)
                }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose apps to pause", style = MaterialTheme.typography.titleLarge)
                    Text("${selected.size} selected · saved on this device", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search your apps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(Modifier.height(220.dp)) {
                        items(visibleApps) { app ->
                            Row(
                                Modifier.fillMaxWidth().toggleable(
                                    value = app.packageName in selected,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        selected = if (checked) selected + app.packageName else selected - app.packageName
                                        store.saveSelectedApps(selected)
                                    },
                                ).padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked = app.packageName in selected, onCheckedChange = null)
                                Text(app.label, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }

            if (!accessibilityEnabled) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Turn on app blocking", style = MaterialTheme.typography.titleMedium)
                        Text("Android requires you to enable Steady's Accessibility Service. It only checks the app currently on screen during a focus session and processes that information on your phone.")
                        OutlinedButton(onClick = openAccessibilitySettings) { Text("Open Accessibility settings") }
                    }
                }
            }

            Button(
                onClick = {
                    startSession(duration, selected, intention)
                    refresh++
                },
                enabled = accessibilityEnabled && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Start focus · $duration min") }
            if (selected.isEmpty()) Text("Select at least one app to start.", style = MaterialTheme.typography.bodySmall)
            if (history.isNotEmpty()) {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Recent sessions", style = MaterialTheme.typography.titleLarge)
                        history.take(5).forEach { item ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.intention.ifBlank { "Focus session" }, style = MaterialTheme.typography.titleSmall)
                                    Text("${item.durationMinutes} min · ${item.blockedAppCount} apps · ${if (item.completed) "Finished" else "Ended early"}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(if (item.completed) "✓" else "·", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("History stays on this phone.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        FocusNotificationSetting(notificationsEnabled, setNotificationsEnabled)

        Spacer(Modifier.height(2.dp))
        Text("Sessions are optional. You stay in control and can stop any time.", style = MaterialTheme.typography.bodySmall)
    }

    if (showRoutineEditor) {
        RoutineEditorDialog(
            initial = editingRoutine,
            apps = apps,
            defaultApps = selected,
            onDismiss = { showRoutineEditor = false },
            onSave = { routine -> saveRoutine(routine); showRoutineEditor = false },
        )
    }

    routineToDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { routineToDelete = null },
            title = { Text("Delete ${routine.name}?") },
            text = { Text("This removes the routine and its upcoming alarms. Your previous sessions stay in history.") },
            confirmButton = {
                TextButton(onClick = { deleteRoutine(routine.id); routineToDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { routineToDelete = null }) { Text("Keep routine") }
            },
        )
    }
}

@androidx.compose.runtime.Composable
private fun RoutineRow(
    routine: FocusRoutine,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, style = MaterialTheme.typography.titleMedium)
                Text("${routine.hour.toString().padStart(2, '0')}:${routine.minute.toString().padStart(2, '0')} · ${routine.daysOfWeek.sorted().joinToString(" ") { DAY_LABELS[it - 1] }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = routine.enabled, onCheckedChange = onToggle)
        }
        Text("${routine.durationMinutes} min · ${routine.blockedPackages.size} apps paused${if (routine.intention.isNotBlank()) " · ${routine.intention}" else ""}", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
            Text(if (routine.enabled) "On" else "Off", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@androidx.compose.runtime.Composable
private fun RoutineEditorDialog(
    initial: FocusRoutine?,
    apps: List<LaunchableApp>,
    defaultApps: Set<String>,
    onDismiss: () -> Unit,
    onSave: (FocusRoutine) -> Unit,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var intention by remember(initial?.id) { mutableStateOf(initial?.intention.orEmpty()) }
    var timeText by remember(initial?.id) {
        mutableStateOf(initial?.let { "%02d:%02d".format(it.hour, it.minute) }
            ?: LocalTime.now().plusMinutes(30).withSecond(0).withNano(0).toString().take(5))
    }
    var duration by remember(initial?.id) { mutableIntStateOf(initial?.durationMinutes ?: 25) }
    var days by remember(initial?.id) { mutableStateOf(initial?.daysOfWeek ?: setOf(1, 2, 3, 4, 5)) }
    var selectedApps by remember(initial?.id) { mutableStateOf(initial?.blockedPackages ?: defaultApps) }
    var appSearch by remember(initial?.id) { mutableStateOf("") }
    val parsedTime = remember(timeText) { runCatching { LocalTime.parse(timeText) }.getOrNull() }
    val visibleApps = remember(apps, appSearch) { apps.filter { it.label.contains(appSearch, ignoreCase = true) } }
    val canSave = name.isNotBlank() && parsedTime != null && days.isNotEmpty() && selectedApps.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create a routine" else "Edit routine") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Steady will start this focus session automatically at the days and time you choose.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Routine name") },
                    placeholder = { Text("Morning study") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = intention,
                    onValueChange = { intention = it.take(FocusSession.MAX_INTENTION_LENGTH) },
                    label = { Text("What are you making time for? (optional)") },
                    placeholder = { Text("Review lecture notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it.filter { char -> char.isDigit() || char == ':' }.take(5) },
                    label = { Text("Start time (24-hour, HH:mm)") },
                    placeholder = { Text("08:30") },
                    supportingText = { if (parsedTime == null) Text("Enter a valid time, for example 08:30.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Repeat on", style = MaterialTheme.typography.titleSmall)
                val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1..4, 5..7).forEach { rowDays ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            rowDays.forEach { day ->
                                FilterChip(
                                    selected = day in days,
                                    onClick = { days = if (day in days) days - day else days + day },
                                    label = { Text(dayNames[day - 1]) },
                                )
                            }
                        }
                    }
                }
                Text("$duration minutes", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = duration.toFloat(),
                    onValueChange = { duration = (it / 5f).roundToInt().times(5).coerceIn(FocusSession.MIN_DURATION_MINUTES, FocusSession.MAX_DURATION_MINUTES) },
                    valueRange = FocusSession.MIN_DURATION_MINUTES.toFloat()..FocusSession.MAX_DURATION_MINUTES.toFloat(),
                    steps = 34,
                )
                Text("Apps to pause", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = appSearch,
                    onValueChange = { appSearch = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${selectedApps.size} selected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.height(150.dp)) {
                    items(visibleApps) { app ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(
                                value = app.packageName in selectedApps,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    selectedApps = if (checked) selectedApps + app.packageName else selectedApps - app.packageName
                                },
                            ).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = app.packageName in selectedApps, onCheckedChange = null)
                            Text(app.label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (!canSave) Text("Add a name, valid time, at least one day, and one app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val time = requireNotNull(parsedTime)
                    onSave(
                        FocusRoutine(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            intention = intention.trim(),
                            hour = time.hour,
                            minute = time.minute,
                            daysOfWeek = days,
                            durationMinutes = duration,
                            blockedPackages = selectedApps,
                            enabled = initial?.enabled ?: true,
                        ),
                    )
                },
            ) { Text("Save routine") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@androidx.compose.runtime.Composable
private fun FocusNotificationSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Focus notification", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (enabled) "Quiet countdown stays visible while focus is active."
                    else "Show a quiet countdown while focus is active.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

private fun loadLaunchableApps(context: Context): List<LaunchableApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val dialerPackage = context.packageManager.resolveActivity(
        Intent(Intent.ACTION_DIAL),
        PackageManager.MATCH_DEFAULT_ONLY,
    )?.activityInfo?.packageName
    return context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .mapNotNull { resolve ->
            val pkg = resolve.activityInfo?.packageName ?: return@mapNotNull null
            if (pkg == context.packageName) return@mapNotNull null
            val info = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull() ?: return@mapNotNull null
            if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0 || pkg == dialerPackage) return@mapNotNull null
            LaunchableApp(pkg, resolve.loadLabel(context.packageManager).toString())
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun remainingMinutes(endsAtMillis: Long): Long =
    ((endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L) + 59_999L) / 60_000L
