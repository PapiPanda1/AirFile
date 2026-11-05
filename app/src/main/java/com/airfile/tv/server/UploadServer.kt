package com.airfile.tv.server

import android.content.Context
import com.airfile.tv.AirFileState
import com.airfile.tv.ReceivedFile
import com.airfile.tv.storage.FileSink
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.security.MessageDigest

/**
 * Tiny HTTP server:
 *  - GET  /?t=TOKEN                      -> the upload page
 *  - POST /upload?t=TOKEN&name=FILENAME  -> raw request body is streamed to storage
 * Every request must carry the session token shown in the QR code.
 */
class UploadServer(
    private val context: Context,
    port: Int,
    private val token: String,
    private val sink: FileSink,
) : NanoHTTPD(port) {

    private val page: String by lazy {
        context.assets.open("upload.html").bufferedReader().use { it.readText() }
    }

    override fun serve(session: IHTTPSession): Response {
        val given = session.parameters["t"]?.firstOrNull().orEmpty()
        if (!MessageDigest.isEqual(given.toByteArray(), token.toByteArray())) {
            return plain(Response.Status.FORBIDDEN, "Forbidden: scan the QR code on the TV again.")
        }
        return when {
            session.method == Method.GET && session.uri == "/" ->
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", page)
                    .apply { addHeader("Cache-Control", "no-store") }
            session.method == Method.POST && session.uri == "/upload" -> handleUpload(session)
            else -> plain(Response.Status.NOT_FOUND, "Not found")
        }
    }

    private fun handleUpload(session: IHTTPSession): Response {
        val name = session.parameters["name"]?.firstOrNull() ?: "file"
        val length = session.headers["content-length"]?.toLongOrNull()
            ?: return plain(Response.Status.BAD_REQUEST, "Content-Length header required")

        AirFileState.uploadStarted()
        var dest: FileSink.Destination? = null
        try {
            dest = sink.open(name)
            val buffer = ByteArray(64 * 1024)
            val input = session.inputStream
            var remaining = length
            while (remaining > 0) {
                val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (n < 0) throw IOException("Connection closed before the upload finished")
                dest.out.write(buffer, 0, n)
                remaining -= n
            }
            val savedName = dest.finish()
            AirFileState.addReceived(ReceivedFile(savedName, length))
            return plain(Response.Status.OK, "OK")
        } catch (e: Exception) {
            runCatching { dest?.abort?.invoke() }
            return plain(Response.Status.INTERNAL_ERROR, e.message ?: "Upload failed")
                .apply { addHeader("Connection", "close") }
        } finally {
            AirFileState.uploadFinished()
        }
    }

    private fun plain(status: Response.IStatus, text: String): Response =
        newFixedLengthResponse(status, "text/plain; charset=utf-8", text)
}
