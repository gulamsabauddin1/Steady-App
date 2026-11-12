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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import java.time.LocalTime
import java.util.UUID
import java.util.Date
import java.text.DateFormat

data class LaunchableApp(val packageName: String, val label: String)

private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private enum class SteadyTab(val label: String, val glyph: String) {
    TODAY("Today", "⌂"),
    ROUTINES("Routines", "↻"),
    FOCUS("Focus", "◉"),
    PROGRESS("Progress", "▤"),
    SETTINGS("Settings", "⚙"),
}

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
        val checkIn = store.loadStoredSession()?.let(store::createPendingCheckIn)
        store.finishSession(completed = false)
        FocusNotifications.cancelSessionAlarm(this)
        FocusNotifications.cancelActive(this)
        if (store.areFocusNotificationsEnabled() && checkIn != null) {
            FocusNotifications.showRoutineCheckIn(this, checkIn)
        }
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
    var selected by remember { mutableStateOf(store.loadSelectedApps()) }
    var apps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var appsLoaded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SteadyTab.FOCUS) }
    var editingRoutine by remember { mutableStateOf<FocusRoutine?>(null) }
    var showRoutineEditor by remember { mutableStateOf(false) }
    var routineToDelete by remember { mutableStateOf<FocusRoutine?>(null) }
    var showWeeklyReview by remember { mutableStateOf(false) }
    var recoveryRoutine by remember { mutableStateOf<FocusRoutine?>(null) }
    var dismissedCheckInKey by remember { mutableStateOf<String?>(null) }

    val notificationsEnabled = remember(refresh) { store.areFocusNotificationsEnabled() }
    val savedSession = remember(refresh) { store.loadSession() }
    val session = savedSession?.takeIf { it.isActive(nowMillis) }
    val accessibilityEnabled = remember(refresh) { isAccessibilityEnabled() }
    val exactAlarmAccessEnabled = remember(refresh) { isExactAlarmAccessEnabled() }
    val routines = remember(refresh) { store.loadRoutines() }
    val history = remember(refresh) { store.loadHistory().asReversed() }
    val routineOutcomes = remember(refresh) { store.loadRoutineOutcomes().asReversed() }
    val pendingCheckIns = remember(refresh) { store.loadPendingCheckIns() }
    val checkInToShow = pendingCheckIns.firstOrNull { it.key != dismissedCheckInKey }
    val weeklySummary = remember(routineOutcomes) { summarizeRoutineOutcomes(routineOutcomes) }
    val nextRoutine = remember(routines, nowMillis) {
        routines.filter { it.enabled }
            .mapNotNull { routine -> RoutineScheduler.nextTriggerMillis(routine, nowMillis)?.let { routine to it } }
            .minByOrNull { it.second }
    }
    val visibleApps = remember(apps, search) { apps.filter { it.label.contains(search, ignoreCase = true) } }

    SideEffect { registerRefresh { refresh++ } }
    LaunchedEffect(selectedTab, showRoutineEditor) {
        if ((selectedTab == SteadyTab.FOCUS || showRoutineEditor) && !appsLoaded) {
            apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
            appsLoaded = true
        }
    }
    LaunchedEffect(savedSession?.endsAtMillis) {
        while (savedSession != null && nowMillis < savedSession.endsAtMillis) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                SteadyTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Text(tab.glyph, fontSize = 26.sp, lineHeight = 28.sp, textAlign = TextAlign.Center)
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_steady_mark),
                    contentDescription = "Steady logo",
                    modifier = Modifier.size(42.dp),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text("steady", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(tabSubtitle(selectedTab), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (selectedTab) {
                    SteadyTab.TODAY -> {
                        Text("Make room for what matters.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (session != null) ActiveSessionCard(session, nowMillis) { endSession(); refresh++ }
                        else {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Your next step", style = MaterialTheme.typography.headlineSmall)
                                    if (nextRoutine == null) {
                                        Text("Add one routine to give your week a simple anchor.")
                                    } else {
                                        val (routine, triggerAt) = nextRoutine
                                        Text(routine.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(triggerAt))} · ${routine.durationMinutes} min focus")
                                        Text("Small version: ${routine.minimumAction.ifBlank { routine.name }}", style = MaterialTheme.typography.bodyMedium)
                                        if (routine.fallbackPlan.isNotBlank()) Text("If plans change: ${routine.fallbackPlan}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(onClick = { selectedTab = SteadyTab.ROUTINES }) { Text(if (routines.isEmpty()) "Create a routine" else "View routines") }
                                }
                            }
                        }
                        if (pendingCheckIns.isNotEmpty()) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("A quick check-in is ready", style = MaterialTheme.typography.titleMedium)
                                    Text("${pendingCheckIns.size} routine ${if (pendingCheckIns.size == 1) "session" else "sessions"} to review. Any progress counts, including the smallest version.")
                                    OutlinedButton(onClick = { selectedTab = SteadyTab.PROGRESS; dismissedCheckInKey = null }) { Text("Review check-in") }
                                }
                            }
                        }
                        WeeklySummaryCard(weeklySummary)
                    }

                    SteadyTab.ROUTINES -> {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Your routines", style = MaterialTheme.typography.headlineSmall)
                                        Text("Automatic focus sessions, on your schedule.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(onClick = { editingRoutine = null; showRoutineEditor = true }) { Text("Add") }
                                }
                                if (!accessibilityEnabled) PermissionNotice(
                                    title = "App blocking is off",
                                    detail = "Scheduled routines cannot pause apps until Steady's Accessibility service is enabled.",
                                    button = "Enable app blocking",
                                    onClick = openAccessibilitySettings,
                                )
                                if ((routines.any { it.enabled } || session != null) && !exactAlarmAccessEnabled) PermissionNotice(
                                    title = "Routine times may be delayed",
                                    detail = "Allow exact alarms so Android can start routines close to the time you chose.",
                                    button = "Allow exact routine times",
                                    onClick = openExactAlarmSettings,
                                )
                                if (routines.isEmpty()) Text("Create a routine for study, reading, or any time you want to protect.")
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

                    SteadyTab.FOCUS -> {
                        if (session != null) ActiveSessionCard(session, nowMillis) { endSession(); refresh++ }
                        else {
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
                            if (!accessibilityEnabled) PermissionNotice(
                                title = "Turn on app blocking",
                                detail = "Android requires Steady's Accessibility service. It checks only which app is currently open during focus and processes that on your phone.",
                                button = "Open Accessibility settings",
                                onClick = openAccessibilitySettings,
                            )
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
                                    if (!appsLoaded) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
                                    LazyColumn(Modifier.height(220.dp)) {
                                        items(visibleApps, key = { it.packageName }) { app ->
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
                            Button(
                                onClick = { startSession(duration, selected, intention); refresh++ },
                                enabled = accessibilityEnabled && selected.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Start focus · $duration min") }
                            if (selected.isEmpty()) Text("Select at least one app to start.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    SteadyTab.PROGRESS -> {
                        WeeklySummaryCard(weeklySummary)
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Weekly reset", style = MaterialTheme.typography.titleLarge)
                                Text("What would make next week easier? Keep the plan, shrink an action, move a routine, or pause it.")
                                OutlinedButton(onClick = { showWeeklyReview = true }) { Text("Review my week") }
                            }
                        }
                        if (pendingCheckIns.isNotEmpty()) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Routine check-ins", style = MaterialTheme.typography.titleLarge)
                                    pendingCheckIns.forEach { item ->
                                        Text("${item.routineName} · ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.scheduledAtMillis))}")
                                        TextButton(onClick = { dismissedCheckInKey = null }) { Text("Check in") }
                                    }
                                }
                            }
                        }
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Routine activity", style = MaterialTheme.typography.titleLarge)
                                if (routineOutcomes.isEmpty()) Text("Your routine check-ins will appear here after your next scheduled session.")
                                routineOutcomes.take(12).forEach { item ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.routineName, style = MaterialTheme.typography.titleSmall)
                                            Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.scheduledAtMillis))}${if (item.detail.isBlank()) "" else " · ${item.detail}"}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(outcomeLabel(item.outcome), color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                        if (history.isNotEmpty()) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Focus sessions", style = MaterialTheme.typography.titleLarge)
                                    history.take(10).forEach { item ->
                                        Text("${item.intention.ifBlank { "Focus session" }} · ${item.durationMinutes} min · ${if (item.completed) "Finished" else "Ended early"}")
                                    }
                                    Text("History stays on this phone.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    SteadyTab.SETTINGS -> {
                        Text("Permissions and preferences", style = MaterialTheme.typography.headlineSmall)
                        FocusNotificationSetting(notificationsEnabled) { setNotificationsEnabled(it); refresh++ }
                        PermissionNotice(
                            title = if (accessibilityEnabled) "App blocking is enabled" else "App blocking is disabled",
                            detail = "Steady uses Accessibility only to see which app is open during a focus session. You can change this in Android Settings.",
                            button = if (accessibilityEnabled) "Manage Accessibility" else "Enable app blocking",
                            onClick = openAccessibilitySettings,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PermissionNotice(
                            title = if (exactAlarmAccessEnabled) "Routine timing is enabled" else "Routine timing may be delayed",
                            detail = "Exact alarms let scheduled focus start close to the selected time. Android can delay inexact alarms during power saving.",
                            button = "Manage alarm access",
                            onClick = openExactAlarmSettings,
                        )
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Your data", style = MaterialTheme.typography.titleLarge)
                                Text("Routines, app choices, check-ins, and focus history are stored on this phone. Steady does not upload your app usage.")
                            }
                        }
                    }
                }
            }
        }
    }

    checkInToShow?.let { checkIn ->
        RoutineCheckInDialog(
            checkIn = checkIn,
            onLater = { dismissedCheckInKey = checkIn.key; selectedTab = SteadyTab.PROGRESS },
            onOutcome = { outcome ->
                store.resolvePendingCheckIn(checkIn, outcome)
                dismissedCheckInKey = checkIn.key
                if (outcome == RoutineOutcome.MISSED) recoveryRoutine = routines.firstOrNull { it.id == checkIn.routineId }
                refresh++
            },
        )
    }

    if (showWeeklyReview) {
        WeeklyReviewDialog(
            summary = weeklySummary,
            onDismiss = { showWeeklyReview = false },
            onAdjust = {
                showWeeklyReview = false
                selectedTab = SteadyTab.ROUTINES
            },
        )
    }

    recoveryRoutine?.let { routine ->
        RoutineRecoveryDialog(
            routine = routine,
            onKeep = { recoveryRoutine = null },
            onEdit = {
                recoveryRoutine = null
                editingRoutine = routine
                selectedTab = SteadyTab.ROUTINES
                showRoutineEditor = true
            },
            onPause = {
                setRoutineEnabled(routine, false)
                recoveryRoutine = null
                refresh++
            },
        )
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

private fun tabSubtitle(tab: SteadyTab): String = when (tab) {
    SteadyTab.TODAY -> "YOUR NEXT STEP"
    SteadyTab.ROUTINES -> "PLANS THAT FIT YOUR WEEK"
    SteadyTab.FOCUS -> "PROTECT A LITTLE TIME"
    SteadyTab.PROGRESS -> "NOTICE, THEN ADJUST"
    SteadyTab.SETTINGS -> "YOUR PREFERENCES"
}

private fun outcomeLabel(outcome: RoutineOutcome): String = when (outcome) {
    RoutineOutcome.PENDING -> "Check in"
    RoutineOutcome.MINIMUM -> "Small step ✓"
    RoutineOutcome.NORMAL -> "Done ✓"
    RoutineOutcome.EXTRA -> "Extra ✓"
    RoutineOutcome.MISSED -> "Missed"
    RoutineOutcome.SKIPPED -> "Skipped"
}

@androidx.compose.runtime.Composable
private fun ActiveSessionCard(session: FocusSession, nowMillis: Long, onEnd: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
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
            OutlinedButton(onClick = onEnd) { Text("End focus") }
        }
    }
}

