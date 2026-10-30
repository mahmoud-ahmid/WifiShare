package com.example.wifishare.data.send

import android.graphics.drawable.Drawable
import android.net.Uri

data class SendItem(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val category: Category,
    val sourcePath: String? = null,
    val isInstalledApp: Boolean = false,
    val subCategory: String? = null,
    val appIcon: Drawable? = null,
    val packageName: String? = null
) {
    enum class Category { APPLICATIONS, AUDIO, IMAGES, DOCUMENTS, FILES }
}
