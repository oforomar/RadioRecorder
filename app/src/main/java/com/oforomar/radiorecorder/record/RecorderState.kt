package com.oforomar.radiorecorder.record

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

/** What to record. [programId] is [TEST_ID] for the 15-second test from the home screen. */
data class RecordRequest(
    val programId: Long,
    val name: String,
    val endAt: Long,
    val airDate: LocalDate,
    val test: Boolean,
    /** Folder chosen for the program; empty = default Music folder. */
    val folderUri: String = "",
)

const val TEST_ID = -1L
const val TEST_NAME = "تجربة"

/** Live, in-process view of what the recorder is doing, for the UI and the scheduler. */
object RecorderState {
    data class Active(
        val programId: Long,
        val name: String,
        val endAt: Long,
        val bytes: Long,
        val connected: Boolean,
    )

    data class TestResult(val ok: Boolean, val message: String, val uri: String)

    private val _active = MutableStateFlow<Map<Long, Active>>(emptyMap())
    val active: StateFlow<Map<Long, Active>> = _active

    private val _test = MutableStateFlow<TestResult?>(null)
    val test: StateFlow<TestResult?> = _test

    fun isActive(programId: Long) = _active.value.containsKey(programId)

    internal fun set(map: Map<Long, Active>) {
        _active.value = map
    }

    internal fun setTest(r: TestResult?) {
        _test.value = r
    }

    fun clearTest() = setTest(null)
}
