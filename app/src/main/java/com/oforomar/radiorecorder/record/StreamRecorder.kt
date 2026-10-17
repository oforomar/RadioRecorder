package com.oforomar.radiorecorder.record

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.model.RecStatus
import com.oforomar.radiorecorder.model.Recording
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/** One recording in progress. Fields touched from several threads are volatile. */
class Session(val req: RecordRequest) {
    @Volatile var endAt: Long = req.endAt
    @Volatile var stopped: Boolean = false
    @Volatile var call: Call? = null
    @Volatile var bytes: Long = 0
    @Volatile var connected: Boolean = false

    fun stop() {
        stopped = true
        call?.cancel()
    }
}

/**
 * Copies the live MP3 stream byte-for-byte into the file — no decoding, no microphone,
 * almost no CPU. If the connection drops it reconnects (re-requesting the base URL, because
 * Radiojar redirects to a short-lived tokenised URL) and keeps appending to the same file,
 * which stays playable because MP3 frames simply concatenate.
 */
class StreamRecorder(private val ctx: Context, private val client: OkHttpClient) {

    data class Outcome(val recording: Recording, val reason: String)

    @SuppressLint("WakelockTimeout")
    fun run(s: Session): Outcome {
        val req = s.req
        val startedAt = System.currentTimeMillis()

        fun failure(reason: String) = Outcome(
            Recording(
                id = Repo.newId(), programId = req.programId, programName = req.name,
                uri = "", fileName = "", startedAt = startedAt, endedAt = System.currentTimeMillis(),
                bytes = 0, gapSec = 0, status = RecStatus.FAILED, error = reason,
            ),
            reason,
        )

        if (MediaFiles.freeBytes() < 50L * 1024 * 1024) return failure("لا توجد مساحة كافية على الهاتف.")

        val created = try {
            MediaFiles.create(ctx, if (req.test) TEST_NAME else req.name, req.airDate)
        } catch (e: Exception) {
            return failure("تعذّر إنشاء ملف التسجيل.")
        }
        val uriString = created.uri.toString()
        val recId = Repo.newId()
        Repo.markInProgress(
            Recording(
                id = recId, programId = req.programId, programName = req.name, uri = uriString,
                fileName = created.fileName, startedAt = startedAt, endedAt = startedAt,
                bytes = 0, gapSec = 0, status = RecStatus.PARTIAL,
            ),
        )

        val power = ctx.getSystemService(PowerManager::class.java)
        val wake = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioRecorder:rec${req.programId}").apply {
            setReferenceCounted(false)
            acquire(maxOf(60_000L, s.endAt - startedAt + 15 * 60_000L))
        }
        @Suppress("DEPRECATION")
        val wifi = ctx.applicationContext.getSystemService(WifiManager::class.java)
            ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RadioRecorder:wifi${req.programId}")
            ?.apply { setReferenceCounted(false); acquire() }

        var gapMs = 0L
        var disconnectedAt: Long? = null
        var lastError = "تعذّر الاتصال بالإذاعة."
        var lastDetail = ""
        var attempt = 0
        val url = Repo.streamUrl

