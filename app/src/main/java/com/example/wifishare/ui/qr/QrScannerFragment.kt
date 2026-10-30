package com.example.wifishare.ui.qr

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.data.qr.QrPayload
import com.example.wifishare.databinding.FragmentQrScannerBinding
import com.example.wifishare.ui.common.BaseFragment
import com.example.wifishare.ui.transfer.TransferFragment
import com.example.wifishare.ui.viewer.ScannerActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class QrScannerFragment : BaseFragment<FragmentQrScannerBinding>(
    { i, p, a -> FragmentQrScannerBinding.inflate(i, p, a) }
) {
    private var lastPayload: QrPayload? = null

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents.isNullOrBlank()) {
            binding.tvResult.text = getString(R.string.qr_scanner_cancelled)
            binding.btnConnect.isVisible = false
            lastPayload = null
            return@registerForActivityResult
        }
        val payload = QrPayload.decode(contents)
        if (payload != null) {
            lastPayload = payload
            binding.tvResult.text = getString(
                R.string.qr_scanner_found, payload.name, payload.ip, payload.port
            )
            binding.btnConnect.isVisible = true
        } else {
            lastPayload = null
            binding.tvResult.text = getString(R.string.qr_scanner_invalid)
            binding.btnConnect.isVisible = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnScan.setOnClickListener { launchScanner() }
        binding.btnConnect.setOnClickListener {
            val p = lastPayload ?: return@setOnClickListener
            findNavController().navigate(
                R.id.action_qrScanner_to_transfer,
                bundleOf(
                    TransferFragment.ARG_MODE to "SEND",
                    TransferFragment.ARG_HOST_IP to p.ip,
                    TransferFragment.ARG_TOKEN to p.token
                )
            )
        }
    }

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setCaptureActivity(ScannerActivity::class.java)
            setPrompt(getString(R.string.qr_scanner_prompt))
            setBeepEnabled(false)
            setOrientationLocked(false)
        }
        scanLauncher.launch(options)
    }
}
