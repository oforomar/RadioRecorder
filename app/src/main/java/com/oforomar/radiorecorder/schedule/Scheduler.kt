package com.oforomar.radiorecorder.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.model.ALL_DAYS
import com.oforomar.radiorecorder.model.Program
import com.oforomar.radiorecorder.record.RecorderState
import com.oforomar.radiorecorder.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One alarm per program, always pointing at that program's next airing.
 *
 * Times are stored as wall-clock (e.g. 21:00) and turned into an instant only when
 * scheduling, using the phone's own time zone — so Egypt's summer-time switch and any
 * change of the phone's clock are handled by recomputing, never by stored timestamps.
 */
object Scheduler {
    const val ACTION_FIRE = "com.oforomar.radiorecorder.FIRE"
    const val EXTRA_ID = "id"
    const val EXTRA_START = "start"
    const val EXTRA_END = "end"
    const val EXTRA_AIR_DATE = "air_date"

    data class Occurrence(val airDate: LocalDate, val startAt: Long, val endAt: Long)

    /**
     * The next airing of [p] after [from].
     * With [catchUp], an airing that is already under way (and wasn't recorded yet) counts too,
     * so a reboot or a just-added program still records the rest of what is on air now.
     */
    fun nextOccurrence(
        p: Program,
        from: Long,
        catchUp: Boolean,
        lead: Int = Repo.leadMin,
        trail: Int = Repo.trailMin,
        handledUntil: Long = Repo.handledUntil(p.id),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Occurrence? {
        if (!p.enabled || (p.days and ALL_DAYS) == 0) return null
        val today = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        for (offset in -1L..8L) {
            val day = today.plusDays(offset)
            if (!p.runsOn(day.dayOfWeek)) continue
            val air = ZonedDateTime.of(day, LocalTime.of(p.hour, p.minute), zone)
            val start = air.minusMinutes(lead.toLong()).toInstant().toEpochMilli()
            val end = air.plusMinutes((p.durationMin + trail).toLong()).toInstant().toEpochMilli()
            if (start > from) return Occurrence(day, start, end)
            if (catchUp && end - from > 60_000 && end > handledUntil) return Occurrence(day, start, end)
        }
        return null
    }

    fun canScheduleExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** (Re)arms the alarm for [p]. Returns the occurrence it is armed for, or null if none. */
    fun schedule(ctx: Context, p: Program, catchUp: Boolean, from: Long = System.currentTimeMillis()): Occurrence? {
        val occ = nextOccurrence(p, from, catchUp && !RecorderState.isActive(p.id))
        if (occ == null) {
            cancel(ctx, p.id)
            return null
        }
        val now = System.currentTimeMillis()
        val triggerAt = maxOf(occ.startAt, now + 2_000)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val operation = firePendingIntent(ctx, p.id, occ)
        try {
            if (canScheduleExact(ctx)) {
                // The alarm-clock type is the one Android treats as most important: it fires
                // through Doze and lets us start the recorder from the background.
                val show = PendingIntent.getActivity(
                    ctx, 0, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), operation)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
        return occ
    }

    fun rescheduleAll(ctx: Context, catchUp: Boolean) {
        Repo.programs.value.forEach { schedule(ctx, it, catchUp) }
    }

    fun cancel(ctx: Context, programId: Long) {
        val pi = PendingIntent.getBroadcast(
            ctx, programId.toInt(), fireIntent(ctx, programId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    private fun fireIntent(ctx: Context, programId: Long) =
        Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("radiorecorder://program/$programId"))

    private fun firePendingIntent(ctx: Context, programId: Long, occ: Occurrence): PendingIntent {
        val intent = fireIntent(ctx, programId)
            .putExtra(EXTRA_ID, programId)
            .putExtra(EXTRA_START, occ.startAt)
            .putExtra(EXTRA_END, occ.endAt)
            .putExtra(EXTRA_AIR_DATE, occ.airDate.toEpochDay())
        return PendingIntent.getBroadcast(
            ctx, programId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
