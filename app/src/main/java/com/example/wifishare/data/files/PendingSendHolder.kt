package com.example.wifishare.data.files

import android.net.Uri

/**
 * تحفظ URIs مؤقتًا عند اختيار "إرسال" من مدير الملفات، ثم يقرأها
 * TransferViewModel عند فتح شاشة الإرسال بعد الاتصال بجهاز.
 */
object PendingSendHolder {
    @Volatile var uris: List<Uri> = emptyList()
    fun set(newUris: List<Uri>) { uris = newUris.toList() }
    fun take(): List<Uri> { val u = uris; uris = emptyList(); return u }
    fun clear() { uris = emptyList() }
}
