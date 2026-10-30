package com.example.wifishare.ui.qr

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.data.qr.QrPayload
import com.example.wifishare.databinding.FragmentQrScannerBinding
import com.example.wifishare.ui.common.BaseFragment
import com.example.wifishare.ui.transfer.TransferFragment
import com.example.wifishare.ui.viewer.ScannerActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

class QrScannerFragment : BaseFragment<FragmentQrScannerBinding>(
    { i, p, a -> FragmentQrScannerBinding.inflate(i, p, a) }
) {
    private var lastPayload: QrPayload? = null
    private var connecting = false
    private var manualJoined = false

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        manualJoined = false
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
            binding.btnConnect.isEnabled = true
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
            if (connecting) return@setOnClickListener
            if (p.ssid.isBlank() || manualJoined) { goTransfer(p); return@setOnClickListener }
            connecting = true
            binding.btnConnect.isEnabled = false
            binding.tvResult.text = "جارٍ الاتصال بشبكة ${p.ssid} ... وافق على طلب النظام إن ظهر"
            viewLifecycleOwner.lifecycleScope.launch {
                val container = (requireActivity().application as WifiShareApp).container
                val net = container.wifiConnector.connect(p.ssid, p.pass)
                connecting = false
                if (view == null) return@launch
                binding.btnConnect.isEnabled = true
                if (net != null) {
                    goTransfer(p)
                } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    manualJoined = true
                    binding.tvResult.text =
                        "اتصل يدويًا بالشبكة: ${p.ssid}\nكلمة المرور: ${p.pass}\nثم اضغط اتصال مرة أخرى"
                } else {
                    binding.tvResult.text = "تعذّر الاتصال بالشبكة ${p.ssid}. أعد المسح وحاول مجددًا."
                }
            }
        }
    }

    private fun goTransfer(p: QrPayload) {
        findNavController().navigate(
            R.id.action_qrScanner_to_transfer,
            bundleOf(
                TransferFragment.ARG_MODE to "SEND",
                TransferFragment.ARG_HOST_IP to p.ip,
                TransferFragment.ARG_TOKEN to p.token
            )
        )
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
