package com.oforomar.radiorecorder.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.record.Notifier
import com.oforomar.radiorecorder.schedule.Scheduler

sealed interface Screen {
    data object Home : Screen
    data class Edit(val programId: Long?) : Screen
    data class Recordings(val programId: Long) : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private val openProgram = mutableStateOf<Long?>(null)
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        enableEdgeToEdge()
        readIntent(intent)
        setContent {
            RadioTheme {
                AppRoot(openProgram, resumeTick.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
        // Cheap and safe: re-arm every alarm, and pick up anything that should be on air now.
        Scheduler.rescheduleAll(this, catchUp = true)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getLongExtra(Notifier.EXTRA_OPEN_PROGRAM, -1L) ?: -1L
        if (id >= 0) openProgram.value = id
    }
}

@Composable
private fun AppRoot(openProgram: MutableState<Long?>, resumeTick: Int) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    LaunchedEffect(openProgram.value) {
        openProgram.value?.let {
            screen = Screen.Recordings(it)
            openProgram.value = null
        }
    }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            resumeTick = resumeTick,
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it) },
            onRecordings = { screen = Screen.Recordings(it) },
            onSettings = { screen = Screen.Settings },
        )
        is Screen.Edit -> EditorScreen(programId = s.programId, onDone = { screen = Screen.Home })
        is Screen.Recordings -> RecordingsScreen(programId = s.programId, onBack = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
    }
}