@androidx.compose.runtime.Composable
private fun PermissionNotice(title: String, detail: String, button: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClick) { Text(button) }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WeeklySummaryCard(summary: RoutineWeeklySummary) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This week", style = MaterialTheme.typography.titleLarge)
            Text(
                if (summary.total == 0) "Your routine check-ins will build a picture here."
                else "${summary.completed} of ${summary.total} routine sessions completed · ${summary.minimumCount} small steps counted.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (summary.total > 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { summary.completed.toFloat() / summary.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("${summary.missed} missed · ${summary.skipped} skipped · ${summary.pending} awaiting check-in", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RoutineCheckInDialog(
    checkIn: PendingRoutineCheckIn,
    onLater: () -> Unit,
    onOutcome: (RoutineOutcome) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("How did ${checkIn.routineName} go?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Count what you did. A small step counts.")
                OutlinedButton(onClick = { onOutcome(RoutineOutcome.MINIMUM) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Small step · ${checkIn.minimumAction.ifBlank { checkIn.routineName }}")
                }
                OutlinedButton(onClick = { onOutcome(RoutineOutcome.NORMAL) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Usual · ${checkIn.normalAction.ifBlank { checkIn.routineName }}")
                }
                if (checkIn.extraAction.isNotBlank()) {
                    OutlinedButton(onClick = { onOutcome(RoutineOutcome.EXTRA) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Extra · ${checkIn.extraAction}")
                    }
                }
                if (checkIn.fallbackPlan.isNotBlank()) {
                    Text("For next time: ${checkIn.fallbackPlan}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { onOutcome(RoutineOutcome.MISSED) }) { Text("I didn’t do it this time") }
            }
        },
        confirmButton = { TextButton(onClick = onLater) { Text("Later") } },
    )
}

@androidx.compose.runtime.Composable
private fun WeeklyReviewDialog(
    summary: RoutineWeeklySummary,
    onDismiss: () -> Unit,
    onAdjust: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Make next week easier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${summary.completed} completed · ${summary.missed} missed · ${summary.skipped} skipped")
                Text("Choose one small adjustment, or keep your plan as it is. You don’t need to make up missed sessions.")
            }
        },
        confirmButton = { TextButton(onClick = onAdjust) { Text("Adjust a routine") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep my plan") } },
    )
}

@androidx.compose.runtime.Composable
private fun RoutineRecoveryDialog(
    routine: FocusRoutine,
    onKeep: () -> Unit,
    onEdit: () -> Unit,
    onPause: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("No need to catch up") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${routine.name} can start again at its next scheduled time.")
                if (routine.fallbackPlan.isNotBlank()) Text("Your fallback: ${routine.fallbackPlan}")
                Text("Keep the plan, make it easier, or pause it for now.")
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Make it easier") } },
        dismissButton = {
            Row {
                TextButton(onClick = onPause) { Text("Pause") }
                TextButton(onClick = onKeep) { Text("Keep plan") }
            }
        },
    )
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
        Text("Small version: ${routine.minimumAction.ifBlank { routine.name }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var minimumAction by remember(initial?.id) { mutableStateOf(initial?.minimumAction.orEmpty()) }
    var normalAction by remember(initial?.id) { mutableStateOf(initial?.normalAction.orEmpty()) }
    var extraAction by remember(initial?.id) { mutableStateOf(initial?.extraAction.orEmpty()) }
    var fallbackPlan by remember(initial?.id) { mutableStateOf(initial?.fallbackPlan.orEmpty()) }
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
    val canSave = name.isNotBlank() && minimumAction.isNotBlank() && parsedTime != null && days.isNotEmpty() && selectedApps.isNotEmpty()

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
                Text("Make it easy to begin", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = minimumAction,
                    onValueChange = { minimumAction = it.take(100) },
                    label = { Text("Smallest version") },
                    placeholder = { Text("Read one page") },
                    supportingText = { Text("This still counts as doing your routine.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = normalAction,
                    onValueChange = { normalAction = it.take(100) },
                    label = { Text("Usual version (optional)") },
                    placeholder = { Text("Read for ten minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = extraAction,
                    onValueChange = { extraAction = it.take(100) },
                    label = { Text("Extra version (optional)") },
                    placeholder = { Text("Continue if I feel like it") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = fallbackPlan,
                    onValueChange = { fallbackPlan = it.take(160) },
                    label = { Text("If my plan gets disrupted (optional)") },
                    placeholder = { Text("If I get home late, read one page before bed") },
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
                if (!canSave) Text("Add a name, smallest action, valid time, at least one day, and one app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
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
                            minimumAction = minimumAction.trim(),
                            normalAction = normalAction.trim(),
                            extraAction = extraAction.trim(),
                            fallbackPlan = fallbackPlan.trim(),
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
