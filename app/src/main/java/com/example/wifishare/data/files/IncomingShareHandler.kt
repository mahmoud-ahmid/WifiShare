package com.example.wifishare.data.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger
import com.example.wifishare.core.SafeName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object IncomingShareHandler {
    suspend fun handle(ctx: Context, intent: Intent): List<File> = withContext(Dispatchers.IO) {
        val raw: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            )
            Intent.ACTION_SEND_MULTIPLE ->
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM) ?: emptyList()
            else -> emptyList()
        }
        val uris = raw
            .filter { it.scheme == "content" && it.authority != Constants.PROVIDER_AUTHORITY }
            .take(Constants.MAX_FILES)
        if (uris.isEmpty()) return@withContext emptyList()

        val base = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(ctx.filesDir, "Download")
        val dir = File(base, Constants.DOWNLOAD_SUBDIR).apply { mkdirs() }

        val saved = mutableListOf<File>()
        for (uri in uris) {
            var out: File? = null
            try {
                val name = SafeName.sanitize(queryName(ctx, uri) ?: "shared_${System.currentTimeMillis()}")
                val target = uniqueFile(dir, name)
                if (!SafeName.isInside(dir, target)) throw IOException("Unsafe file name")
                out = target
                var copied = 0L
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().buffered(256 * 1024).use { output ->
                        val buf = ByteArray(128 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            copied += n
                            if (copied > Constants.MAX_FILE_BYTES) throw IOException("File too large")
                            output.write(buf, 0, n)
                        }
                    }
                } ?: throw IOException("Cannot open $uri")
                saved.add(target)
                Logger.i("Incoming saved: ${target.absolutePath}")
            } catch (e: Exception) {
                Logger.e("Failed to save uri $uri", e)
                out?.delete()
            }
        }
        saved
    }

    private fun queryName(ctx: Context, uri: Uri): String? {
        val proj = arrayOf(OpenableColumns.DISPLAY_NAME)
        ctx.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) return c.getString(i)
            }
        }
        return uri.lastPathSegment
    }

    private fun uniqueFile(dir: File, name: String): File {
        val c = File(dir, name)
        if (!c.exists()) return c
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
