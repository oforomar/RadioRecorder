package com.oforomar.radiorecorder.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.model.ALL_DAYS
import com.oforomar.radiorecorder.model.Program
import com.oforomar.radiorecorder.model.bit
import com.oforomar.radiorecorder.schedule.Scheduler
import com.oforomar.radiorecorder.util.Ar

/** Add or edit a program: name, time, duration — days are "every day" unless asked otherwise. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(programId: Long?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val existing = remember(programId) { programId?.let { Repo.program(it) } }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var hour by remember { mutableIntStateOf(existing?.hour ?: 21) }
    var minute by remember { mutableIntStateOf(existing?.minute ?: 0) }
    var duration by remember { mutableIntStateOf(existing?.durationMin ?: 60) }
    var days by remember { mutableIntStateOf(existing?.days ?: ALL_DAYS) }
    var specificDays by remember { mutableStateOf(existing != null && existing.days != ALL_DAYS) }
    var showTime by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "برنامج جديد" else "تعديل البرنامج") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // 1. Name
            Column {
                Label("اسم البرنامج")
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    placeholder = { Text("مثال: قرآن الفجر") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = { Text(if (nameError) "اكتب اسماً للبرنامج" else "يُحفظ التسجيل بهذا الاسم") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 2. Time
            Column {
                Label("موعد البرنامج")
                OutlinedButton(
                    onClick = { showTime = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                ) {
                    Text(Ar.time(hour, minute), style = MaterialTheme.typography.displaySmall)
                }
            }

            // 3. Duration
            Column {
                Label("مدة التسجيل")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(
                        onClick = { duration = (duration - 5).coerceAtLeast(5) },
                        modifier = Modifier.size(60.dp),
                    ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
                    Text(
                        Ar.duration(duration),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { duration = (duration + 5).coerceAtMost(600) },
                        modifier = Modifier.size(60.dp),
                    ) { Icon(Icons.Default.Add, contentDescription = "زيادة") }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                        FilterChip(
                            selected = duration == m,
                            onClick = { duration = m },
                            label = { Text(Ar.duration(m)) },
                        )
                    }
                }
            }

            // 4. Days
            Column {
                Label("أيام التسجيل")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = !specificDays, onClick = { specificDays = false })
                    Text("كل يوم", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.size(24.dp))
                    RadioButton(selected = specificDays, onClick = {
                        specificDays = true
                        if (days == ALL_DAYS) days = 0
                    })
                    Text("أيام محددة", style = MaterialTheme.typography.bodyLarge)
                }
                if (specificDays) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ar.week.forEach { d ->
                            val on = (days and d.bit()) != 0
                            FilterChip(
                                selected = on,
                                onClick = { days = days xor d.bit() },
                                label = { Text(Ar.dayName(d)) },
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    val finalDays = if (specificDays) days else ALL_DAYS
                    if (finalDays == 0) {
                        Toast.makeText(ctx, "اختر يوماً واحداً على الأقل", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val saved = Repo.upsert(
                        (existing ?: Program(id = 0, name = "", hour = 0, minute = 0, durationMin = 0)).copy(
                            name = name.trim(),
                            hour = hour,
                            minute = minute,
                            durationMin = duration,
                            days = finalDays,
                        ),
                    )
                    val occ = Scheduler.schedule(ctx, saved, catchUp = true)
                    val msg = when {
                        !saved.enabled -> "تم الحفظ"
                        occ == null -> "تم الحفظ"
                        occ.startAt <= System.currentTimeMillis() + 5_000 -> "البرنامج على الهواء الآن — بدأ التسجيل"
                        else -> "سيُسجَّل ${Ar.relativeDay(occ.airDate)} الساعة ${Ar.time(hour, minute)}"
                    }
                    Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            ) {
                Text("حفظ", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showTime) {
        TimePickerDialog(
            hour = hour,
            minute = minute,
            title = "موعد البرنامج",
            onDismiss = { showTime = false },
            onConfirm = { h, m ->
                hour = h
                minute = m
                showTime = false
            },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
