package com.oforomar.radiorecorder.record

import android.app.ForegroundServiceStartNotAllowedException
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.model.RecStatus
import com.oforomar.radiorecorder.schedule.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Foreground service that runs every recording currently in progress (programs may overlap).
 * All bookkeeping happens on the main thread; the byte copying happens on IO threads.
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val sessions = LinkedHashMap<Long, Session>()
    private var ticker: Job? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                handleStart(intent)
                goForeground()
            }
            ACTION_STOP -> handleStop(intent.getLongExtra(EXTRA_ID, STOP_ALL))
        }
        if (sessions.isEmpty()) finish()
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val req = RecordRequest(
            programId = intent.getLongExtra(EXTRA_ID, TEST_ID),
            name = intent.getStringExtra(EXTRA_NAME) ?: TEST_NAME,
            endAt = intent.getLongExtra(EXTRA_END, System.currentTimeMillis() + 15_000),
            airDate = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_AIR_DATE, LocalDate.now().toEpochDay())),
            test = intent.getBooleanExtra(EXTRA_TEST, false),
            folderUri = intent.getStringExtra(EXTRA_FOLDER).orEmpty(),
        )
        // Same program already recording (e.g. its time was moved later): just extend it.
        sessions[req.programId]?.let { existing ->
            if (req.endAt > existing.endAt) existing.endAt = req.endAt
            return
        }
        if (req.test) RecorderState.clearTest()

        val session = Session(req)
        sessions[req.programId] = session
        publish()

        scope.launch {
            val outcome = withContext(Dispatchers.IO) { StreamRecorder(applicationContext, client).run(session) }
            onFinished(session, outcome)
        }
        // Ends the recording on time even if the stream stalls mid-read.
        scope.launch {
            while (sessions[req.programId] === session) {
                val left = session.endAt - System.currentTimeMillis()
                if (left <= 0) {
                    session.call?.cancel()
                    break
                }
                delay(minOf(left, 30_000L))
            }
        }
        if (ticker?.isActive != true) {
            ticker = scope.launch {
                while (sessions.isNotEmpty()) {
                    publish()
                    updateNotification()
                    delay(5_000)
                }
            }
        }
    }

    private fun handleStop(programId: Long) {
        val targets = if (programId == STOP_ALL) sessions.values.toList() else listOfNotNull(sessions[programId])
        targets.forEach { it.stop() }
    }

    private fun onFinished(session: Session, outcome: StreamRecorder.Outcome) {
        val req = session.req
        sessions.remove(req.programId)
        val rec = outcome.recording

        if (req.test) {
            RecorderState.setTest(
                RecorderState.TestResult(
                    ok = rec.status != RecStatus.FAILED,
                    message = outcome.reason,
                    uri = rec.uri,
                ),
            )
        } else {
            // Don't let a catch-up restart this same airing.
            Repo.setHandledUntil(req.programId, session.endAt)
            if (rec.status == RecStatus.FAILED) {
                Repo.addRecording(rec)
                Notifier.failed(this, req.programId, req.name, outcome.reason)
            } else {
                Notifier.done(this, rec)
            }
            Repo.program(req.programId)?.let { Scheduler.schedule(this, it, catchUp = false) }
        }

        publish()
        if (sessions.isEmpty()) finish() else updateNotification()
    }

    private fun publish() {
        RecorderState.set(
            sessions.values.associate { s ->
                s.req.programId to RecorderState.Active(
                    programId = s.req.programId,
                    name = s.req.name,
                    endAt = s.endAt,
                    bytes = s.bytes,
                    connected = s.connected,
                )
            },
        )
    }

    private fun goForeground() {
        val n = Notifier.ongoing(this, RecorderState.active.value.values)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(Notifier.FOREGROUND_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.FOREGROUND_ID, n)
        }
    }

    private fun updateNotification() {
        if (sessions.isEmpty()) return
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(Notifier.FOREGROUND_ID, Notifier.ongoing(this, RecorderState.active.value.values))
        }
    }

    private fun finish() {
        ticker?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        // Stopping the calls lets each recorder thread close and publish its file.
        sessions.values.forEach { it.stop() }
        sessions.clear()
        RecorderState.set(emptyMap())
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.oforomar.radiorecorder.START"
        const val ACTION_STOP = "com.oforomar.radiorecorder.STOP"
        const val EXTRA_ID = "id"
        const val EXTRA_NAME = "name"
        const val EXTRA_END = "end"
        const val EXTRA_AIR_DATE = "air_date"
        const val EXTRA_TEST = "test"
        const val EXTRA_FOLDER = "folder"
        const val STOP_ALL = Long.MIN_VALUE

        fun start(ctx: Context, req: RecordRequest) {
            val intent = Intent(ctx, RecorderService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, req.programId)
                .putExtra(EXTRA_NAME, req.name)
                .putExtra(EXTRA_END, req.endAt)
                .putExtra(EXTRA_AIR_DATE, req.airDate.toEpochDay())
                .putExtra(EXTRA_TEST, req.test)
                .putExtra(EXTRA_FOLDER, req.folderUri)
            try {
                ContextCompat.startForegroundService(ctx, intent)
            } catch (e: Exception) {
                val blocked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e is ForegroundServiceStartNotAllowedException
                Notifier.failed(
                    ctx, req.programId, req.name,
                    if (blocked) "منع الهاتف التطبيق من البدء في الخلفية. افتح التطبيق واتبع خطوات «لكي يعمل التسجيل في موعده»."
                    else "تعذّر بدء التسجيل: ${e.message}",
                )
            }
        }

        fun startTest(ctx: Context) = start(
            ctx,
            RecordRequest(
                programId = TEST_ID, name = TEST_NAME,
                endAt = System.currentTimeMillis() + 15_000,
                airDate = LocalDate.now(), test = true,
            ),
        )

        fun stop(ctx: Context, programId: Long) {
            runCatching { ctx.startService(stopIntent(ctx, programId)) }
        }

        fun stopIntent(ctx: Context, programId: Long): Intent =
            Intent(ctx, RecorderService::class.java)
                .setAction(ACTION_STOP)
                .setData(Uri.parse("radiorecorder://stop/$programId"))
                .putExtra(EXTRA_ID, programId)
    }
}
