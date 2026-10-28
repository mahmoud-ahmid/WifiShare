package com.example.wifishare.domain.model

/** بيانات وصفية لملف يتم نقله. */
data class FileMeta(
    val name: String,
    val size: Long,
    val mimeType: String? = null
)
