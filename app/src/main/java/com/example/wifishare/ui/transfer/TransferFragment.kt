package com.example.wifishare.ui.transfer

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.toast
import com.example.wifishare.data.transfer.TransferProgress
import com.example.wifishare.domain.model.TransferDirection
import com.example.wifishare.databinding.FragmentTransferBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.launch

class TransferFragment : BaseFragment<FragmentTransferBinding>({ i, p, a -> FragmentTransferBinding.inflate(i, p, a) }) {

    private val direction: TransferDirection by lazy {
        when (requireArguments().getString(ARG_MODE)?.uppercase()) {
            "RECEIVE" -> TransferDirection.RECEIVE
            else -> TransferDirection.SEND
        }
    }
    private val hostIp: String by lazy {
        requireArguments().getString(ARG_HOST_IP).orEmpty()
    }
    private val token: String by lazy {
        requireArguments().getString(ARG_TOKEN).orEmpty()
    }

    private var cancelBtn: View? = null

    private val viewModel: TransferViewModel by viewModels {
        TransferViewModel.Factory(
            application = requireActivity().application,
            container = (requireActivity().application as WifiShareApp).container,
            direction = direction,
            hostIp = hostIp,
            token = token
        )
    }

    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            binding.btnPick.isVisible = false
            viewModel.sendFiles(uris)
        } else {
            requireContext().toast(getString(R.string.transfer_no_files))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvMode.text = when (direction) {
            TransferDirection.SEND -> getString(R.string.transfer_mode_send)
            TransferDirection.RECEIVE -> getString(R.string.transfer_mode_receive)
        }
        binding.tvHost.text = if (hostIp.isNotBlank())
            getString(R.string.transfer_host, hostIp)
        else getString(R.string.transfer_waiting)

        binding.btnPick.isVisible = direction == TransferDirection.SEND
        // زر الإلغاء يُنشأ من الكود (لا يعتمد على ملف الواجهة)
        val cancel = com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = getString(android.R.string.cancel)
            visibility = View.GONE
            setOnClickListener { viewModel.cancel() }
        }
        (binding.btnDone.parent as android.view.ViewGroup).let { parent ->
            parent.addView(cancel, parent.indexOfChild(binding.btnDone))
        }
        cancelBtn = cancel
        binding.btnPick.setOnClickListener {
            pickFilesLauncher.launch(arrayOf("*/*"))
        }
        binding.btnDone.setOnClickListener {
            findNavController().popBackStack()
        }

        // الاستقبال يبدأ تلقائيًا.
        if (direction == TransferDirection.RECEIVE && savedInstanceState == null) {
            viewModel.startReceiving()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.progress.collect { render(it) }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.savedFiles.collect { files ->
                    if (files.isNotEmpty()) {
                        binding.tvSaved.text = getString(
                            R.string.transfer_saved,
                            files.joinToString("\n") { it.absolutePath }
                        )
                        binding.tvSaved.isVisible = true
                    }
                }
            }
        }
    }

    private fun render(p: TransferProgress) {
        cancelBtn?.isVisible = p.isRunning
        binding.progress.progress = p.percent
        binding.tvPercent.text = "${p.percent}%"

        when {
            p.errorMessage != null -> {
                binding.tvStatus.text = getString(R.string.transfer_error, p.errorMessage)
                binding.progress.isVisible = false
                binding.btnDone.isVisible = true
            }
            p.isDone -> {
                binding.tvStatus.text = getString(R.string.transfer_done)
                binding.progress.progress = 100
                binding.btnDone.isVisible = true
                binding.btnPick.isVisible = false
            }
            p.isRunning -> {
                binding.tvStatus.text = getString(R.string.transfer_running)
                binding.tvCurrentFile.text = p.currentFileName
                binding.tvSpeed.text = getString(R.string.transfer_speed, p.bytesPerSecond / 1024 / 1024)
                binding.tvEta.text = getString(R.string.transfer_eta, p.etaSeconds)
                binding.progress.isVisible = true
                binding.btnDone.isVisible = false
            }
            else -> {
                binding.tvStatus.text = if (direction == TransferDirection.RECEIVE)
                    getString(R.string.transfer_waiting_for_sender)
                else getString(R.string.transfer_pick_prompt)
            }
        }
    }

    companion object {
        const val ARG_MODE = "mode"
        const val ARG_HOST_IP = "hostIp"
        const val ARG_TOKEN = "token"
    }
}
