package com.example.wifishare.data.transfer

import android.content.Context
import android.os.Environment
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger
import com.example.wifishare.domain.model.TransferDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import kotlin.coroutines.coroutineContext

class TransferServer(private val context: Context) {

    private val _progress = MutableStateFlow(TransferProgress(direction = TransferDirection.RECEIVE))
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    fun receiveDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        return File(base, Constants.DOWNLOAD_SUBDIR).apply { mkdirs() }
    }

    suspend fun receiveOnce(): Result<List<File>> = withContext(Dispatchers.IO) {
        var server: ServerSocket? = null
        var socket: Socket? = null
        val saved = mutableListOf<File>()
        try {
            server = ServerSocket(Constants.TRANSFER_PORT).apply {
                reuseAddress = true
                soTimeout = Constants.SERVER_ACCEPT_TIMEOUT_MS.toInt()
            }
            Logger.d("TransferServer listening on ${Constants.TRANSFER_PORT}")
            socket = server.accept().apply {
                soTimeout = Constants.SOCKET_TIMEOUT_MS.toInt()
                tcpNoDelay = true
                receiveBufferSize = 256 * 1024
            }
            val input = TransferProtocol.wrapIn(socket.getInputStream())
            val out = TransferProtocol.wrapOut(socket.getOutputStream())
            val saveDir = receiveDir()
            val startNs = System.currentTimeMillis()
            var recvBytes = 0L
            var fileCount = 0
            var totalBytes = 0L
            while (true) {
                coroutineContext.ensureActive()
                val meta = TransferProtocol.readHeader(input)
                if (TransferProtocol.isEnd(meta)) break
                fileCount++
                totalBytes += meta.size
                _progress.value = _progress.value.copy(
                    direction = TransferDirection.RECEIVE,
                    currentFileName = meta.name,
                    fileIndex = fileCount,
                    totalFiles = fileCount,
                    totalBytes = totalBytes,
                    isRunning = true
                )
                val outFile = uniqueFile(saveDir, meta.name)
                outFile.outputStream().buffered().use { fileOut ->
                    var remaining = meta.size
                    val buffer = ByteArray(Constants.CHUNK_SIZE)
                    while (remaining > 0) {
                        coroutineContext.ensureActive()
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) throw IOException("Connection closed mid-file")
                        fileOut.write(buffer, 0, read)
                        remaining -= read
                        recvBytes += read
                        val elapsed = (System.currentTimeMillis() - startNs).coerceAtLeast(1L)
                        _progress.value = _progress.value.copy(
                            bytesTransferred = recvBytes,
                            bytesPerSecond = (recvBytes * 1000L) / elapsed
                        )
                    }
                }
                saved.add(outFile)
                TransferProtocol.writeConfirm(out, ok = true)
            }
            _progress.value = _progress.value.copy(isRunning = false, isDone = true)
            Logger.i("TransferServer done: ${saved.size} file(s), $recvBytes bytes")
            Result.success(saved)
        } catch (e: Exception) {
            Logger.e("TransferServer failed", e)
            _progress.value = _progress.value.copy(
                isRunning = false,
                errorMessage = e.message ?: "Unknown error"
            )
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: IOException) {}
            try { server?.close() } catch (_: IOException) {}
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (true) {
            val f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }
}
