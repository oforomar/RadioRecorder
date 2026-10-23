package com.oforomar.radiorecorder.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.model.Program
import com.oforomar.radiorecorder.record.Folders
import com.oforomar.radiorecorder.record.RecorderService
import com.oforomar.radiorecorder.record.RecorderState
import com.oforomar.radiorecorder.record.TEST_ID
import com.oforomar.radiorecorder.schedule.Scheduler
import com.oforomar.radiorecorder.util.Ar
import com.oforomar.radiorecorder.util.Health
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    resumeTick: Int,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onRecordings: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val programs by Repo.programs.collectAsState()
    val recordings by Repo.recordings.collectAsState()
    val active by RecorderState.active.collectAsState()
    val testResult by RecorderState.test.collectAsState()

    // Refreshes "next recording" texts as time passes.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(20_000)
            value = System.currentTimeMillis()
        }
    }

    var healthRefresh by remember { mutableIntStateOf(0) }
    val issues = remember(resumeTick, healthRefresh) { Health.issues(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Health.fix(ctx, Health.Issue.NOTIFICATIONS)
        healthRefresh++
    }

    // Programs whose chosen folder was deleted or lost its permission.
    val folderProblems = remember(programs, resumeTick) {
        programs.filter { !Folders.isAvailable(ctx, it.folderUri) }.map { it.id }.toSet()
    }

    var timeEditFor by remember { mutableStateOf<Program?>(null) }
    var deleteFor by remember { mutableStateOf<Program?>(null) }

    fun announce(p: Program) {
        val occ = Scheduler.schedule(ctx, p, catchUp = true)
        val msg = when {
            !p.enabled -> "تم إيقاف «${p.name}»"
            occ == null -> "لا توجد أيام محددة لـ«${p.name}»"
            occ.startAt <= System.currentTimeMillis() + 5_000 -> "البرنامج على الهواء الآن — بدأ التسجيل"
            else -> "سيُسجَّل «${p.name}» ${Ar.relativeDay(occ.airDate)} الساعة ${Ar.time(p.hour, p.minute)}"
        }
        scope.launch { snackbar.showSnackbar(msg) }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("مسجّل إذاعة القرآن", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "الإعدادات") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("إضافة برنامج", style = MaterialTheme.typography.labelLarge) },
                modifier = Modifier.heightIn(min = 64.dp),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (issues.isNotEmpty()) {
                item {
                    HealthCard(
                        issues = issues,
                        onFix = { issue ->
                            if (issue == Health.Issue.NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                Health.fix(ctx, issue)
                            }
                        },
                        onAutostartDone = {
                            Repo.autostartAcknowledged = true
                            healthRefresh++
                        },
                    )
                }
            }

            if (programs.isEmpty()) {
                item { EmptyState() }
            }

            items(programs, key = { it.id }) { p ->
                ProgramCard(
                    p = p,
                    now = now,
                    active = active[p.id],
                    folderProblem = p.id in folderProblems,
                    recordingCount = recordings.count { it.programId == p.id && it.uri.isNotEmpty() },
                    onTimeClick = { timeEditFor = p },
                    onToggle = { enabled ->
                        val updated = Repo.upsert(p.copy(enabled = enabled))
                        announce(updated)
                    },
                    onStop = { RecorderService.stop(ctx, p.id) },
                    onRecordings = { onRecordings(p.id) },
                    onEdit = { onEdit(p.id) },
                    onDelete = { deleteFor = p },
                )
            }

            item {
                TestRow(
                    running = active.containsKey(TEST_ID),
                    onTest = { RecorderService.startTest(ctx) },
                )
            }
        }
    }

    timeEditFor?.let { p ->
        TimePickerDialog(
            hour = p.hour,
            minute = p.minute,
            title = "موعد «${p.name}»",
            onDismiss = { timeEditFor = null },
            onConfirm = { h, m ->
                timeEditFor = null
                val updated = Repo.upsert(p.copy(hour = h, minute = m))
                announce(updated)
            },
        )
    }

    deleteFor?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("حذف «${p.name}»؟") },
            text = { Text("لن يُسجَّل هذا البرنامج بعد الآن. التسجيلات المحفوظة تبقى في مجلد الموسيقى.") },
            confirmButton = {
                TextButton(onClick = {
                    RecorderService.stop(ctx, p.id)
                    Scheduler.cancel(ctx, p.id)
                    Repo.deleteProgram(p.id)
                    Folders.releaseUnused(ctx)
                    deleteFor = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("إلغاء") } },
        )
    }

    testResult?.let { r ->
        TestResultDialog(r = r, onClose = { RecorderState.clearTest() })
    }
}

