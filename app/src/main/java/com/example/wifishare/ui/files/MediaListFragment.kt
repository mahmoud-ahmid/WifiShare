package com.example.wifishare.ui.files

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.EditText
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
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
import com.example.wifishare.data.send.SendItem
import com.example.wifishare.databinding.FragmentMediaListBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MediaListFragment : BaseFragment<FragmentMediaListBinding>(
    { i, p, a -> FragmentMediaListBinding.inflate(i, p, a) }
) {
    private val container by lazy { (requireActivity().application as WifiShareApp).container }
    private val selection = ItemSelection()
    private var source: Source = Source.RECEIVED
    private var allItems: List<MediaItem> = emptyList()
    private var pendingRename: Pair<MediaItem, String>? = null
    private var currentFolderPath: String? = null

    private lateinit var adapter: MediaAdapter

    private val writeRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val p = pendingRename; pendingRename = null
        if (result.resultCode == Activity.RESULT_OK && p != null) directRename(p.first, p.second)
        else if (p != null) requireContext().toast(getString(R.string.files_rename_failed))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        source = Source.valueOf(requireArguments().getString(ARG_SOURCE, Source.RECEIVED.name))

        adapter = MediaAdapter(
            onClick = { if (selection.isEmpty()) openItem(it) else toggleSelection(it) },
            onLongClick = { toggleSelection(it) },
            onMoreClick = { item, v -> showMoreMenu(item, v) },
            isSelected = { selection.isSelected(it.uri) },
            selectionMode = { !selection.isEmpty() }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter

        binding.btnSelClose.setOnClickListener { exitSelection() }
        binding.cbSelectAll.setOnClickListener {
            if (binding.cbSelectAll.isChecked) selection.selectAll(allItems)
            else selection.clear()
            refreshSelectionUi()
            adapter.notifyDataSetChanged()
        }
        binding.btnOverflow.setOnClickListener { anchor -> showOverflowMenu(anchor) }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (source == Source.RECEIVED || source == Source.FOLDERS || source == Source.APPS) loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            allItems = when (source) {
                Source.RECEIVED -> {
                    container.receivedFilesRepository.refresh()
                    container.receivedFilesRepository.files.value.map { rf ->
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            rf.file
                        )
                        MediaItem(
                            uri = uri, name = rf.name, sizeBytes = rf.sizeBytes,
                            mimeType = requireContext().contentResolver.getType(uri) ?: "*/*",
                            modifiedMs = rf.modifiedMs, source = Source.RECEIVED, filePath = rf.file.absolutePath
                        )
                    }
                }
                Source.IMAGES -> container.mediaStoreRepository.images()
                Source.VIDEOS -> container.mediaStoreRepository.videos()
                Source.AUDIO -> container.mediaStoreRepository.audio()
                Source.DOCUMENTS -> container.mediaStoreRepository.documents()
                Source.FOLDERS -> loadFolders()
                Source.APPS -> loadApps()
            }
            adapter.submitList(allItems)
            binding.emptyState.isVisible = allItems.isEmpty()
            binding.rvFiles.isVisible = allItems.isNotEmpty()
            refreshSelectionUi()
        }
    }

    private suspend fun loadFolders(): List<MediaItem> {
        if (!FileAccessHelper.hasFullAccess()) return emptyList()
        val sendItems = if (currentFolderPath == null)
            container.sendRepository.load(SendItem.Category.FILES)
        else container.sendRepository.listDir(currentFolderPath!!)
        val items = sendItems.map { si ->
            MediaItem(
                uri = si.uri, name = si.name, sizeBytes = si.sizeBytes,
                mimeType = si.mimeType, modifiedMs = System.currentTimeMillis(),
                source = Source.FOLDERS, filePath = si.sourcePath
            )
        }.toMutableList()
        currentFolderPath?.let { path ->
            val parent = File(path).parentFile
            if (parent != null && parent.absolutePath != Environment.getExternalStorageDirectory().absolutePath) {
                items.add(0, MediaItem(
                    uri = Uri.parse("parent://"), name = "..", sizeBytes = 0L,
                    mimeType = "inode/directory", modifiedMs = System.currentTimeMillis(),
                    source = Source.FOLDERS, filePath = parent.absolutePath
                ))
            }
        }
        return items
    }

    private suspend fun loadApps(): List<MediaItem> {
        return container.sendRepository.load(SendItem.Category.APPLICATIONS).map { si ->
            MediaItem(
                uri = si.uri, name = si.name, sizeBytes = si.sizeBytes,
                mimeType = si.mimeType, modifiedMs = System.currentTimeMillis(),
                source = Source.APPS, filePath = si.sourcePath,
                appIcon = si.appIcon, isInstalledApp = si.isInstalledApp
            )
        }
    }

    private fun navigateInto(path: String) { currentFolderPath = path; loadData() }
    private fun navigateUp() {
        val p = currentFolderPath ?: return
        val parent = File(p).parent
        currentFolderPath = if (parent == null || parent == Environment.getExternalStorageDirectory().absolutePath) null else parent
        loadData()
    }

    private fun toggleSelection(item: MediaItem) {
        if (item.mimeType == "inode/directory" && item.name == "..") return
        selection.toggle(item.uri)
        refreshSelectionUi()
        adapter.notifyDataSetChanged()
    }

    private fun exitSelection() {
        selection.clear()
        refreshSelectionUi()
        adapter.notifyDataSetChanged()
    }

    private fun refreshSelectionUi() {
        val n = selection.size
        binding.selectionBar.isVisible = n > 0
        binding.tvSelected.text = if (n > 0) getString(R.string.files_selected_count, n) else ""
        binding.cbSelectAll.isChecked = allItems.isNotEmpty() && selection.allSelected(allItems)
    }

    private fun selectedItems() = allItems.filter { selection.isSelected(it.uri) }

    private fun openItem(item: MediaItem) {
        val ctx = requireContext()
        if (item.source == Source.FOLDERS && item.mimeType == "inode/directory") {
            if (item.name == "..") navigateUp()
            else if (item.filePath != null) navigateInto(item.filePath)
            return
        }
        when {
            item.mimeType.startsWith("image/") ->
                startActivity(com.example.wifishare.ui.viewer.ImageViewerActivity.newIntent(ctx, item.uri, item.name))
            item.mimeType == "application/pdf" ->
                startActivity(com.example.wifishare.ui.viewer.PdfViewerActivity.newIntent(ctx, item.uri, item.name))
            item.mimeType.startsWith("video/") ->
                requireContext().toast(getString(R.string.media_video_disabled))
            item.mimeType.startsWith("audio/") -> showAudioActions(item)
            else -> openExternally(item)
        }
    }

    private fun openExternally(item: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(item.uri, item.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("", item.uri)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.files_open_with)))
        } catch (e: Exception) { requireContext().toast(getString(R.string.files_cant_open, e.message ?: "no app")) }
    }

    private fun showAudioActions(item: MediaItem) {
        val options = arrayOf(
            getString(R.string.files_action_ringtone),
            getString(R.string.files_action_notification),
            getString(R.string.files_action_alarm),
            getString(R.string.files_action_share),
            getString(R.string.files_action_info)
        )
        AlertDialog.Builder(requireContext()).setTitle(item.name)
            .setItems(options) { _, w ->
                when (w) {
                    0 -> setAsRingtone(item, android.media.RingtoneManager.TYPE_RINGTONE)
                    1 -> setAsRingtone(item, android.media.RingtoneManager.TYPE_NOTIFICATION)
                    2 -> setAsRingtone(item, android.media.RingtoneManager.TYPE_ALARM)
                    3 -> shareItem(item)
                    4 -> showInfo(item)
                }
            }.show()
    }

    private fun setAsRingtone(item: MediaItem, type: Int) {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.System.canWrite(ctx)) {
            AlertDialog.Builder(ctx).setTitle(R.string.files_grant_write_title)
                .setMessage(R.string.files_grant_write_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:" + ctx.packageName); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                .setNegativeButton(android.R.string.cancel, null).show()
            return
        }
        try {
            val m = android.media.RingtoneManager::class.java.getMethod(
                "setActualDefaultRingtoneUri", android.content.Context::class.java,
                Int::class.javaPrimitiveType, Uri::class.java
            )
            m.invoke(null, ctx, type, item.uri)
            val label = when (type) {
                android.media.RingtoneManager.TYPE_RINGTONE -> getString(R.string.files_ringtone_set)
                android.media.RingtoneManager.TYPE_NOTIFICATION -> getString(R.string.files_notification_set)
                else -> getString(R.string.files_alarm_set)
            }
            requireContext().toast(label)
        } catch (e: Exception) { requireContext().toast(getString(R.string.files_setting_failed, e.message ?: "")) }
    }

    private fun shareItem(item: MediaItem) {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", item.uri)
            }, getString(R.string.files_share_with)))
        } catch (e: Exception) { requireContext().toast(getString(R.string.files_cant_share, e.message ?: "")) }
    }

    private fun sendSelected() {
        val items = selectedItems(); if (items.isEmpty()) return
        PendingSendHolder.set(items.map { it.uri })
        requireContext().toast(getString(R.string.files_send_ready))
        exitSelection()
        findNavController().navigate(R.id.homeFragment)
    }

    private fun deleteSelected() {
        val items = selectedItems(); if (items.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_delete_title)
            .setMessage(getString(R.string.files_delete_confirm_multi, items.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    var ok = 0; var fail = 0
                    for (it in items) if (deleteMedia(it)) ok++ else fail++
                    requireContext().toast(if (fail == 0) getString(R.string.files_deleted_multi, ok)
                        else getString(R.string.files_delete_partial, ok, fail))
                    exitSelection(); loadData()
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showOverflowMenu(anchor: View) {
        if (selection.size == 0) return
        val popup = android.widget.PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 1, 1, "إرسال بواسطة WifiShare")
        popup.menu.add(0, 2, 2, "مشاركة")
        val moveSub = popup.menu.addSubMenu(0, 3, 3, "نقل إلى")
        moveSub.add(0, 31, 1, "التخزين الداخلي")
        moveSub.add(0, 32, 2, "التخزين الخارجي (SD)")
        popup.menu.add(0, 4, 4, "حذف")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> sendSelected()
                2 -> {
                    val items = selectedItems()
                    val uris = items.map { it.uri }
                    try {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "*/*"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newRawUri("", uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                        }, getString(R.string.files_share_with)))
                    } catch (e: Exception) { requireContext().toast(getString(R.string.files_cant_share, e.message ?: "")) }
                }
                31 -> moveSelected(true)
                32 -> moveSelected(false)
                4 -> deleteSelected()
            }
            true
        }
        popup.show()
    }

    private fun moveSelected(toInternal: Boolean) {
        val items = selectedItems()
        if (items.isEmpty()) return
        val ctx = requireContext()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var success = 0
            var failed = 0

            val destDir = if (toInternal) {
                Environment.getExternalStorageDirectory()
            } else {
                val dirs = ctx.getExternalFilesDirs(null)
                if (dirs.size > 1 && dirs[1] != null) File(dirs[1]!!.absolutePath.substringBefore("/Android"))
                else Environment.getExternalStorageDirectory()
            }

            destDir.mkdirs()

            for (item in items) {
                val sourcePath = item.filePath
                if (sourcePath == null) {
                    failed++
                    continue
                }
                val sourceFile = File(sourcePath)
                if (!sourceFile.exists()) {
                    failed++
                    continue
                }

                try {
                    var destFile = File(destDir, sourceFile.name)
                    if (destFile.exists()) {
                        val base = sourceFile.name.substringBeforeLast('.', sourceFile.name)
                        val ext = if (sourceFile.name.contains('.')) "." + sourceFile.name.substringAfterLast('.') else ""
                        var i = 1
                        while (destFile.exists()) {
                            destFile = File(destDir, "$base ($i)$ext")
                            i++
                        }
                    }
                    sourceFile.copyTo(destFile, overwrite = true)
                    if (sourceFile.delete()) success++ else failed++
                } catch (e: Exception) {
                    failed++
                }
            }

            withContext(Dispatchers.Main) {
                if (isAdded) {
                    requireContext().toast("تم النقل: $success، فشل: $failed")
                    exitSelection()
                    loadData()
                }
            }
        }
    }

    private suspend fun deleteMedia(item: MediaItem): Boolean = try {
        if ((item.source == Source.RECEIVED || item.source == Source.FOLDERS) && item.filePath != null)
            File(item.filePath).delete()
        else requireContext().contentResolver.delete(item.uri, null, null) > 0
    } catch (_: Exception) { false }

    private fun showMoreMenu(item: MediaItem, anchor: View) {
        val actions = arrayOf(
            getString(R.string.files_action_open), getString(R.string.files_action_share),
            getString(R.string.files_action_send), getString(R.string.files_action_rename),
            getString(R.string.files_action_info), getString(R.string.files_action_delete)
        )
        android.widget.PopupMenu(requireContext(), anchor).apply {
            actions.forEachIndexed { i, l -> menu.add(0, i, i, l) }
            setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    0 -> openItem(item); 1 -> shareItem(item)
                    2 -> { PendingSendHolder.set(listOf(item.uri)); requireContext().toast(getString(R.string.files_send_ready)); findNavController().navigate(R.id.homeFragment) }
                    3 -> promptRename(item); 4 -> showInfo(item); 5 -> {
                        AlertDialog.Builder(requireContext()).setTitle(R.string.files_delete_title)
                            .setMessage(getString(R.string.files_delete_confirm, item.name))
                            .setPositiveButton(android.R.string.ok) { _, _ ->
                                viewLifecycleOwner.lifecycleScope.launch {
                                    val ok = deleteMedia(item)
                                    requireContext().toast(getString(if (ok) R.string.files_deleted else R.string.files_delete_failed))
                                    if (ok) loadData()
                                }
                            }.setNegativeButton(android.R.string.cancel, null).show()
                    }
                }; true
            }; show()
        }
    }

    private fun showInfo(item: MediaItem) {
        AlertDialog.Builder(requireContext()).setTitle(R.string.files_action_info)
            .setMessage(getString(R.string.files_info, item.name, humanSize(item.sizeBytes),
                item.mimeType, item.filePath ?: item.uri.toString()))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun promptRename(item: MediaItem) {
        val edit = EditText(requireContext()).apply { setText(item.name); setSelection(item.name.length); setPadding(48,32,48,32) }
        val d = AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_rename_title).setView(edit)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null).create()
        d.setOnShowListener {
            val pos = d.getButton(AlertDialog.BUTTON_POSITIVE)
            edit.doAfterTextChanged { pos.isEnabled = !it.isNullOrBlank() }
            pos.setOnClickListener {
                val n = edit.text.toString().trim()
                if (n.isNotEmpty() && n != item.name) { d.dismiss(); performRename(item, n) }
            }
        }
        d.show()
    }

    private fun performRename(item: MediaItem, newName: String) {
        if ((item.source == Source.RECEIVED || item.source == Source.FOLDERS) && item.filePath != null) {
            val oldF = File(item.filePath); val tgt = File(oldF.parentFile, newName)
            if (tgt.exists()) { requireContext().toast(getString(R.string.files_rename_exists)); return }
            if (oldF.renameTo(tgt)) { requireContext().toast(getString(R.string.files_renamed)); loadData() }
            else requireContext().toast(getString(R.string.files_rename_failed))
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                pendingRename = item to newName
                writeRequestLauncher.launch(IntentSenderRequest.Builder(
                    MediaStore.createWriteRequest(requireContext().contentResolver, listOf(item.uri)).intentSender
                ).build())
            } catch (e: Exception) { requireContext().toast(getString(R.string.files_rename_failed)) }
        } else directRename(item, newName)
    }

    private fun directRename(item: MediaItem, newName: String) {
        try {
            val v = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
            if (requireContext().contentResolver.update(item.uri, v, null, null) > 0) {
                requireContext().toast(getString(R.string.files_renamed)); loadData()
            } else requireContext().toast(getString(R.string.files_rename_failed))
        } catch (e: Exception) { requireContext().toast(getString(R.string.files_rename_failed)) }
    }

    private fun humanSize(b: Long) = when {
        b < 1024 -> "$b B"
        b < 1024*1024 -> "%.1f KB".format(b/1024.0)
        b < 1024L*1024*1024 -> "%.1f MB".format(b/1024.0/1024.0)
        else -> "%.2f GB".format(b/1024.0/1024.0/1024.0)
    }

    companion object { const val ARG_SOURCE = "source" }
}