        try {
            val raw = ctx.contentResolver.openOutputStream(created.uri, "w")
                ?: throw IOException("cannot open output")
            raw.use {
                val out = BufferedOutputStream(raw, 64 * 1024)
                val year = req.airDate.year.toString()
                val title = if (req.test) "$TEST_NAME ${req.airDate}" else "${req.name} ${req.airDate}"
                out.write(Id3.tag(title = title, album = req.name, artist = MediaFiles.ARTIST, year = year))

                while (!s.stopped && System.currentTimeMillis() < s.endAt) {
                    val attemptStart = System.currentTimeMillis()
                    var gotData = false
                    val call = client.newCall(
                        Request.Builder().url(url)
                            .header("User-Agent", "RadioRecorder/1.0 (Android)")
                            .build(),
                    )
                    s.call = call
                    try {
                        call.execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val type = resp.body?.contentType()?.toString().orEmpty()
                            if (type.startsWith("text/") || type.contains("mpegurl", ignoreCase = true)) {
                                throw IOException("unsupported content type $type")
                            }
                            val input = resp.body?.byteStream() ?: throw IOException("empty body")
                            val buf = ByteArray(16 * 1024)
                            while (!s.stopped && System.currentTimeMillis() < s.endAt) {
                                val n = input.read(buf)
                                if (n < 0) break
                                if (n == 0) continue
                                if (!gotData) {
                                    gotData = true
                                    s.connected = true
                                    attempt = 0
                                    disconnectedAt?.let { gapMs += System.currentTimeMillis() - it }
                                    disconnectedAt = null
                                }
                                out.write(buf, 0, n)
                                s.bytes += n
                            }
                        }
                    } catch (e: IOException) {
                        lastError = describe(e)
                        lastDetail = "${e.javaClass.simpleName}: ${e.message}"
                    } finally {
                        s.call = null
                        s.connected = false
                    }
                    out.flush()

                    if (s.stopped || System.currentTimeMillis() >= s.endAt) break
                    if (disconnectedAt == null) {
                        disconnectedAt = if (gotData) System.currentTimeMillis() else attemptStart
                    }
                    val wait = BACKOFF_MS[minOf(attempt, BACKOFF_MS.lastIndex)]
                    attempt++
                    sleepUntil(s, minOf(System.currentTimeMillis() + wait, s.endAt))
                }
                out.flush()
            }
        } catch (e: Exception) {
            lastError = if (e is IOException) describe(e) else "خطأ أثناء الكتابة."
            lastDetail = "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            runCatching { wifi?.release() }
            runCatching { wake.release() }
        }

        val endedAt = System.currentTimeMillis()
        disconnectedAt?.let { gapMs += minOf(endedAt, s.endAt) - it }

        if (s.bytes < MIN_BYTES) {
            MediaFiles.delete(ctx, created.uri)
            Repo.clearInProgress(uriString)
            val reason = when {
                s.stopped -> "أُوقف قبل أن يُسجَّل شيء."
                lastDetail.isNotEmpty() -> "$lastError\n\n($lastDetail)"
                else -> lastError
            }
            val failed = failure(reason).recording.copy(id = recId)
            return Outcome(failed, reason)
        }

        MediaFiles.publish(ctx, created.uri)
        val status = when {
            s.stopped -> RecStatus.STOPPED
            gapMs > 15_000 -> RecStatus.PARTIAL
            else -> RecStatus.OK
        }
        val rec = Recording(
            id = recId, programId = req.programId, programName = req.name, uri = uriString,
            fileName = created.fileName, startedAt = startedAt, endedAt = endedAt,
            bytes = s.bytes, gapSec = (gapMs / 1000).toInt(), status = status,
            error = if (status == RecStatus.PARTIAL) lastError else "",
        )
        Repo.addRecording(rec)
        Repo.clearInProgress(uriString)
        return Outcome(rec, "")
    }

    private fun sleepUntil(s: Session, until: Long) {
        while (!s.stopped) {
            val left = until - System.currentTimeMillis()
            if (left <= 0) return
            try {
                Thread.sleep(minOf(left, 500L))
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun describe(e: IOException): String = when {
        e is UnknownHostException -> "لا يوجد اتصال بالإنترنت."
        e is SocketTimeoutException -> "الاتصال بالإذاعة بطيء أو منقطع."
        e is UnknownServiceException -> "الهاتف منع الاتصال برابط البث (رابط غير آمن)."
        e is SSLException -> "تعذّر الاتصال الآمن بخادم الإذاعة."
        e.message?.startsWith("HTTP") == true -> "رفض خادم الإذاعة الطلب (${e.message})."
        e.message?.startsWith("unsupported") == true -> "رابط البث لا يعيد صوتاً. راجع الرابط في الإعدادات."
        else -> "انقطع الاتصال بالإذاعة."
    }

    companion object {
        private val BACKOFF_MS = longArrayOf(1_000, 2_000, 5_000, 10_000)
        private const val MIN_BYTES = 32L * 1024
    }
}
