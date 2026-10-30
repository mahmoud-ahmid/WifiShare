package com.example.wifishare.ui.send

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.core.toast
import com.example.wifishare.data.files.PendingSendHolder
import com.example.wifishare.databinding.FragmentSendFindBinding
import com.example.wifishare.ui.common.BaseFragment
import com.example.wifishare.ui.transfer.TransferFragment

class SendFindFragment : BaseFragment<FragmentSendFindBinding>(
    { i, p, a -> FragmentSendFindBinding.inflate(i, p, a) }
) {
    private var pendingUris: List<Uri> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val arr = requireArguments().getStringArray("uris") ?: emptyArray()
        pendingUris = arr.map { Uri.parse(it) }

        // إخفاء عناصر البحث التلقائي (P2P) لأنها لا تعمل
        binding.tvStatus.text = "اتصل بشبكة WifiShare من إعدادات الواي فاي أولاً، ثم أدخل الـ IP هنا"
        binding.progress.visibility = View.GONE
        binding.btnRefresh.visibility = View.GONE
        binding.btnScanQr.visibility = View.GONE
        binding.rvDevices.visibility = View.GONE
        binding.emptyState.visibility = View.VISIBLE

        // زر لفتح إعدادات الواي فاي
        binding.btnOpenSettings.visibility = View.VISIBLE
        binding.btnOpenSettings.text = "فتح إعدادات الواي فاي"
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        // حقول إدخال IP يدوياً
        binding.etIp.visibility = View.VISIBLE
        binding.btnConnectIp.visibility = View.VISIBLE
        binding.etIp.setText("192.168.43.1") // القيمة الافتراضية

        binding.btnConnectIp.setOnClickListener {
            val ip = binding.etIp.text.toString().trim()
            if (ip.isEmpty()) {
                requireContext().toast("أدخل عنوان IP صالح")
                return@setOnClickListener
            }
            PendingSendHolder.set(pendingUris)
            findNavController().navigate(
                R.id.action_sendFind_to_transfer,
                bundleOf(
                    TransferFragment.ARG_MODE to "SEND",
                    TransferFragment.ARG_HOST_IP to ip
                )
            )
        }
        
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }
    }
}
