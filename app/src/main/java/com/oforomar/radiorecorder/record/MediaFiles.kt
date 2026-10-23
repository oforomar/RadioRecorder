package com.oforomar.radiorecorder.record

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.oforomar.radiorecorder.data.Repo
import com.oforomar.radiorecorder.util.Ar
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/**
 * Where recordings are written.
 *
 * Each program may have its own folder, chosen with Android's folder picker (Storage Access
 * Framework); the app keeps a persistent permission for it. Without one — or if the chosen folder
 * was deleted or its permission revoked — recordings go to the shared Music folder:
 *   Music/إذاعة القرآن/<program name>/<program name> 2026-10-18.mp3
 * Either way the files are ordinary MP3s visible to any file manager or player.
 */
object MediaFiles {
    const val ROOT = "إذاعة القرآن"
    const val ARTIST = "إذاعة القرآن الكريم"

    /** [fellBack] is true when a chosen folder was unavailable and the default was used instead. */
    data class Created(val uri: Uri, val fileName: String, val fellBack: Boolean = false)

    fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().ifEmpty { "برنامج" }.take(80)

    fun fileName(programName: String, airDate: LocalDate): String =
        "${safeName(programName)} ${airDate.format(Ar.fileDate)}.mp3"

    fun create(ctx: Context, programName: String, airDate: LocalDate, folderUri: String = ""): Created {
        val name = fileName(programName, airDate)
        if (folderUri.isNotEmpty()) {
            runCatching { return createInFolder(ctx, Uri.parse(folderUri), name) }
            return createInMusic(ctx, programName, name).copy(fellBack = true)
        }
        return createInMusic(ctx, programName, name)
    }

    private fun createInFolder(ctx: Context, tree: Uri, fileName: String): Created {
        val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val uri = DocumentsContract.createDocument(ctx.contentResolver, dir, "audio/mpeg", fileName)
            ?: throw IllegalStateException("createDocument returned null")
        // The provider renames on a clash ("… (1).mp3"); keep the real name.
        val actual = runCatching {
            ctx.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return Created(uri, actual ?: fileName)
    }

    private fun createInMusic(ctx: Context, programName: String, fileName: String): Created {
        val folder = safeName(programName)
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

    private fun isMediaStore(uri: Uri) = uri.authority == MediaStore.AUTHORITY

    /** Makes a Music-folder file visible to other apps (it is hidden while being written). */
    fun publish(ctx: Context, uri: Uri) {
        if (!isMediaStore(uri)) return
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        runCatching { ctx.contentResolver.update(uri, values, null, null) }
    }

    fun delete(ctx: Context, uri: Uri): Boolean = runCatching {
        if (DocumentsContract.isDocumentUri(ctx, uri)) DocumentsContract.deleteDocument(ctx.contentResolver, uri)
        else ctx.contentResolver.delete(uri, null, null) > 0
    }.getOrDefault(false)

    fun freeBytes(): Long =
        runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }
            .getOrDefault(Long.MAX_VALUE)
}

/** Folders picked by the user for programs: permissions, availability and readable names. */
object Folders {
    const val PROVIDER = "com.android.externalstorage.documents"

    /** Folder picker starts in Music when nothing is chosen yet. */
    val musicInitialUri: Uri = DocumentsContract.buildDocumentUri(PROVIDER, "primary:${Environment.DIRECTORY_MUSIC}")

    /** Only real folders on the phone or SD card — not cloud providers, which can't take a live recording. */
    fun isSupported(tree: Uri) = tree.authority == PROVIDER

    fun takePermission(ctx: Context, tree: Uri): Boolean = runCatching {
        ctx.contentResolver.takePersistableUriPermission(
            tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        true
    }.getOrDefault(false)

    /** Still have write access, and the folder still exists. */
    fun isAvailable(ctx: Context, folderUri: String): Boolean {
        if (folderUri.isEmpty()) return true
        val tree = Uri.parse(folderUri)
        val granted = ctx.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }
        if (!granted) return false
        return runCatching {
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            ctx.contentResolver.query(dir, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    /** "ذاكرة الهاتف › Music › قرآن" */
    fun label(folderUri: String): String = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(Uri.parse(folderUri))
        val volume = docId.substringBefore(':')
        val path = docId.substringAfter(':', "")
        val root = if (volume.equals("primary", ignoreCase = true)) "ذاكرة الهاتف" else "بطاقة الذاكرة"
        (listOf(root) + path.split('/').filter { it.isNotBlank() }).joinToString(" › ")
    }.getOrDefault("مجلد مختار")

    fun defaultLabel(programName: String) =
        "الموسيقى › ${MediaFiles.ROOT} › ${MediaFiles.safeName(programName)}"

    fun labelFor(programName: String, folderUri: String) =
        if (folderUri.isEmpty()) defaultLabel(programName) else label(folderUri)

    /**
     * Gives back folder permissions nothing uses any more — no program saves there and no listed
     * recording lives there (playing or deleting a recording needs its folder's permission).
     */
    fun releaseUnused(ctx: Context) {
        val programs = Repo.programs.value.map { it.folderUri }.filter { it.isNotEmpty() }.toSet()
        val recordingUris = Repo.recordings.value.map { it.uri }
        ctx.contentResolver.persistedUriPermissions.forEach { perm ->
            val tree = perm.uri.toString()
            val used = tree in programs || recordingUris.any { it.startsWith("$tree/document/") }
            if (!used) {
                runCatching {
                    ctx.contentResolver.releasePersistableUriPermission(
                        perm.uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
        }
    }
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
