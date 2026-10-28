package com.example.wifishare.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * مدير صلاحيات مركزي.
 * يتعامل مع فروق الإصدارات: API 24 → 36.
 * - API < 33: ACCESS_FINE_LOCATION مطلوب لاكتشاف Wi-Fi Direct.
 * - API >= 33: NEARBY_WIFI_DEVICES يحل محله.
 * - POST_NOTIFICATIONS مطلوب من API 33+.
 * - CAMERA مطلوب لمسح QR (يُطلب عند الحاجة فقط).
 */
object PermissionHelper {

    /** صلاحيات Wi-Fi المطلوبة لاكتشاف الأجهزة والاتصال. */
    fun wifiPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /** صلاحية الكاميرا لمسح QR. تُطلب عند فتح شاشة المسح. */
    fun cameraPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)

    fun hasAll(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun missing(context: Context, permissions: Array<String>): List<String> =
        permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
}
