package com.example.wifishare.core

import java.io.File

/** تعقيم أسماء الملفات القادمة من الشبكة أو من المستخدم (يمنع ../ والمسارات). */
object SafeName {
    private val BAD = Regex("[\\u0000-\\u001F<>:\"|?*]")

    fun sanitize(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(BAD, "_").trim().trim('.')
        val name = if (base.isBlank()) "file_${System.currentTimeMillis()}" else base
        if (name.length <= 120) return name
        val ext = name.substringAfterLast('.', "").take(10)
        val stem = name.substringBeforeLast('.', name).take(100)
        return if (ext.isEmpty()) stem else "$stem.$ext"
    }

    /** يتأكد أن الملف داخل المجلد المسموح (canonical path). */
    fun isInside(dir: File, file: File): Boolean =
        file.canonicalPath.startsWith(dir.canonicalPath + File.separator)
}
