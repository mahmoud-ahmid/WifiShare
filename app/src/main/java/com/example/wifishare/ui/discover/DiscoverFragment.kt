package com.example.wifishare.ui.discover

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.toast
import com.example.wifishare.data.wifi.WifiP2pState
import com.example.wifishare.databinding.FragmentDiscoverBinding
import com.example.wifishare.ui.common.BaseFragment
import com.example.wifishare.ui.transfer.TransferFragment
import kotlinx.coroutines.launch

class DiscoverFragment : BaseFragment<FragmentDiscoverBinding>({ i, p, a -> FragmentDiscoverBinding.inflate(i, p, a) }) {

    private val viewModel: DiscoverViewModel by viewModels {
        val app = requireActivity().application as WifiShareApp
        DiscoverViewModel.Factory(
            discover = app.container.discoverPeersUseCase,
            connect = app.container.connectToPeerUseCase
        )
    }

    private var lastConnected: WifiP2pState.Connected? = null

    private val adapter = DevicesAdapter { device ->
        requireContext().toast(getString(R.string.connecting_to, device.name))
        viewModel.connectTo(device)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDevices.adapter = adapter

        binding.btnToggleDiscovery.setOnClickListener {
            if (viewModel.state.value is WifiP2pState.Discovering) viewModel.stopDiscovery()
            else viewModel.startDiscovery()
        }
        binding.btnContinue.setOnClickListener { navigateToTransfer() }
        binding.btnDisconnect.setOnClickListener {
            viewModel.disconnect()
            lastConnected = null
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopDiscovery()
    }

    private fun navigateToTransfer() {
        val connected = lastConnected ?: return
        val mode = if (connected.isGroupOwner) "RECEIVE" else "SEND"
        findNavController().navigate(
            R.id.action_discover_to_transfer,
            bundleOf(
                TransferFragment.ARG_MODE to mode,
                TransferFragment.ARG_HOST_IP to connected.groupOwnerAddress
            )
        )
    }

    private fun render(state: WifiP2pState) {
        binding.progress.isVisible =
            state is WifiP2pState.Discovering || state is WifiP2pState.Connecting

        binding.tvStatus.text = when (state) {
            WifiP2pState.Idle -> getString(R.string.discover_status_idle)
            WifiP2pState.WifiDisabled -> getString(R.string.discover_status_wifi_disabled)
            WifiP2pState.NotSupported -> getString(R.string.discover_status_not_supported)
            WifiP2pState.Discovering -> getString(R.string.discover_status_discovering)
            is WifiP2pState.PeersAvailable -> getString(R.string.discover_status_peers, state.peers.size)
            is WifiP2pState.Connecting -> getString(R.string.discover_status_connecting, state.device.name)
            is WifiP2pState.Connected -> getString(R.string.discover_status_connected, state.groupOwnerAddress)
            is WifiP2pState.Failed -> getString(R.string.discover_status_failed, state.reason)
        }

        lastConnected = state as? WifiP2pState.Connected
        binding.btnContinue.isVisible = lastConnected != null
        binding.btnDisconnect.isVisible = lastConnected != null

        adapter.submitList(
            (state as? WifiP2pState.PeersAvailable)?.peers ?: emptyList()
        )
        binding.btnToggleDiscovery.text =
            if (state is WifiP2pState.Discovering) getString(R.string.discover_stop)
            else getString(R.string.discover_start)
        binding.btnToggleDiscovery.isVisible = lastConnected == null
    }
}
