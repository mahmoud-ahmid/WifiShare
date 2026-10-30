package com.example.wifishare.ui.viewer

import android.os.Bundle
import android.view.ViewGroup
import com.example.wifishare.R
import com.journeyapps.barcodescanner.CaptureActivity

class ScannerActivity : CaptureActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val content = findViewById<ViewGroup>(android.R.id.content)
            val overlay = layoutInflater.inflate(R.layout.scanner_overlay, content, false)
            content.addView(overlay)
        } catch (_: Exception) {
        }
    }
}
