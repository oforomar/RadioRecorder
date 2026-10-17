package com.oforomar.radiorecorder.data

import android.content.Context
import android.content.SharedPreferences
import android.util.AtomicFile
import com.oforomar.radiorecorder.model.Program
import com.oforomar.radiorecorder.model.Recording
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * All app state lives here: programs and recordings as small JSON files,
 * settings in SharedPreferences. A handful of programs never needs a database.
 */
object Repo {
    const val DEFAULT_STREAM_URL = "https://stream.radiojar.com/8s5u5tpdtwzuv"
    private const val MAX_RECORDINGS = 1000

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private val lock = Any()

    private val _programs = MutableStateFlow<List<Program>>(emptyList())
    val programs: StateFlow<List<Program>> = _programs

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _programs.value = readArray("programs.json").map { Program.fromJson(it) }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
        _recordings.value = readArray("recordings.json").map { Recording.fromJson(it) }
            .sortedByDescending { it.startedAt }
    }

    // ---------- programs ----------

    fun program(id: Long): Program? = _programs.value.firstOrNull { it.id == id }

    fun upsert(p: Program): Program = synchronized(lock) {
        val saved = if (p.id == 0L) p.copy(id = newId()) else p
        val list = _programs.value.filterNot { it.id == saved.id } + saved
        _programs.value = list.sortedWith(compareBy({ it.hour }, { it.minute }))
        writeArray("programs.json", _programs.value.map { it.toJson() })
        saved
    }

    fun deleteProgram(id: Long) = synchronized(lock) {
        _programs.value = _programs.value.filterNot { it.id == id }
        writeArray("programs.json", _programs.value.map { it.toJson() })
        prefs.edit().remove("handled_$id").apply()
    }

    // ---------- recordings ----------

    fun addRecording(r: Recording) = synchronized(lock) {
        _recordings.value = (listOf(r) + _recordings.value.filterNot { it.id == r.id })
            .sortedByDescending { it.startedAt }
            .take(MAX_RECORDINGS)
        writeArray("recordings.json", _recordings.value.map { it.toJson() })
    }

    fun deleteRecording(id: Long) = synchronized(lock) {
        _recordings.value = _recordings.value.filterNot { it.id == id }
        writeArray("recordings.json", _recordings.value.map { it.toJson() })
    }

    // ---------- in-progress files (to recover recordings if the process is killed) ----------

    fun markInProgress(r: Recording) = synchronized(lock) {
        val arr = inProgressArray()
        arr.put(r.toJson())
        prefs.edit().putString("in_progress", arr.toString()).commit()
    }

    fun clearInProgress(uri: String) = synchronized(lock) {
        val arr = inProgressArray()
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("uri") != uri) kept.put(o)
        }
        prefs.edit().putString("in_progress", kept.toString()).commit()
    }

    fun takeInProgress(): List<Recording> = synchronized(lock) {
        val arr = inProgressArray()
        prefs.edit().remove("in_progress").commit()
        (0 until arr.length()).map { Recording.fromJson(arr.getJSONObject(it)) }
    }

    private fun inProgressArray(): JSONArray =
        runCatching { JSONArray(prefs.getString("in_progress", "[]")) }.getOrDefault(JSONArray())

    // ---------- settings ----------

    var streamUrl: String
        get() = prefs.getString("stream_url", null)?.takeIf { it.isNotBlank() } ?: DEFAULT_STREAM_URL
        set(v) = prefs.edit().putString("stream_url", v.trim()).apply()

    /** Minutes to start before the program's time. */
    var leadMin: Int
        get() = prefs.getInt("lead_min", 1)
        set(v) = prefs.edit().putInt("lead_min", v).apply()

    /** Minutes to keep recording after the program's end. */
    var trailMin: Int
        get() = prefs.getInt("trail_min", 2)
        set(v) = prefs.edit().putInt("trail_min", v).apply()

    var autostartAcknowledged: Boolean
        get() = prefs.getBoolean("autostart_ack", false)
        set(v) = prefs.edit().putBoolean("autostart_ack", v).apply()

    /** End time of the last occurrence already recorded (or stopped by the user) for a program. */
    fun handledUntil(programId: Long): Long = prefs.getLong("handled_$programId", 0L)

    fun setHandledUntil(programId: Long, endAt: Long) =
        prefs.edit().putLong("handled_$programId", endAt).apply()

    fun newId(): Long = synchronized(lock) {
        val next = prefs.getLong("next_id", 1L)
        prefs.edit().putLong("next_id", next + 1).commit()
        next
    }

    // ---------- files ----------

    private fun readArray(name: String): List<JSONObject> = runCatching {
        val f = AtomicFile(File(app.filesDir, name))
        if (!f.baseFile.exists()) return emptyList()
        val arr = JSONArray(String(f.readFully(), Charsets.UTF_8))
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun writeArray(name: String, items: List<JSONObject>) {
        val f = AtomicFile(File(app.filesDir, name))
        val out = f.startWrite()
        try {
            out.write(JSONArray(items).toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
        }
    }
}
