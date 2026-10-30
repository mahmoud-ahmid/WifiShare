package com.example.wifishare.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.wifishare.R
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger

/**
 * خدمة أمامية: تُبقي الإشعار حيًا وتمنع نوم الواي فاي/المعالج أثناء النقل.
 * المنطق الفعلي في TransferClient/Server.
 */
class TransferService : Service() {
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT)
            ?: getString(R.string.notif_transfer_running)
        val pct = intent?.getIntExtra(EXTRA_PERCENT, 0) ?: 0
        val n = NotificationHelper.buildProgress(
            this,
            getString(R.string.notif_transfer_running),
            text,
            pct
        )
        startForeground(Constants.NOTIFICATION_ID_TRANSFER, n)
        acquireLocks()
        Logger.d("TransferService started")
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val mode = if (Build.VERSION.SDK_INT >= 29)
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                else WifiManager.WIFI_MODE_FULL_HIGH_PERF
                wifiLock = wm.createWifiLock(mode, "WifiShare:transfer").apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiShare:transfer").apply {
                    setReferenceCounted(false)
                    acquire(60 * 60 * 1000L)
                }
            }
        } catch (e: Exception) {
            Logger.w("Locks failed: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try { wifiLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wifiLock = null
        wakeLock = null
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
        NotificationHelper.cancel(this, Constants.NOTIFICATION_ID_TRANSFER)
    }

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_PERCENT = "extra_percent"
    }
}
