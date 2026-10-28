package com.example.wifishare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.wifishare.R

object NotificationHelper {
    const val CHANNEL_ID = "transfer_channel"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun buildProgress(context: Context, title: String, text: String, pct: Int): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .build()
    }

    fun notify(context: Context, id: Int, n: Notification) {
        context.getSystemService(NotificationManager::class.java).notify(id, n)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
