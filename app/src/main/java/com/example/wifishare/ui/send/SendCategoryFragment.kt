package com.example.wifishare.ui.send

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wifishare.R
import com.example.wifishare.core.FileAccessHelper
import com.example.wifishare.data.send.SendItem
import com.example.wifishare.databinding.FragmentSendCategoryBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.launch
import java.io.File

class SendCategoryFragment : BaseFragment<FragmentSendCategoryBinding>(
    { i, p, a -> FragmentSendCategoryBinding.inflate(i, p, a) }
) {
    private val parentVm: SendPickViewModel by activityViewModels {
        SendPickViewModel.Factory(requireActivity().application)
    }

    private var category: SendItem.Category = SendItem.Category.IMAGES
    private var currentFolderPath: String? = null
    private var allItems: List<SendItem> = emptyList()
    private lateinit var adapter: SendItemAdapter
    private var permissionDialogShown = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        category = SendItem.Category.valueOf(requireArguments().getString(ARG_CATEGORY)!!)

        adapter = SendItemAdapter(parentVm.selection) { item ->
            when {
                item.mimeType == "inode/directory" && item.name == ".." -> navigateUp()
                item.mimeType == "inode/directory" -> {
                    currentFolderPath = item.sourcePath; loadData()
                }
                else -> parentVm.selection.toggle(item)
            }
        }
        binding.rvItems.layoutManager = LinearLayoutManager(requireContext())
        binding.rvItems.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            parentVm.selection.countFlow.collect {
                if (::adapter.isInitialized) adapter.refreshSelection()
            }
        }

        if (category == SendItem.Category.FILES && FileAccessHelper.needsRequest()) {
            showPermissionDialog()
        } else {
            loadData()
        }
    }

    override fun onResume() {
        super.onResume()
        if (category == SendItem.Category.FILES && !FileAccessHelper.hasFullAccess()) {
            showPermissionDialog()
        } else {
            loadData()
        }
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.progress.isVisible = true
            val list: List<SendItem> = try {
                if (category == SendItem.Category.FILES) {
                    if (!FileAccessHelper.hasFullAccess()) emptyList()
                    else {
                        val acc = mutableListOf<SendItem>()
                        currentFolderPath?.let { p ->
                            val parent = File(p).parent
                            if (parent != null) {
                                acc.add(SendItem(
                                    uri = Uri.parse("parent://$p"), name = "..",
                                    sizeBytes = 0L, mimeType = "inode/directory",
                                    category = SendItem.Category.FILES, sourcePath = parent
                                ))
                            }
                        }
                        acc.addAll(parentVm.listDir(
                            currentFolderPath ?: Environment.getExternalStorageDirectory().absolutePath
                        ))
                        acc
                    }
                } else parentVm.loadCategory(category)
            } catch (e: Exception) { emptyList() }

            allItems = list
            adapter.submit(list, category)
            binding.progress.isVisible = false
            binding.emptyState.isVisible = list.isEmpty()
            binding.rvItems.isVisible = list.isNotEmpty()
            binding.tvPath.isVisible = currentFolderPath != null
            binding.tvPath.text = currentFolderPath ?: ""
        }
    }

    private fun navigateUp() {
        val p = currentFolderPath ?: return
        val parent = File(p).parent
        currentFolderPath = if (parent == null || parent == Environment.getExternalStorageDirectory().absolutePath) null else parent
        loadData()
    }

    private fun showPermissionDialog() {
        if (permissionDialogShown || !isAdded) return
        permissionDialogShown = true
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.storage_perm_title)
            .setMessage(R.string.storage_perm_message)
            .setPositiveButton(R.string.storage_perm_open) { _, _ ->
                FileAccessHelper.openSettings(requireContext())
            }
            .setCancelable(false)
            .show()
    }

    fun selectAll() {
        if (allItems.isEmpty()) return
        if (parentVm.selection.allSelected(allItems)) parentVm.selection.clear()
        else parentVm.selection.selectAll(allItems)
    }

    fun onBackPressed(): Boolean {
        if (currentFolderPath != null) { navigateUp(); return true }
        return false
    }

    fun currentItems(): List<SendItem> = allItems

    companion object { const val ARG_CATEGORY = "category" }
}