@Composable
private fun ProgramCard(
    p: Program,
    now: Long,
    active: RecorderState.Active?,
    folderProblem: Boolean,
    recordingCount: Int,
    onTimeClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onStop: () -> Unit,
    onRecordings: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dim = !p.enabled
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active != null) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Switch(checked = p.enabled, onCheckedChange = onToggle)
            }

            // The season-change flow: tap the time, pick the new one, done.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable(onClickLabel = "تغيير الموعد", onClick = onTimeClick)
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    Ar.time(p.hour, p.minute),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "تغيير الموعد",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                "${Ar.duration(p.durationMin)} · ${Ar.days(p.days)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (folderProblem) {
                Text(
                    "مجلد الحفظ المختار لم يعد متاحاً — ستُحفظ في المجلد الافتراضي. اضغط ✎ لاختيار مجلد.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            if (active != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(RecordRed, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (active.connected) "جارٍ التسجيل حتى ${Ar.time(active.endAt)} · ${Ar.size(active.bytes)}"
                        else "جارٍ الاتصال بالإذاعة…",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = RecordRed),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(StopIcon, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("إيقاف التسجيل")
                }
            } else {
                Text(
                    nextText(p, now),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (dim) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = onRecordings,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("التسجيلات (${Ar.num(recordingCount)})")
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "تعديل")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف")
                }
            }
        }
    }
}

private fun nextText(p: Program, now: Long): String {
    if (!p.enabled) return "متوقف — لن يُسجَّل"
    val occ = Scheduler.nextOccurrence(p, now, catchUp = false) ?: return "لا توجد أيام محددة"
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return "التسجيل القادم: ${Ar.relativeDay(occ.airDate, today)} ${Ar.time(p.hour, p.minute)}"
}

@Composable
private fun HealthCard(
    issues: List<Health.Issue>,
    onFix: (Health.Issue) -> Unit,
    onAutostartDone: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("لكي يعمل التسجيل في موعده", style = MaterialTheme.typography.titleMedium)
            }
            issues.forEach { issue ->
                Column {
                    Text(issue.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(issue.why, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onFix(issue) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (issue == Health.Issue.AUTOSTART) "فتح الإعداد" else "إصلاح")
                        }
                        if (issue == Health.Issue.AUTOSTART) {
                            OutlinedButton(onClick = onAutostartDone, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("تم")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("لا توجد برامج بعد", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "اضغط «إضافة برنامج»، واكتب اسمه، واختر موعده.\nسيُسجَّل تلقائياً كل يوم ويُحفظ باسمه في مجلد الموسيقى.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TestRow(running: Boolean, onTest: () -> Unit) {
    OutlinedButton(
        onClick = onTest,
        enabled = !running,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(top = 8.dp),
    ) {
        if (running) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("جارٍ الاختبار… (١٥ ثانية)")
        } else {
            Text("جرّب التسجيل الآن (١٥ ثانية)")
        }
    }
}

@Composable
private fun TestResultDialog(r: RecorderState.TestResult, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val player = remember { Player(ctx) }
    DisposableEffect(Unit) { onDispose { player.release() } }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (r.ok) "التسجيل يعمل ✓" else "تعذّر التسجيل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.ok) {
                    Text("سُجّلت ١٥ ثانية من الإذاعة وحُفظت في:\nالموسيقى ← إذاعة القرآن ← تجربة")
                    Button(
                        onClick = {
                            if (!player.toggle(r.uri)) Toast.makeText(ctx, "تعذّر تشغيل الملف", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        Icon(if (player.playing) PauseIcon else Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (player.playing) "إيقاف مؤقت" else "استمع")
                    }
                } else {
                    Text(r.message)
                    Text(
                        "تأكد من اتصال الإنترنت. إن استمرت المشكلة فقد يكون رابط البث تغيّر — يمكن تعديله من الإعدادات.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("حسناً") } },
    )
}
