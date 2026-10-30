package com.example.wifishare.ui.qr

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.Constants
import com.example.wifishare.core.PermissionHelper
import com.example.wifishare.core.SessionToken
import com.example.wifishare.core.toast
import com.example.wifishare.data.history.TransferRecord
import com.example.wifishare.data.qr.QrEncoder
import com.example.wifishare.data.qr.QrPayload
import com.example.wifishare.data.wifi.HotspotInfo
import com.example.wifishare.databinding.FragmentQrDisplayBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID

class QrDisplayFragment : BaseFragment<FragmentQrDisplayBinding>(
    { i, p, a -> FragmentQrDisplayBinding.inflate(i, p, a) }
) {
    private val appContainer get() = (requireActivity().application as WifiShareApp).container
    private var started = false

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startFlow() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showLoading("جارٍ تجهيز نقطة الاتصال...")
        binding.btnStartReceive.setOnClickListener { findNavController().popBackStack() }
        binding.btnRetry.setOnClickListener {
            started = false
            showLoading("جارٍ إعادة المحاولة...")
            begin()
        }
        begin()
    }

    private fun begin() {
        val missing = PermissionHelper.missing(requireContext(), PermissionHelper.wifiPermissions())
        if (missing.isEmpty()) startFlow() else permLauncher.launch(missing.toTypedArray())
    }

    private fun startFlow() {
        if (started || view == null) return
        started = true
        val hm = appContainer.hotspotManager
        viewLifecycleOwner.lifecycleScope.launch {
            hm.startHotspot()
            var hot: HotspotInfo? = null
            val t0 = System.currentTimeMillis()
            while (hot == null && hm.error.value == null && System.currentTimeMillis() - t0 < 12_000) {
                delay(250)
                hot = hm.hotspotInfo.value
            }
            val info = hot
            val ip = if (info != null) (hotspotIp() ?: info.ip) else (hotspotIp() ?: wifiIp())
            if (ip == null) {
                showError("لا توجد شبكة متاحة. شغّل الواي فاي أو نقطة الاتصال ثم أعد المحاولة.")
                return@launch
            }
            val ssid = info?.ssid.orEmpty().removeSurrounding("\"")
            val pass = info?.password.orEmpty()
            val creds = info != null && ssid.isNotEmpty() && pass.length >= 8
            val token = SessionToken.regenerate()
            val payload = QrPayload(
                name = Build.MODEL.take(20),
                ip = ip,
                port = Constants.TRANSFER_PORT,
                token = token,
                ssid = if (creds) ssid else "",
                pass = if (creds) pass else ""
            )
            showQr(payload, creds)
            receive(token)
        }
    }

    private fun receive(token: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val srv = appContainer.transferServer
            val watcher = launch {
                srv.progress.collect { p ->
                    if (p.isRunning && view != null) {
                        binding.tvDeviceName.text = "جارٍ الاستلام..."
                        binding.tvPayload.text = "${p.currentFileName}\n${p.percent}%"
                    }
                }
            }
            val result = srv.receiveOnce(token)
            watcher.cancel()
            if (view == null) return@launch
            val files = result.getOrNull()
            if (files != null && files.isNotEmpty()) {
                files.forEach { f ->
                    appContainer.historyRepository.add(
                        TransferRecord(
                            id = UUID.randomUUID().toString(),
                            fileName = f.name,
                            sizeBytes = f.length(),
                            direction = "RECEIVE",
                            peerName = "Peer",
                            timestampMs = System.currentTimeMillis(),
                            success = true,
                            savedPath = f.absolutePath
                        )
                    )
                }
                binding.ivQr.setImageDrawable(null)
                binding.tvDeviceName.text = "تم استلام ${files.size} ملف ✅"
                binding.tvPayload.text = files.joinToString("\n") { it.name }
                binding.btnStartReceive.text = "تم"
                requireContext().toast("تم استلام ${files.size} ملف!")
            } else {
                showError("فشل الاستقبال: " + (result.exceptionOrNull()?.message ?: "لم يصل أي ملف"))
            }
        }
    }

    private fun showLoading(msg: String) {
        if (view == null) return
        binding.errorUi.visibility = View.GONE
        binding.manualUi.visibility = View.GONE
        binding.successUi.visibility = View.GONE
        binding.loadingUi.visibility = View.VISIBLE
        binding.tvLoadingMsg.text = msg
    }

    private fun showError(msg: String) {
        if (view == null) return
        binding.loadingUi.visibility = View.GONE
        binding.successUi.visibility = View.GONE
        binding.manualUi.visibility = View.GONE
        binding.errorUi.visibility = View.VISIBLE
        binding.tvError.text = msg
    }

    private fun showQr(p: QrPayload, hotspot: Boolean) {
        if (view == null) return
        binding.loadingUi.visibility = View.GONE
        binding.errorUi.visibility = View.GONE
        binding.manualUi.visibility = View.GONE
        binding.successUi.visibility = View.VISIBLE
        binding.ivQr.setImageBitmap(QrEncoder.encode(p.encode(), 720))
        binding.tvDeviceName.text =
            if (hotspot) "امسح الرمز من جهاز المرسِل"
            else "تأكد أن الجهازين على نفس الشبكة ثم امسح الرمز"
        binding.tvPayload.text =
            if (p.ssid.isNotEmpty()) "الشبكة: ${p.ssid}\nكلمة المرور: ${p.pass}\nIP: ${p.ip}"
            else "IP: ${p.ip}"
        binding.btnStartReceive.text = "إلغاء"
        binding.btnStartReceive.isEnabled = true
    }

    private fun localIpv4s(): List<Pair<String, String>> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni ->
                ni.inetAddresses.toList()
                    .filter { it is Inet4Address && it.isSiteLocalAddress }
                    .map { ni.name to (it.hostAddress ?: "") }
            }
            .filter { it.second.isNotEmpty() }
    } catch (_: Exception) { emptyList() }

    private fun hotspotIp(): String? = localIpv4s().firstOrNull { (n, _) ->
        n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("softap") ||
            n == "wlan1" || n.contains("p2p")
    }?.second

    private fun wifiIp(): String? =
        (localIpv4s().firstOrNull { it.first.startsWith("wlan") } ?: localIpv4s().firstOrNull())?.second

    override fun onDestroyView() {
        try { appContainer.hotspotManager.stopHotspot() } catch (_: Exception) {}
        super.onDestroyView()
    }
}
