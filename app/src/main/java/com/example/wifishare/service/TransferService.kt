package com.example.wifishare.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.wifishare.R
import com.example.wifishare.core.Constants
import com.example.wifishare.core.Logger

/**
 * خدمة أمامية بسيطة تُبقي إشعار النقل حيًا. المنطق الفعلي في TransferClient/Server.
 * تُشغَّل من الـ ViewModel مع EXTRA_TEXT و EXTRA_PERCENT.
 */
class TransferService : Service() {
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
        Logger.d("TransferService started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        NotificationHelper.cancel(this, Constants.NOTIFICATION_ID_TRANSFER)
    }

    companion object {
        const val EXTRA_TEXT = "extra_text"
        const val EXTRA_PERCENT = "extra_percent"
    }
}
