package com.example.wifishare.ui.transfer

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.wifishare.AppContainer
import com.example.wifishare.R
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger
import com.example.wifishare.data.files.PendingSendHolder
import com.example.wifishare.data.history.TransferRecord
import com.example.wifishare.data.transfer.TransferProgress
import com.example.wifishare.domain.model.TransferDirection
import com.example.wifishare.service.NotificationHelper
import com.example.wifishare.service.TransferService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class TransferViewModel(
    app: Application,
    private val container: AppContainer,
    val direction: TransferDirection,
    private val hostIp: String,
    private val token: String = ""
) : AndroidViewModel(app) {

    private val _progress = MutableStateFlow(TransferProgress(direction = direction))
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    private val _savedFiles = MutableStateFlow<List<File>>(emptyList())
    val savedFiles: StateFlow<List<File>> = _savedFiles.asStateFlow()

    private var serviceStarted = false
    private var running = false

    init {
        viewModelScope.launch {
            val source = when (direction) {
                TransferDirection.SEND -> container.transferClient.progress
                TransferDirection.RECEIVE -> container.transferServer.progress
            }
            source.collect { p ->
                _progress.value = p
                if (p.isRunning) updateNotification(p)
            }
        }
        // إذا كان المستخدم اختار ملفات من مدير الملفات، أرسلها تلقائيًا
        if (direction == TransferDirection.SEND && hostIp.isNotBlank()) {
            val pending = PendingSendHolder.take()
            if (pending.isNotEmpty()) {
                viewModelScope.launch {
                    kotlinx.coroutines.delay(400)
                    sendFiles(pending)
                }
            }
        }
    }

    fun startReceiving() {
        if (running || direction != TransferDirection.RECEIVE) return
        running = true
        startForegroundSafely()
        viewModelScope.launch {
            Logger.i("Receiving on port ${Constants.TRANSFER_PORT}")
            val result = container.transferServer.receiveOnce()
            running = false
            result.onSuccess { files ->
                _savedFiles.value = files
                files.forEach { f ->
                    container.historyRepository.add(
                        TransferRecord(
                            id = UUID.randomUUID().toString(),
                            fileName = f.name,
                            sizeBytes = f.length(),
                            direction = "RECEIVE",
                            peerName = hostIp.ifBlank { "Peer" },
                            timestampMs = System.currentTimeMillis(),
                            success = true,
                            savedPath = f.absolutePath
                        )
                    )
                }
            }
            result.onFailure {
                val p = _progress.value
                if (p.currentFileName.isNotBlank()) {
                    container.historyRepository.add(
                        TransferRecord(
                            id = UUID.randomUUID().toString(),
                            fileName = p.currentFileName,
                            sizeBytes = p.bytesTransferred,
                            direction = "RECEIVE",
                            peerName = hostIp.ifBlank { "Peer" },
                            timestampMs = System.currentTimeMillis(),
                            success = false,
                            savedPath = null
                        )
                    )
                }
            }
            stopForegroundSafely()
        }
    }

    fun sendFiles(uris: List<Uri>) {
        if (running || direction != TransferDirection.SEND) return
        if (uris.isEmpty()) return
        if (hostIp.isBlank()) {
            _progress.value = _progress.value.copy(
                isRunning = false,
                errorMessage = "Host IP is empty"
            )
            return
        }
        running = true
        startForegroundSafely()
        viewModelScope.launch {
            Logger.i("Sending ${uris.size} file(s) to $hostIp")
            val result = container.transferClient.send(hostIp, uris, token)
            running = false
            val p = _progress.value
            if (p.currentFileName.isNotBlank()) {
                container.historyRepository.add(
                    TransferRecord(
                        id = UUID.randomUUID().toString(),
                        fileName = p.currentFileName,
                        sizeBytes = p.bytesTransferred,
                        direction = "SEND",
                        peerName = hostIp,
                        timestampMs = System.currentTimeMillis(),
                        success = result.isSuccess,
                        savedPath = null
                    )
                )
            }
            result.onFailure { Logger.e("Send failed", it) }
            stopForegroundSafely()
        }
    }

    private fun startForegroundSafely() {
        if (serviceStarted) return
        try {
            val ctx = getApplication<Application>()
            val intent = Intent(ctx, TransferService::class.java).apply {
                putExtra(TransferService.EXTRA_TEXT, ctx.getString(R.string.notif_transfer_running))
                putExtra(TransferService.EXTRA_PERCENT, 0)
            }
            ContextCompat.startForegroundService(ctx, intent)
            serviceStarted = true
        } catch (e: Exception) {
            Logger.w("Foreground service failed: ${e.message}")
        }
    }

    private fun stopForegroundSafely() {
        if (!serviceStarted) return
        try {
            getApplication<Application>().stopService(
                Intent(getApplication(), TransferService::class.java)
            )
        } catch (_: Exception) {}
        serviceStarted = false
    }

    private fun updateNotification(p: TransferProgress) {
        try {
            val ctx = getApplication<Application>()
            val text = if (p.currentFileName.isNotBlank())
                "${p.currentFileName} (${p.percent}%)"
            else ctx.getString(R.string.notif_transfer_running)
            val n = NotificationHelper.buildProgress(
                ctx, ctx.getString(R.string.notif_transfer_running), text, p.percent
            )
            NotificationHelper.notify(ctx, Constants.NOTIFICATION_ID_TRANSFER, n)
        } catch (_: Exception) {}
    }

    override fun onCleared() {
        super.onCleared()
        stopForegroundSafely()
    }

    class Factory(
        private val application: Application,
        private val container: AppContainer,
        private val direction: TransferDirection,
        private val hostIp: String,
        private val token: String = ""
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TransferViewModel::class.java))
            return TransferViewModel(application, container, direction, hostIp, token) as T
        }
    }
}
