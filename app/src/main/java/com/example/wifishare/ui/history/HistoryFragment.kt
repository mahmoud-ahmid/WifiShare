package com.example.wifishare.ui.history

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.toast
import com.example.wifishare.data.history.TransferRecord
import com.example.wifishare.databinding.FragmentHistoryBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.launch
import java.io.File

class HistoryFragment : BaseFragment<FragmentHistoryBinding>(
    { i, p, a -> FragmentHistoryBinding.inflate(i, p, a) }
) {
    private val container by lazy { (requireActivity().application as WifiShareApp).container }
    private val adapter = HistoryAdapter { record -> openRecord(record) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.historyRepository.records.collect { list ->
                    adapter.submitList(list)
                    val empty = list.isEmpty()
                    binding.emptyState.isVisible = empty
                    binding.rvHistory.isVisible = !empty
                }
            }
        }
    }

    private fun openRecord(r: TransferRecord) {
        val path = r.savedPath
        if (path.isNullOrBlank()) {
            requireContext().toast(getString(R.string.history_no_file))
            return
        }
        val file = File(path)
        if (!file.exists()) {
            requireContext().toast(getString(R.string.history_file_missing))
            return
        }
        try {
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val mime = requireContext().contentResolver.getType(uri) ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.history_open_with)))
        } catch (e: Exception) {
            requireContext().toast(getString(R.string.history_cant_open, e.message ?: ""))
        }
    }
}
