package com.oforomar.radiorecorder.record

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.oforomar.radiorecorder.util.Ar
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Recordings go to the shared Music folder through MediaStore:
 *   Music/إذاعة القرآن/<program name>/<program name> 2026-10-18.mp3
 * so they show up in any file manager or music player, can be shared on WhatsApp,
 * and survive uninstalling the app.
 */
object MediaFiles {
    const val ROOT = "إذاعة القرآن"
    const val ARTIST = "إذاعة القرآن الكريم"

    data class Created(val uri: Uri, val fileName: String)

    fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().ifEmpty { "برنامج" }.take(80)

    fun create(ctx: Context, programName: String, airDate: LocalDate): Created {
        val folder = safeName(programName)
        val fileName = "$folder ${airDate.format(Ar.fileDate)}.mp3"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$ROOT/$folder")
            put(MediaStore.Audio.Media.TITLE, fileName.removeSuffix(".mp3"))
            put(MediaStore.Audio.Media.ALBUM, folder)
            put(MediaStore.Audio.Media.ARTIST, ARTIST)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = ctx.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        return Created(uri, fileName)
    }

    /** Makes the file visible to other apps (it is hidden while being written). */
    fun publish(ctx: Context, uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        runCatching { ctx.contentResolver.update(uri, values, null, null) }
    }

    fun delete(ctx: Context, uri: Uri): Boolean =
        runCatching { ctx.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    fun freeBytes(): Long =
        runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }
            .getOrDefault(Long.MAX_VALUE)
}

/** A minimal ID3v2.3 tag so players show the program name instead of a file name. */
object Id3 {
    fun tag(title: String, album: String, artist: String, year: String): ByteArray {
        val frames = ByteArrayOutputStream()
        fun text(id: String, value: String) {
            val body = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) + value.toByteArray(Charsets.UTF_16LE)
            frames.write(id.toByteArray(Charsets.US_ASCII))
            frames.write(int32(body.size))
            frames.write(byteArrayOf(0, 0))
            frames.write(body)
        }
        text("TIT2", title)
        text("TALB", album)
        text("TPE1", artist)
        text("TYER", year)
        val f = frames.toByteArray()
        val header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0) + syncSafe(f.size)
        return header + f
    }

    private fun int32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun syncSafe(v: Int) = byteArrayOf(
        ((v ushr 21) and 0x7F).toByte(),
        ((v ushr 14) and 0x7F).toByte(),
        ((v ushr 7) and 0x7F).toByte(),
        (v and 0x7F).toByte(),
    )
}
