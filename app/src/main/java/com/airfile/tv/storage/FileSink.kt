package com.airfile.tv.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Decides where received files go. Preference order:
 *  1. A folder the user picked (system folder picker, ideally on the SD card).
 *  2. The SD card's app directory (`<sd>/Android/data/com.airfile.tv/files/AirFile`).
 *  3. Downloads/AirFile (Android 10+) or app storage (older versions) when there's no SD card.
 */
class FileSink(private val context: Context) {

    /** An in-progress file. Write to [out], then call [finish] on success or [abort] on failure. */
    class Destination(
        val out: OutputStream,
        val finish: () -> String,
        val abort: () -> Unit,
    )

    private val prefs = context.getSharedPreferences("airfile", Context.MODE_PRIVATE)

    fun setPickedFolder(uri: Uri?) {
        prefs.edit().apply {
            if (uri == null) remove(KEY_TREE) else putString(KEY_TREE, uri.toString())
        }.apply()
    }

    private fun pickedDir(): DocumentFile? =
        prefs.getString(KEY_TREE, null)
            ?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
            ?.takeIf { it.exists() && it.canWrite() }

    /** First mounted non-primary external volume (removable SD card or adopted storage). */
    private fun sdCardDir(): File? =
        context.getExternalFilesDirs(null)
            .drop(1)
            .filterNotNull()
            .firstOrNull { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }
            ?.let { File(it, "AirFile") }

    fun describe(): String {
        pickedDir()?.let { return "Saving to the folder you chose: ${it.name ?: "selected folder"}" }
        sdCardDir()?.let { return "Saving to the SD card (AirFile folder)" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "No SD card found. Saving to Downloads/AirFile on this TV"
        } else {
            "No SD card found. Saving to AirFile app storage on this TV"
        }
    }

    fun open(rawName: String): Destination {
        val name = FileNames.sanitize(rawName)
        pickedDir()?.let { return openDocument(it, name) }
        sdCardDir()?.let { return openFile(it, name) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openMediaStore(name)
        } else {
            openFile(File(context.getExternalFilesDir(null) ?: context.filesDir, "AirFile"), name)
        }
    }

    private fun openFile(dir: File, name: String): Destination {
        dir.mkdirs()
        if (!dir.isDirectory) throw IOException("Cannot create folder ${dir.path}")
        val finalName = FileNames.uniqueName(name) { File(dir, it).exists() }
        val target = File(dir, finalName)
        val part = File(dir, "$finalName.part")
        val stream = BufferedOutputStream(FileOutputStream(part), BUFFER)
        return Destination(
            out = stream,
            finish = {
                stream.close()
                if (!part.renameTo(target)) throw IOException("Could not finalize $finalName")
                finalName
            },
            abort = {
                runCatching { stream.close() }
                part.delete()
            },
        )
    }

    private fun openDocument(dir: DocumentFile, name: String): Destination {
        val finalName = FileNames.uniqueName(name) { dir.findFile(it) != null }
        val doc = dir.createFile(mimeFor(finalName), finalName)
            ?: throw IOException("Could not create $finalName in the chosen folder")
        val raw = context.contentResolver.openOutputStream(doc.uri, "w")
            ?: throw IOException("Could not open $finalName for writing")
        val stream = BufferedOutputStream(raw, BUFFER)
        return Destination(
            out = stream,
            finish = {
                stream.close()
                doc.name ?: finalName
            },
            abort = {
                runCatching { stream.close() }
                doc.delete()
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openMediaStore(name: String): Destination {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeFor(name))
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/AirFile")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create $name in Downloads")
        val raw = resolver.openOutputStream(uri)
            ?: throw IOException("Could not open $name for writing")
        val stream = BufferedOutputStream(raw, BUFFER)
        return Destination(
            out = stream,
            finish = {
                stream.close()
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
                // MediaStore may rename on collision; report the real name.
                resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    ?: name
            },
            abort = {
                runCatching { stream.close() }
                runCatching { resolver.delete(uri, null, null) }
            },
        )
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val BUFFER = 256 * 1024
    }
}
