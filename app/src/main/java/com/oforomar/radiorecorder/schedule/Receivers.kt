package com.oforomar.radiorecorder.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.record.Notifier
import com.oforomar.radiorecorder.record.RecordRequest
import com.oforomar.radiorecorder.record.RecorderService
import java.time.LocalDate

/** Fires at a program's start time: arms the next airing, then starts recording this one. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Scheduler.ACTION_FIRE) return
        Repo.init(ctx)
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        val start = intent.getLongExtra(Scheduler.EXTRA_START, 0L)
        val end = intent.getLongExtra(Scheduler.EXTRA_END, 0L)
        val airDate = LocalDate.ofEpochDay(intent.getLongExtra(Scheduler.EXTRA_AIR_DATE, LocalDate.now().toEpochDay()))
        val program = Repo.program(id) ?: return

        // Arm the following airing first, so one failure never breaks the chain.
        Scheduler.schedule(ctx, program, catchUp = false, from = maxOf(start, System.currentTimeMillis()) + 1_000)

        if (!program.enabled) return
        if (end - System.currentTimeMillis() < 60_000) {
            Notifier.missed(ctx, program.name)
            return
        }
        RecorderService.start(
            ctx,
            RecordRequest(programId = program.id, name = program.name, endAt = end, airDate = airDate, test = false),
        )
    }
}

/** Reboot, app update, clock or time-zone change, exact-alarm permission granted: re-arm everything. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        Scheduler.rescheduleAll(ctx, catchUp = true)
    }
}
