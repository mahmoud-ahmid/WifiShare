package com.example.wifishare.data.transfer

import android.content.Context
import android.os.Environment
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger
import com.example.wifishare.core.SafeName
import com.example.wifishare.core.SessionToken
import com.example.wifishare.domain.model.TransferDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class TransferServer(private val context: Context) {

    private val _progress = MutableStateFlow(TransferProgress(direction = TransferDirection.RECEIVE))
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    fun receiveDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        return File(base, Constants.DOWNLOAD_SUBDIR).apply { mkdirs() }
    }

    suspend fun receiveOnce(token: String = SessionToken.current): Result<List<File>> =
        withContext(Dispatchers.IO) {
            var server: ServerSocket? = null
            var socket: Socket? = null
            var partial: File? = null
            val saved = mutableListOf<File>()
            try {
                val srv = ServerSocket(Constants.TRANSFER_PORT).apply { reuseAddress = true }
                server = srv
                val deadline = System.currentTimeMillis() + Constants.SERVER_ACCEPT_TIMEOUT_MS
                Logger.d("TransferServer listening on ${Constants.TRANSFER_PORT}")

                var streams: Pair<DataInputStream, DataOutputStream>? = null
                var bad = 0
                while (streams == null) {
                    coroutineContext.ensureActive()
                    val left = deadline - System.currentTimeMillis()
                    if (left <= 0) throw IOException("Timed out waiting for sender")
                    srv.soTimeout = left.toInt()
                    val s = srv.accept()
                    val hs = handshake(s, token)
                    if (hs == null) {
                        try { s.close() } catch (_: IOException) {}
                        bad++
                        if (bad >= Constants.MAX_BAD_ATTEMPTS) throw IOException("Too many rejected connections")
                    } else {
                        s.tcpNoDelay = true
                        s.receiveBufferSize = 256 * 1024
                        socket = s
                        streams = hs
                    }
                }
                val ready = streams ?: throw IOException("No connection")
                val input = ready.first
                val out = ready.second

                val saveDir = receiveDir()
                val startMs = System.currentTimeMillis()
                var recvBytes = 0L
                var fileCount = 0
                var totalBytes = 0L

                while (true) {
                    coroutineContext.ensureActive()
                    val meta = TransferProtocol.readHeader(input)
                    if (TransferProtocol.isEnd(meta)) break
                    if (fileCount >= Constants.MAX_FILES) throw IOException("Too many files")
                    if (meta.size < 0 || meta.size > Constants.MAX_FILE_BYTES) throw IOException("Invalid file size")
                    if (saveDir.usableSpace < meta.size + Constants.MIN_FREE_BYTES) throw IOException("Not enough storage")

                    fileCount++
                    totalBytes += meta.size
                    _progress.value = _progress.value.copy(
                        direction = TransferDirection.RECEIVE,
                        currentFileName = SafeName.sanitize(meta.name),
                        fileIndex = fileCount,
                        totalFiles = fileCount,
                        totalBytes = totalBytes,
                        isRunning = true
                    )

                    val outFile = uniqueFile(saveDir, meta.name)
                    partial = outFile
                    FileOutputStream(outFile).buffered().use { fileOut ->
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
                            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                            _progress.value = _progress.value.copy(
                                bytesTransferred = recvBytes,
                                bytesPerSecond = (recvBytes * 1000L) / elapsed
                            )
                        }
                    }
                    partial = null
                    saved.add(outFile)
                    TransferProtocol.writeConfirm(out, ok = true)
                }
                _progress.value = _progress.value.copy(isRunning = false, isDone = true)
                Logger.i("TransferServer done: ${saved.size} file(s), $recvBytes bytes")
                Result.success(saved)
            } catch (e: Exception) {
                Logger.e("TransferServer failed", e)
                partial?.delete()   // لا تترك ملفًا ناقصًا
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

    /** هل وصل الاتصال عبر واجهة Wi-Fi Direct (p2p)؟ الرابط نفسه مصادَق عليه بقبول الطرفين. */
    private fun isP2pLink(s: Socket): Boolean = try {
        NetworkInterface.getByInetAddress(s.localAddress)?.name?.contains("p2p", ignoreCase = true) == true
    } catch (_: Exception) { false }

    /**
     * يقبل فقط عناوين الشبكة المحلية. ثم: إمّا واجهة p2p، أو توكن صحيح وغير منتهي.
     * يرجع null عند الرفض.
     */
    private fun handshake(s: Socket, token: String): Pair<DataInputStream, DataOutputStream>? = try {
        val a = s.inetAddress
        if (!(a.isSiteLocalAddress || a.isLinkLocalAddress || a.isLoopbackAddress)) {
            null
        } else {
            s.soTimeout = Constants.HANDSHAKE_TIMEOUT_MS.toInt()
            val input = TransferProtocol.wrapIn(s.getInputStream())
            val out = TransferProtocol.wrapOut(s.getOutputStream())
            val got = TransferProtocol.readHello(input)
            val fresh = token != SessionToken.current || !SessionToken.isExpired()
            val tokenOk = got != null && got.isNotEmpty() && fresh &&
                MessageDigest.isEqual(got.toByteArray(), token.toByteArray())
            val ok = got != null && (tokenOk || isP2pLink(s))
            out.writeByte(if (ok) 1 else 0)
            out.flush()
            if (ok) {
                s.soTimeout = Constants.SOCKET_TIMEOUT_MS.toInt()
                Pair(input, out)
            } else null
        }
    } catch (_: Exception) { null }

    private fun uniqueFile(dir: File, rawName: String): File {
        val name = SafeName.sanitize(rawName)
        var f = File(dir, name)
        if (f.exists()) {
            val base = name.substringBeforeLast('.', name)
            val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
            var i = 1
            while (true) {
                f = File(dir, "$base ($i)$ext")
                if (!f.exists()) break
                i++
            }
        }
        if (!SafeName.isInside(dir, f)) throw IOException("Unsafe file name")
        return f
    }
}
