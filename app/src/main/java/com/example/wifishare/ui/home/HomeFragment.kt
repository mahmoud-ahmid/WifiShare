package com.example.wifishare.ui.home

import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.core.PermissionHelper
import com.example.wifishare.core.toast
import com.example.wifishare.databinding.FragmentHomeBinding
import com.example.wifishare.ui.common.BaseFragment

class HomeFragment : BaseFragment<FragmentHomeBinding>({ i, p, a -> FragmentHomeBinding.inflate(i, p, a) }) {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissionState()
        if (PermissionHelper.hasAll(requireContext(), PermissionHelper.wifiPermissions()))
            requireContext().toast(getString(R.string.home_perm_granted))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.cardPermissions.setOnClickListener { requestPermissions() }
        binding.cardSend.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_discover)
        }
        binding.cardReceive.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_qrDisplay)
        }
        binding.cardQr.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_qrScanner)
        }
        refreshPermissionState()
    }

    override fun onResume() { super.onResume(); refreshPermissionState() }

    private fun requestPermissions() {
        val missing = PermissionHelper.missing(requireContext(), PermissionHelper.wifiPermissions())
        if (missing.isEmpty()) requireContext().toast(getString(R.string.home_perm_granted))
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun refreshPermissionState() {
        val granted = PermissionHelper.hasAll(requireContext(), PermissionHelper.wifiPermissions())
        binding.cardPermissions.isVisible = !granted
        binding.tvPermissionStatus.text = if (granted)
            getString(R.string.home_perm_granted)
        else getString(R.string.home_perm_missing)
    }
}
