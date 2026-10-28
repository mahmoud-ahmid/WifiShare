package com.example.wifishare.data.files

import android.content.Context
import android.os.Environment
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class ReceivedFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val modifiedMs: Long
)

class ReceivedFilesRepository(private val context: Context) {
    private val _files = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val files: StateFlow<List<ReceivedFile>> = _files.asStateFlow()

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize.asStateFlow()

    fun receiveDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download")
        return File(base, Constants.DOWNLOAD_SUBDIR).apply { mkdirs() }
    }

    fun refresh() {
        try {
            val dir = receiveDir()
            val list = (dir.listFiles() ?: emptyArray())
                .filter { it.isFile }
                .map { ReceivedFile(it, it.name, it.length(), it.lastModified()) }
                .sortedByDescending { it.modifiedMs }
            _files.value = list
            _totalSize.value = list.sumOf { it.sizeBytes }
        } catch (e: Exception) {
            Logger.e("refresh failed", e)
        }
    }

    suspend fun delete(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val ok = file.delete()
            if (ok) refresh()
            ok
        } catch (e: Exception) {
            Logger.e("delete failed", e)
            false
        }
    }
}
