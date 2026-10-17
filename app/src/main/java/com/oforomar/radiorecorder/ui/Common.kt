package com.oforomar.radiorecorder.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/** Pause and stop glyphs (not in the core icon set). */
val PauseIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Pause") {
        materialPath {
            moveTo(6f, 19f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineTo(6f); close()
            moveTo(14f, 5f); verticalLineToRelative(14f); horizontalLineToRelative(4f); verticalLineTo(5f); close()
        }
    }
}

val StopIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Stop") {
        materialPath {
            moveTo(6f, 6f); horizontalLineToRelative(12f); verticalLineToRelative(12f); horizontalLineTo(6f); close()
        }
    }
}

/** A clock-face time picker in a dialog. The dial is kept left-to-right so numbers sit where people expect. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    hour: Int,
    minute: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}

/** One shared player for the recordings list. */
class Player(private val ctx: Context) {
    var currentUri by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableIntStateOf(0)
        private set
    var duration by mutableIntStateOf(0)
        private set

    private var mp: MediaPlayer? = null

    /** Play/pause [uri]. Returns false if the file can't be played. */
    fun toggle(uri: String): Boolean {
        val current = mp
        if (uri == currentUri && current != null) {
            if (current.isPlaying) {
                current.pause(); playing = false
            } else {
                current.start(); playing = true
            }
            return true
        }
        release()
        return try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(ctx, Uri.parse(uri))
                setOnCompletionListener { playing = false; position = 0 }
                prepare()
                start()
            }
            mp = player
            currentUri = uri
            duration = player.duration.coerceAtLeast(0)
            playing = true
            true
        } catch (e: Exception) {
            release()
            false
        }
    }

    fun seekTo(ms: Int) {
        mp?.seekTo(ms)
        position = ms
    }

    fun tick() {
        mp?.let { runCatching { position = it.currentPosition } }
    }

    fun release() {
        mp?.let { runCatching { it.release() } }
        mp = null
        currentUri = null
        playing = false
        position = 0
        duration = 0
    }
}

fun shareAudio(ctx: Context, uri: String, title: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("audio/mpeg")
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** 3725000 ms → "١:٠٢:٠٥" */
fun clock(ms: Int): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val text = if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
    return com.oforomar.radiorecorder.util.Ar.digits(text)
}
