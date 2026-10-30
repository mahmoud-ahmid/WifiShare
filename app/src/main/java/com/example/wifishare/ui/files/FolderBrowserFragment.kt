package com.example.wifishare.ui.files

import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.wifishare.R
import com.example.wifishare.WifiShareApp
import com.example.wifishare.core.FileAccessHelper
import com.example.wifishare.core.toast
import com.example.wifishare.data.files.MediaItem
import com.example.wifishare.data.files.PendingSendHolder
import com.example.wifishare.data.files.Source
import com.example.wifishare.databinding.FragmentFolderBrowserBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.launch
import java.io.File

class FolderBrowserFragment : BaseFragment<FragmentFolderBrowserBinding>(
    { i, p, a -> FragmentFolderBrowserBinding.inflate(i, p, a) }
) {
    private val container by lazy { (requireActivity().application as WifiShareApp).container }
    private var currentPath: String? = null
    private val selection = ItemSelection()
    private lateinit var adapter: MediaAdapter
    private var permissionDialogShown = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MediaAdapter(
            onClick = { if (selection.isEmpty()) onItemClick(it) else toggle(it) },
            onLongClick = { toggle(it) },
            onMoreClick = { item, v -> showMore(item, v) },
            isSelected = { selection.isSelected(it.uri) },
            selectionMode = { !selection.isEmpty() }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { navigateUp() }
        binding.btnUp.setOnClickListener { navigateUp() }

        binding.btnSelClose.setOnClickListener {
            selection.clear(); adapter.notifyDataSetChanged(); updateBottomBar()
        }
        binding.btnSelSend.setOnClickListener { sendSelected() }
        binding.btnSelDelete.setOnClickListener { deleteSelected() }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        permissionDialogShown = false
        if (FileAccessHelper.hasFullAccess()) loadData()
        else showPermissionDialog()
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
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                findNavController().popBackStack()
            }
            .setCancelable(false)
            .show()
    }

    private fun loadData() {
        if (!FileAccessHelper.hasFullAccess()) {
            binding.emptyState.isVisible = true
            binding.rvFiles.isVisible = false
            binding.tvPath.text = getString(R.string.storage_perm_needed)
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val path = currentPath ?: Environment.getExternalStorageDirectory().absolutePath
            binding.tvPath.text = path
            binding.progress.isVisible = true
            val sendItems = container.sendRepository.listDir(path)
            val items = sendItems.map { si ->
                MediaItem(
                    uri = si.uri, name = si.name, sizeBytes = si.sizeBytes,
                    mimeType = si.mimeType, modifiedMs = System.currentTimeMillis(),
                    source = Source.FOLDERS, filePath = si.sourcePath
                )
            }.toMutableList()

            if (currentPath != null) {
                val parentPath = File(path).parent
                if (parentPath != null) {
                    items.add(0, MediaItem(
                        uri = Uri.parse("parent://"), name = "..",
                        sizeBytes = 0L, mimeType = "inode/directory",
                        modifiedMs = 0L, source = Source.FOLDERS,
                        filePath = parentPath
                    ))
                }
            }
            adapter.submitList(items)
            binding.progress.isVisible = false
            binding.emptyState.isVisible = items.isEmpty()
            binding.rvFiles.isVisible = items.isNotEmpty()
            updateBottomBar()
        }
    }

    private fun onItemClick(item: MediaItem) {
        if (item.mimeType == "inode/directory") {
            if (item.name == "..") navigateUp()
            else if (item.filePath != null) { currentPath = item.filePath; loadData() }
        } else openFile(item)
    }

    private fun navigateUp() {
        val path = currentPath ?: run { findNavController().popBackStack(); return }
        val parent = File(path).parent
        currentPath = if (parent == null || parent == Environment.getExternalStorageDirectory().absolutePath) null else parent
        loadData()
    }

    private fun openFile(item: MediaItem) {
        val ctx = requireContext()
        when {
            item.mimeType.startsWith("image/") ->
                startActivity(com.example.wifishare.ui.viewer.ImageViewerActivity.newIntent(ctx, item.uri, item.name))
            item.mimeType == "application/pdf" ->
                startActivity(com.example.wifishare.ui.viewer.PdfViewerActivity.newIntent(ctx, item.uri, item.name))
            item.mimeType.startsWith("video/") ->
                ctx.toast(getString(R.string.media_video_disabled))
            item.mimeType.startsWith("audio/") ->
                ctx.toast("تشغيل الصوت معطل")
            else -> try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(item.uri, item.mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newRawUri("", item.uri)
                }, getString(R.string.files_open_with)))
            } catch (e: Exception) {
                ctx.toast(getString(R.string.files_cant_open, e.message ?: ""))
            }
        }
    }

    private fun toggle(item: MediaItem) {
        if (item.mimeType == "inode/directory" && item.name == "..") return
        selection.toggle(item.uri)
        adapter.notifyDataSetChanged()
        updateBottomBar()
    }

    private fun updateBottomBar() {
        val n = selection.size
        binding.selectionBar.isVisible = n > 0
        binding.tvSelected.text = if (n > 0) getString(R.string.files_selected_count, n) else ""
    }

    private fun showMore(item: MediaItem, anchor: View) {
        if (item.mimeType == "inode/directory" && item.name == "..") return
        val actions = arrayOf(
            getString(R.string.files_action_open),
            getString(R.string.files_action_share),
            getString(R.string.files_action_send),
            getString(R.string.files_action_info)
        )
        android.widget.PopupMenu(requireContext(), anchor).apply {
            actions.forEachIndexed { i, l -> menu.add(0, i, i, l) }
            setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    0 -> onItemClick(item)
                    1 -> shareItem(item)
                    2 -> {
                        PendingSendHolder.set(listOf(item.uri))
                        requireContext().toast(getString(R.string.files_send_ready))
                        findNavController().navigate(R.id.homeFragment)
                    }
                    3 -> showInfo(item)
                }
                true
            }
            show()
        }
    }

    private fun shareItem(item: MediaItem) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", item.uri)
            }, getString(R.string.files_share_with)))
        } catch (e: Exception) {
            requireContext().toast(getString(R.string.files_cant_share, e.message ?: ""))
        }
    }

    private fun sendSelected() {
        val uris = selection.selected.toList()
        if (uris.isEmpty()) return
        PendingSendHolder.set(uris)
        requireContext().toast(getString(R.string.files_send_ready))
        selection.clear()
        findNavController().navigate(R.id.homeFragment)
    }

    private fun deleteSelected() {
        val uris = selection.selected.toList()
        if (uris.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_delete_title)
            .setMessage(getString(R.string.files_delete_confirm_multi, uris.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                var ok = 0
                uris.forEach { uri ->
                    val f = File(uri.path ?: return@forEach)
                    if (f.exists() && f.delete()) ok++
                }
                selection.clear()
                requireContext().toast(getString(R.string.files_deleted_multi, ok))
                loadData()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showInfo(item: MediaItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_action_info)
            .setMessage(getString(R.string.files_info, item.name, humanSize(item.sizeBytes),
                item.mimeType, item.filePath ?: item.uri.toString()))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun humanSize(b: Long) = when {
        b < 1024 -> "$b B"
        b < 1024*1024 -> "%.1f KB".format(b/1024.0)
        b < 1024L*1024*1024 -> "%.1f MB".format(b/1024.0/1024.0)
        else -> "%.2f GB".format(b/1024.0/1024.0/1024.0)
    }
}
