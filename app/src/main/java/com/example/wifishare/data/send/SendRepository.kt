package com.example.wifishare.data.send

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.wifishare.core.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SendRepository(private val context: Context) {

    suspend fun load(category: SendItem.Category): List<SendItem> = withContext(Dispatchers.IO) {
        when (category) {
            SendItem.Category.APPLICATIONS -> apps()
            SendItem.Category.AUDIO -> media(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_MODIFIED,
                SendItem.Category.AUDIO
            )
            SendItem.Category.IMAGES -> media(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.DATE_MODIFIED,
                SendItem.Category.IMAGES
            )
            SendItem.Category.DOCUMENTS -> docs()
            SendItem.Category.FILES -> filesRoot()
        }
    }

    suspend fun listDir(path: String): List<SendItem> = withContext(Dispatchers.IO) {
        try {
            val dir = File(path)
            if (!dir.isDirectory || !dir.canRead()) return@withContext emptyList()
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.mapNotNull { f ->
                    val uri = try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            f
                        )
                    } catch (_: Exception) { null }
                    if (uri == null) null else SendItem(
                        uri = uri,
                        name = f.name,
                        sizeBytes = if (f.isDirectory) 0L else f.length(),
                        mimeType = if (f.isDirectory) "inode/directory"
                            else context.contentResolver.getType(uri) ?: guessMime(f.name),
                        category = SendItem.Category.FILES,
                        sourcePath = f.absolutePath
                    )
                } ?: emptyList()
        } catch (e: Exception) {
            Logger.e("listDir failed", e); emptyList()
        }
    }

    private fun apps(): List<SendItem> {
        val pm = context.packageManager
        val infos = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        val result = mutableListOf<SendItem>()
        for (info in infos) {
            val launchIntent = pm.getLaunchIntentForPackage(info.packageName) ?: continue
            val label = try { pm.getApplicationLabel(info).toString() } catch (_: Exception) { info.packageName }
            val icon = try { pm.getApplicationIcon(info.packageName) } catch (_: Exception) { null }
            val apkPath = info.sourceDir
            val apk = File(apkPath)
            val size = if (apk.exists() && apk.canRead()) apk.length() else 0L
            // URI وهمي — لن يُستخدم للإرسال، لكنه مطلوب في SendItem
            val virtualUri = Uri.parse("apk://" + info.packageName)
            result.add(SendItem(
                uri = virtualUri,
                name = label,
                sizeBytes = size,
                mimeType = "application/vnd.android.package-archive",
                category = SendItem.Category.APPLICATIONS,
                sourcePath = apkPath,
                isInstalledApp = true,
                subCategory = "INSTALLED",
                appIcon = icon,
                packageName = info.packageName
            ))
        }
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            downloads.listFiles()?.filter { it.isFile && it.extension.lowercase() == "apk" }?.forEach { f ->
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    result.add(SendItem(
                        uri = uri, name = f.name, sizeBytes = f.length(),
                        mimeType = "application/vnd.android.package-archive",
                        category = SendItem.Category.APPLICATIONS,
                        sourcePath = f.absolutePath, isInstalledApp = false,
                        subCategory = "NOT_INSTALLED"
                    ))
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        Logger.i("apps() returned " + result.size + " apps")
        return result.sortedWith(compareByDescending<SendItem> { it.isInstalledApp }.thenBy { it.name.lowercase() })
    }

    private fun media(
        collection: Uri, idCol: String, nameCol: String, sizeCol: String,
        mimeCol: String, dateCol: String, category: SendItem.Category
    ): List<SendItem> {
        val out = mutableListOf<SendItem>()
        try {
            val proj = arrayOf(idCol, nameCol, sizeCol, mimeCol, dateCol)
            context.contentResolver.query(collection, proj, null, null, "$dateCol DESC")?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(idCol)
                val nameIdx = c.getColumnIndexOrThrow(nameCol)
                val sizeIdx = c.getColumnIndexOrThrow(sizeCol)
                val mimeIdx = c.getColumnIndexOrThrow(mimeCol)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    out.add(
                        SendItem(
                            uri = uri,
                            name = c.getString(nameIdx) ?: "file",
                            sizeBytes = c.getLong(sizeIdx),
                            mimeType = c.getString(mimeIdx) ?: "*/*",
                            category = category
                        )
                    )
                }
            }
        } catch (e: Exception) { Logger.e("query $category failed", e) }
        return out
    }

    private fun docs(): List<SendItem> {
        val out = mutableListOf<SendItem>()
        try {
            val collection = MediaStore.Files.getContentUri("external")
            val proj = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            val sel = "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?"
            val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_NONE.toString())
            context.contentResolver.query(collection, proj, sel, args, null)?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    out.add(
                        SendItem(
                            uri = uri,
                            name = c.getString(nameIdx) ?: "file",
                            sizeBytes = c.getLong(sizeIdx),
                            mimeType = c.getString(mimeIdx) ?: "application/octet-stream",
                            category = SendItem.Category.DOCUMENTS
                        )
                    )
                }
            }
        } catch (e: Exception) { Logger.e("docs failed", e) }
        return out
    }

    private fun filesRoot(): List<SendItem> {
        val root = Environment.getExternalStorageDirectory() ?: return emptyList()
        return listDirSync(root.absolutePath)
    }

    private fun listDirSync(path: String): List<SendItem> {
        return try {
            val dir = File(path)
            if (!dir.isDirectory || !dir.canRead()) return emptyList()
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.mapNotNull { f ->
                    val uri = try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            f
                        )
                    } catch (_: Exception) { null }
                    if (uri == null) null else SendItem(
                        uri = uri,
                        name = f.name,
                        sizeBytes = if (f.isDirectory) 0L else f.length(),
                        mimeType = if (f.isDirectory) "inode/directory"
                            else context.contentResolver.getType(uri) ?: guessMime(f.name),
                        category = SendItem.Category.FILES,
                        sourcePath = f.absolutePath
                    )
                } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun guessMime(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "txt" -> "text/plain"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
