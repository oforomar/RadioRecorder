package com.oforomar.radiorecorder.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.schedule.Scheduler
import com.oforomar.radiorecorder.util.Ar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf(Repo.streamUrl) }
    var lead by remember { mutableIntStateOf(Repo.leadMin) }
    var trail by remember { mutableIntStateOf(Repo.trailMin) }

    fun save() {
        Repo.streamUrl = url
        Repo.leadMin = lead
        Repo.trailMin = trail
        Scheduler.rescheduleAll(ctx, catchUp = false)
        Toast.makeText(ctx, "تم الحفظ", Toast.LENGTH_SHORT).show()
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Stepper(
                title = "يبدأ التسجيل قبل الموعد بـ",
                value = lead,
                range = 0..10,
                onChange = { lead = it },
            )
            Stepper(
                title = "ويستمر بعد نهاية البرنامج بـ",
                value = trail,
                range = 0..15,
                onChange = { trail = it },
            )
            Text(
                "الدقائق الإضافية تحمي من تأخر البث على الإنترنت ومن البرامج التي تطول عن موعدها.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رابط البث المباشر", style = MaterialTheme.typography.titleMedium)
                Text(
                    "لا تغيّره إلا إذا توقف التسجيل عن العمل وعرفت الرابط الجديد للإذاعة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { url = Repo.DEFAULT_STREAM_URL }) { Text("استعادة الرابط الأصلي") }
            }

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
                Text("حفظ", style = MaterialTheme.typography.titleMedium)
            }

            Text(
                "التسجيلات تُحفظ في: الموسيقى ← إذاعة القرآن ← اسم البرنامج",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stepper(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            FilledTonalIconButton(
                onClick = { onChange((value - 1).coerceIn(range)) },
                modifier = Modifier.size(56.dp),
            ) { Text("−", style = MaterialTheme.typography.headlineSmall) }
            Text(
                if (value == 0) "بدون" else Ar.minutes(value),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            FilledTonalIconButton(
                onClick = { onChange((value + 1).coerceIn(range)) },
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Default.Add, contentDescription = "زيادة") }
        }
    }
}
