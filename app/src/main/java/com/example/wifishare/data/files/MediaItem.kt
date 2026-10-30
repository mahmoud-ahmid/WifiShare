package com.example.wifishare.data.files

import android.graphics.drawable.Drawable
import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedMs: Long,
    val source: Source,
    val filePath: String? = null,
    val appIcon: Drawable? = null,
    val isInstalledApp: Boolean = false
)

enum class Source { RECEIVED, IMAGES, VIDEOS, AUDIO, DOCUMENTS, FOLDERS, APPS }
