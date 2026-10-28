package com.example.wifishare.ui.qr

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.Constants
import com.example.wifishare.core.SessionToken
import com.example.wifishare.data.qr.QrEncoder
import com.example.wifishare.data.qr.QrPayload
import com.example.wifishare.data.wifi.WifiP2pState
import com.example.wifishare.databinding.FragmentQrDisplayBinding
import com.example.wifishare.ui.common.BaseFragment
import com.example.wifishare.ui.transfer.TransferFragment

class QrDisplayFragment : BaseFragment<FragmentQrDisplayBinding>(
    { i, p, a -> FragmentQrDisplayBinding.inflate(i, p, a) }
) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = (requireActivity().application as WifiShareApp).container
        val state = container.wifiP2pManager.state.value
        val ip = when (state) {
            is WifiP2pState.Connected -> state.groupOwnerAddress
            else -> "192.168.49.1"
        }
        val deviceName = (Build.MODEL ?: "Android").take(20)
        val token = SessionToken.regenerate()
        val text = QrPayload(name = deviceName, ip = ip, port = Constants.TRANSFER_PORT, token = token).encode()

        binding.ivQr.setImageBitmap(QrEncoder.encode(text, 720))
        binding.tvDeviceName.text = getString(R.string.qr_display_device, deviceName)
        binding.tvPayload.visibility = View.GONE   // النص الخام يحتوي التوكن

        binding.btnStartReceive.setOnClickListener {
            findNavController().navigate(
                R.id.action_qrDisplay_to_transfer,
                bundleOf(
                    TransferFragment.ARG_MODE to "RECEIVE",
                    TransferFragment.ARG_HOST_IP to ""
                )
            )
        }
    }
}
