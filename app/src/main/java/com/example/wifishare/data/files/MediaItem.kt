package com.example.wifishare.data.files

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val modifiedMs: Long,
    val source: Source,
    val filePath: String? = null
)

enum class Source { RECEIVED, IMAGES, VIDEOS, AUDIO, DOCUMENTS }
