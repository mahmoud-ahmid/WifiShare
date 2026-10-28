package com.example.wifishare.data.transfer

import android.content.ContentResolver
import android.net.Uri
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger
import com.example.wifishare.domain.model.FileMeta
import com.example.wifishare.domain.model.TransferDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.coroutineContext

/**
 * عميل النقل (المرسل). يتصل بجهاز المستقبِل عبر Socket ويعطيه قائمة ملفات.
 * StateFlow<TransferProgress> يوفّر تحديثات UI حيّة.
 */
class TransferClient(private val contentResolver: ContentResolver) {

    private val _progress = MutableStateFlow(TransferProgress(direction = TransferDirection.SEND))
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    suspend fun send(hostIp: String, files: List<Uri>, token: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            if (hostIp.isBlank()) throw IOException("Empty receiver IP")
            if (files.isEmpty()) throw IOException("No files selected")

            socket = Socket().apply {
                connect(InetSocketAddress(hostIp, Constants.TRANSFER_PORT), Constants.CONNECT_TIMEOUT_MS.toInt())
                soTimeout = Constants.SOCKET_TIMEOUT_MS.toInt()
                tcpNoDelay = true
                sendBufferSize = 256 * 1024
            }
            val out = TransferProtocol.wrapOut(socket.getOutputStream())
            val input = TransferProtocol.wrapIn(socket.getInputStream())

            // مصافحة + توكن الجلسة
            TransferProtocol.writeHello(out, token)
            if (input.readByte().toInt() != 1) {
                throw IOException("Receiver refused the connection (invalid or expired code)")
            }

            val metas = files.map { uri ->
                var size = querySize(uri)
                if (size <= 0L) size = countBytes(uri)   // الحجم مجهول: احسبه فعليًا
                FileMeta(
                    name = queryName(uri) ?: "file_${System.currentTimeMillis()}",
                    size = size
                )
            }
            val totalBytes = metas.sumOf { it.size }

            _progress.value = TransferProgress(
                direction = TransferDirection.SEND,
                totalFiles = metas.size,
                totalBytes = totalBytes,
                isRunning = true
            )

            val startNs = System.currentTimeMillis()
            var sentBytes = 0L
            val buffer = ByteArray(Constants.CHUNK_SIZE)

            metas.forEachIndexed { idx, meta ->
                coroutineContext.ensureActive()
                _progress.value = _progress.value.copy(
                    currentFileName = meta.name,
                    fileIndex = idx + 1
                )
                TransferProtocol.writeHeader(out, meta)

                val stream = contentResolver.openInputStream(files[idx])
                    ?: throw IOException("Cannot open ${meta.name}")
                stream.use { rawIn ->
                    var remaining = meta.size
                    while (remaining > 0) {
                        coroutineContext.ensureActive()
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = rawIn.read(buffer, 0, toRead)
                        if (read <= 0) throw IOException("Source shorter than declared: ${meta.name}")
                        out.write(buffer, 0, read)
                        remaining -= read
                        sentBytes += read
                        val elapsed = (System.currentTimeMillis() - startNs).coerceAtLeast(1L)
                        _progress.value = _progress.value.copy(
                            bytesTransferred = sentBytes,
                            bytesPerSecond = (sentBytes * 1000L) / elapsed
                        )
                    }
                    out.flush()
                }

                if (!TransferProtocol.readConfirm(input)) {
                    throw IOException("Receiver rejected: ${meta.name}")
                }
            }

            TransferProtocol.writeEnd(out)
            _progress.value = _progress.value.copy(isRunning = false, isDone = true)
            Logger.i("TransferClient done: ${metas.size} file(s), $sentBytes bytes")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("TransferClient failed", e)
            _progress.value = _progress.value.copy(
                isRunning = false,
                errorMessage = e.message ?: "Unknown error"
            )
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: IOException) {}
        }
    }

    private fun countBytes(uri: Uri): Long =
        contentResolver.openInputStream(uri)?.use { s ->
            var n = 0L
            val b = ByteArray(64 * 1024)
            while (true) {
                val r = s.read(b)
                if (r < 0) break
                n += r
            }
            n
        } ?: 0L

    private fun queryName(uri: Uri): String? {
        val proj = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
        contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i)
            }
        }
        return uri.lastPathSegment
    }

    private fun querySize(uri: Uri): Long {
        val proj = arrayOf(android.provider.OpenableColumns.SIZE)
        contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (i >= 0 && !c.isNull(i)) return c.getLong(i)
            }
        }
        return 0L
    }
}
