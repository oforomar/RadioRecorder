package com.oforomar.radiorecorder

import android.app.Application
import android.net.Uri
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.model.RecStatus
import com.oforomar.radiorecorder.record.Folders
import com.oforomar.radiorecorder.record.MediaFiles
import com.oforomar.radiorecorder.record.Notifier

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        Notifier.createChannels(this)
        recoverInterruptedRecordings()
        // Drop folder permissions left over from a folder picked but never saved.
        Folders.releaseUnused(this)
    }

    /**
     * If the process was killed mid-recording, the file is still hidden (pending).
     * A fresh process has no recordings running, so publish whatever was saved.
     */
    private fun recoverInterruptedRecordings() {
        Repo.takeInProgress().forEach { r ->
            val uri = Uri.parse(r.uri)
            MediaFiles.publish(this, uri)
            Repo.addRecording(r.copy(status = RecStatus.PARTIAL, error = "توقف التسجيل فجأة (أُغلق التطبيق أو الهاتف)."))
        }
    }
}
