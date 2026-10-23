package com.oforomar.radiorecorder.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.model.RecStatus
import com.oforomar.radiorecorder.model.Recording
import com.oforomar.radiorecorder.record.Folders
import com.oforomar.radiorecorder.record.MediaFiles
import com.oforomar.radiorecorder.util.Ar
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingsScreen(programId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val programs by Repo.programs.collectAsState()
    val all by Repo.recordings.collectAsState()
    val list = remember(all, programId) { all.filter { it.programId == programId } }
    val program = programs.firstOrNull { it.id == programId }
    val title = program?.name ?: list.firstOrNull()?.programName ?: "التسجيلات"
    val location = program?.let {
        val ok = Folders.isAvailable(ctx, it.folderUri)
        (if (ok) "تُحفظ في: " else "المجلد المختار غير متاح — تُحفظ مؤقتاً في: ") +
            Folders.labelFor(it.name, if (ok) it.folderUri else "")
    }

    val player = remember { Player(ctx) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(player.playing) {
        while (player.playing) {
            player.tick()
            delay(500)
        }
    }

    var deleteFor by remember { mutableStateOf<Recording?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        if (list.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("لا توجد تسجيلات بعد", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "ستظهر هنا بعد أول موعد للبرنامج.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                location?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            location?.let {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(FolderIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(list, key = { it.id }) { r ->
                RecordingRow(
                    r = r,
                    player = player,
                    onPlay = {
                        if (!player.toggle(r.uri)) {
                            Toast.makeText(ctx, "الملف غير موجود — ربما حُذف من مدير الملفات", Toast.LENGTH_LONG).show()
                        }
                    },
                    onShare = { shareAudio(ctx, r.uri, r.fileName) },
                    onDelete = { deleteFor = r },
                )
            }
        }
    }

    deleteFor?.let { r ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("حذف التسجيل؟") },
            text = { Text(if (r.uri.isEmpty()) "سيُزال من القائمة." else "سيُحذف الملف «${r.fileName}» من الهاتف.") },
            confirmButton = {
                TextButton(onClick = {
                    if (player.currentUri == r.uri) player.release()
                    if (r.uri.isNotEmpty() && !MediaFiles.delete(ctx, Uri.parse(r.uri))) {
                        Toast.makeText(ctx, "أُزيل من القائمة؛ احذف الملف من مدير الملفات إن بقي.", Toast.LENGTH_LONG).show()
                    }
                    Repo.deleteRecording(r.id)
                    Folders.releaseUnused(ctx)
                    deleteFor = null
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun RecordingRow(
    r: Recording,
    player: Player,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val failed = r.status == RecStatus.FAILED
    val isCurrent = player.currentUri == r.uri && r.uri.isNotEmpty()
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!failed) {
                    FilledIconButton(onClick = onPlay, modifier = Modifier.size(56.dp)) {
                        Icon(
                            if (isCurrent && player.playing) PauseIcon else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrent && player.playing) "إيقاف مؤقت" else "تشغيل",
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(Ar.date(r.startedAt), style = MaterialTheme.typography.titleMedium)
                    Text(subtitle(r), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (r.note.isNotEmpty()) {
                        Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    if (r.status == RecStatus.PARTIAL || failed) {
                        Text(
                            if (failed) r.error.ifEmpty { "فشل التسجيل" }
                            else "ناقص: انقطع الاتصال ${Ar.minutes(maxOf(1, r.gapSec / 60))} تقريباً",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
                if (!failed) {
                    IconButton(onClick = onShare, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "مشاركة")
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(52.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف")
                }
            }
            if (isCurrent && player.duration > 0) {
                // Seek bar runs left-to-right like any audio player.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column {
                        Slider(
                            value = player.position.toFloat().coerceIn(0f, player.duration.toFloat()),
                            onValueChange = { player.seekTo(it.toInt()) },
                            valueRange = 0f..player.duration.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(Modifier.fillMaxWidth()) {
                            Text(clock(player.position), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.weight(1f))
                            Text(clock(player.duration), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun subtitle(r: Recording): String {
    val start = Ar.time(r.startedAt)
    if (r.status == RecStatus.FAILED) return "الساعة $start"
    val minutes = ((r.endedAt - r.startedAt) / 60_000).toInt().coerceAtLeast(1)
    val stopped = if (r.status == RecStatus.STOPPED) " · أُوقف يدوياً" else ""
    return "الساعة $start · ${Ar.duration(minutes)} · ${Ar.size(r.bytes)}$stopped"
}
