package com.oforomar.radiorecorder.model

import org.json.JSONObject
import java.time.DayOfWeek

/** Days are a bitmask: bit (DayOfWeek.value - 1), so Monday = bit 0 … Sunday = bit 6. */
const val ALL_DAYS = 0b1111111

fun DayOfWeek.bit(): Int = 1 shl (value - 1)

data class Program(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val durationMin: Int,
    val days: Int = ALL_DAYS,
    val enabled: Boolean = true,
) {
    fun runsOn(day: DayOfWeek): Boolean = (days and day.bit()) != 0

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("hour", hour)
        .put("minute", minute)
        .put("durationMin", durationMin)
        .put("days", days)
        .put("enabled", enabled)

    companion object {
        fun fromJson(o: JSONObject) = Program(
            id = o.getLong("id"),
            name = o.getString("name"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            durationMin = o.getInt("durationMin"),
            days = o.optInt("days", ALL_DAYS),
            enabled = o.optBoolean("enabled", true),
        )
    }
}

enum class RecStatus { OK, PARTIAL, STOPPED, FAILED }

data class Recording(
    val id: Long,
    val programId: Long,
    val programName: String,
    /** content:// uri of the saved file, empty when the recording failed. */
    val uri: String,
    val fileName: String,
    val startedAt: Long,
    val endedAt: Long,
    val bytes: Long,
    val gapSec: Int,
    val status: RecStatus,
    val error: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("programId", programId)
        .put("programName", programName)
        .put("uri", uri)
        .put("fileName", fileName)
        .put("startedAt", startedAt)
        .put("endedAt", endedAt)
        .put("bytes", bytes)
        .put("gapSec", gapSec)
        .put("status", status.name)
        .put("error", error)

    companion object {
        fun fromJson(o: JSONObject) = Recording(
            id = o.getLong("id"),
            programId = o.getLong("programId"),
            programName = o.getString("programName"),
            uri = o.optString("uri"),
            fileName = o.optString("fileName"),
            startedAt = o.getLong("startedAt"),
            endedAt = o.getLong("endedAt"),
            bytes = o.optLong("bytes"),
            gapSec = o.optInt("gapSec"),
            status = runCatching { RecStatus.valueOf(o.getString("status")) }.getOrDefault(RecStatus.OK),
            error = o.optString("error"),
        )
    }
}
