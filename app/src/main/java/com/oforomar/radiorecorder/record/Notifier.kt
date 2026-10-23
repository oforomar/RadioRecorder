package com.oforomar.radiorecorder.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.oforomar.radiorecorder.R
import com.oforomar.radiorecorder.model.RecStatus
import com.oforomar.radiorecorder.model.Recording
import com.oforomar.radiorecorder.ui.MainActivity
import com.oforomar.radiorecorder.util.Ar

object Notifier {
    private const val CH_RECORDING = "recording"
    private const val CH_DONE = "done"
    private const val CH_PROBLEM = "problem"
    const val FOREGROUND_ID = 1
    const val EXTRA_OPEN_PROGRAM = "open_program"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_RECORDING, "أثناء التسجيل", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
                NotificationChannel(CH_DONE, "تم الحفظ", NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CH_PROBLEM, "مشكلة في التسجيل", NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    private fun openApp(ctx: Context, programId: Long?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (programId != null && programId >= 0) intent.putExtra(EXTRA_OPEN_PROGRAM, programId)
        return PendingIntent.getActivity(
            ctx, (programId ?: 0L).toInt() + 10_000, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun ongoing(ctx: Context, active: Collection<RecorderState.Active>): Notification {
        val stop = PendingIntent.getService(
            ctx, 1, RecorderService.stopIntent(ctx, RecorderService.STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (active.size) {
            0 -> "جارٍ بدء التسجيل…" to ""
            1 -> active.first().let { a ->
                "جارٍ تسجيل «${a.name}»" to buildString {
                    append("حتى ${Ar.time(a.endAt)}")
                    append(" · ${Ar.size(a.bytes)}")
                    if (!a.connected) append(" · بانتظار الاتصال…")
                }
            }
            else -> "جارٍ تسجيل ${Ar.num(active.size)} برامج" to active.joinToString("، ") { it.name }
        }
        return NotificationCompat.Builder(ctx, CH_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(ctx, active.singleOrNull()?.programId))
            .addAction(0, "إيقاف التسجيل", stop)
            .build()
    }

    fun done(ctx: Context, r: Recording) {
        val text = when (r.status) {
            RecStatus.PARTIAL -> "${Ar.size(r.bytes)} · انقطع الاتصال ${Ar.minutes(maxOf(1, r.gapSec / 60))} تقريباً"
            RecStatus.STOPPED -> "${Ar.size(r.bytes)} · أُوقف يدوياً"
            else -> Ar.size(r.bytes)
        }
        post(
            ctx, r.programId,
            NotificationCompat.Builder(ctx, CH_DONE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("تم حفظ «${r.programName}» ✓")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(if (r.note.isEmpty()) text else "$text\n${r.note}"))
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx, r.programId))
                .build(),
        )
    }

    fun failed(ctx: Context, programId: Long, name: String, reason: String) {
        post(
            ctx, programId,
            NotificationCompat.Builder(ctx, CH_PROBLEM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("تعذّر تسجيل «$name»")
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx, programId))
                .build(),
        )
    }

    fun missed(ctx: Context, name: String) {
        post(
            ctx, -2,
            NotificationCompat.Builder(ctx, CH_PROBLEM)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("فات موعد «$name»")
                .setContentText("كان الهاتف مغلقاً أو نائماً وقت البرنامج.")
                .setAutoCancel(true)
                .setContentIntent(openApp(ctx, null))
                .build(),
        )
    }

    private fun post(ctx: Context, programId: Long, n: Notification) {
        try {
            NotificationManagerCompat.from(ctx).notify(1_000 + programId.toInt(), n)
        } catch (e: SecurityException) {
            // Notifications not allowed; the home screen shows a banner for that.
        }
    }
}
