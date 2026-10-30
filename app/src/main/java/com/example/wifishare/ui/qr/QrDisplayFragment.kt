package com.example.wifishare.ui.qr

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.Constants
import com.example.wifishare.core.toast
import com.example.wifishare.data.qr.QrEncoder
import com.example.wifishare.databinding.FragmentQrDisplayBinding
import com.example.wifishare.ui.common.BaseFragment
import com.example.wifishare.ui.transfer.TransferFragment
import kotlinx.coroutines.launch

class QrDisplayFragment : BaseFragment<FragmentQrDisplayBinding>(
    { i, p, a -> FragmentQrDisplayBinding.inflate(i, p, a) }
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val container = (requireActivity().application as WifiShareApp).container

        // إخفاء حالة التحميل والخطأ القديمة
        binding.loadingUi.visibility = View.GONE
        binding.errorUi.visibility = View.GONE
        binding.manualUi.visibility = View.GONE
        binding.successUi.visibility = View.VISIBLE

        // نطلب من المستخدم إنشاء نقطة اتصال يدوياً
        val ssid = "WifiShare_${android.os.Build.MODEL.take(6)}"
        val password = "12345678"
        val ip = "192.168.43.1" // IP الافتراضي لنقطة اتصال أندرويد

        // توليد رمز QR قياسي (يمكن لأي كاميرا هاتف قراءته للاتصال بالواي فاي)
        // الصيغة القياسية: WIFI:S:SSID;T:WPA;P:PASSWORD;;
        val wifiQr = "WIFI:S:$ssid;T:WPA;P:$password;;"
        
        binding.ivQr.setImageBitmap(QrEncoder.encode(wifiQr, 720))
        binding.tvDeviceName.text = "اسم النقطة: $ssid"
        binding.tvPayload.text = "كلمة المرور: $password\nIP: $ip"
        
        binding.btnStartReceive.text = "فتح إعدادات النقطة"
        binding.btnStartReceive.isEnabled = true
        binding.btnStartReceive.setOnClickListener {
            try {
                // فتح إعدادات النقطة الساخنة (تختلف حسب الشركة، لكن هذا الـ intent عام)
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                requireContext().toast("قم بتشغيل النقطة يدوياً باسم وكلمة مرور كما يظهران في الشاشة")
            } catch (_: Exception) {}
        }

        // بدء الاستقبال في الخلفية فوراً
        viewLifecycleOwner.lifecycleScope.launch {
            val result = container.transferServer.receiveOnce()
            if (!isAdded) return@launch
            result.onSuccess { files ->
                if (files.isNotEmpty()) {
                    requireContext().toast("تم استلام ${files.size} ملف!")
                    findNavController().navigate(
                        R.id.action_qrDisplay_to_transfer,
                        bundleOf(
                            TransferFragment.ARG_MODE to "RECEIVE",
                            TransferFragment.ARG_HOST_IP to ""
                        )
                    )
                }
            }
            result.onFailure { e ->
                requireContext().toast("فشل الاستقبال: ${e.message}")
            }
        }
    }
}
