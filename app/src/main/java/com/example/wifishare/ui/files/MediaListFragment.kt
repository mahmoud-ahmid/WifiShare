package com.example.wifishare.ui.files

import android.app.Activity
import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import com.example.wifishare.core.SafeName
import com.example.wifishare.core.toast
import com.example.wifishare.ui.viewer.PdfViewerActivity
import com.example.wifishare.ui.viewer.ImageViewerActivity
import android.provider.Settings
import android.media.RingtoneManager
import com.example.wifishare.data.files.MediaItem
import com.example.wifishare.data.files.PendingSendHolder
import com.example.wifishare.data.files.Source
import com.example.wifishare.databinding.FragmentMediaListBinding
import com.example.wifishare.ui.common.BaseFragment
import kotlinx.coroutines.launch
import java.io.File

class MediaListFragment : BaseFragment<FragmentMediaListBinding>(
    { i, p, a -> FragmentMediaListBinding.inflate(i, p, a) }
) {
    private val container by lazy { (requireActivity().application as WifiShareApp).container }
    private val selection = ItemSelection()
    private var source: Source = Source.RECEIVED
    private var allItems: List<MediaItem> = emptyList()
    private var pendingRename: Pair<MediaItem, String>? = null

    private lateinit var adapter: MediaAdapter

    private val writeRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val p = pendingRename
        pendingRename = null
        if (result.resultCode == Activity.RESULT_OK && p != null) {
            directRename(p.first, p.second)
        } else if (p != null) {
            requireContext().toast(getString(R.string.files_rename_failed))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        source = Source.valueOf(requireArguments().getString(ARG_SOURCE, Source.RECEIVED.name))

        adapter = MediaAdapter(
            onClick = { item ->
                if (selection.isEmpty) openItem(item) else toggleSelection(item)
            },
            onLongClick = { item -> toggleSelection(item) },
            onMoreClick = { item, v -> showMoreMenu(item, v) },
            isSelected = { selection.isSelected(it.uri) },
            selectionMode = { !selection.isEmpty }
        )

        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = adapter

        binding.btnSelClose.setOnClickListener { exitSelection() }
        binding.btnSelSend.setOnClickListener { sendSelected() }
        binding.btnSelShare.setOnClickListener { shareSelected() }
        binding.btnSelDelete.setOnClickListener { deleteSelected() }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        if (source == Source.RECEIVED) loadData()
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
                            uri = uri,
                            name = rf.name,
                            sizeBytes = rf.sizeBytes,
                            mimeType = requireContext().contentResolver.getType(uri) ?: "*/*",
                            modifiedMs = rf.modifiedMs,
                            source = Source.RECEIVED,
                            filePath = rf.file.absolutePath
                        )
                    }
                }
                Source.IMAGES -> container.mediaStoreRepository.images()
                Source.VIDEOS -> container.mediaStoreRepository.videos()
                Source.AUDIO -> container.mediaStoreRepository.audio()
                Source.DOCUMENTS -> container.mediaStoreRepository.documents()
            }
            adapter.submitList(allItems)
            binding.emptyState.isVisible = allItems.isEmpty()
            binding.rvFiles.isVisible = allItems.isNotEmpty()
        }
    }

    private fun toggleSelection(item: MediaItem) {
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
    }

    private fun selectedItems(): List<MediaItem> =
        allItems.filter { selection.isSelected(it.uri) }

    private fun openItem(item: MediaItem) {
        val ctx = requireContext()
        when {
            item.mimeType.startsWith("image/") ->
                startActivity(ImageViewerActivity.newIntent(ctx, item.uri, item.name))
            item.mimeType == "application/pdf" ->
                startActivity(PdfViewerActivity.newIntent(ctx, item.uri, item.name))
            item.mimeType.startsWith("video/") ->
                requireContext().toast(getString(R.string.media_video_disabled))
            item.mimeType.startsWith("audio/") ->
                showAudioActions(item)
            else -> openExternally(item)
        }
    }

    private fun showAudioActions(item: MediaItem) {
        val options = arrayOf(
            getString(R.string.files_action_ringtone),
            getString(R.string.files_action_notification),
            getString(R.string.files_action_alarm),
            getString(R.string.files_action_share),
            getString(R.string.files_action_info)
        )
        AlertDialog.Builder(requireContext())
            .setTitle(item.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> setAsRingtone(item, RingtoneManager.TYPE_RINGTONE)
                    1 -> setAsRingtone(item, RingtoneManager.TYPE_NOTIFICATION)
                    2 -> setAsRingtone(item, RingtoneManager.TYPE_ALARM)
                    3 -> shareItem(item)
                    4 -> showInfo(item)
                }
            }
            .show()
    }

    private fun setAsRingtone(item: MediaItem, type: Int) {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(ctx)) {
            AlertDialog.Builder(ctx)
                .setTitle(R.string.files_grant_write_title)
                .setMessage(R.string.files_grant_write_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        try {
            val rm = RingtoneManager(ctx)
            rm.setActualDefaultRingtoneUri(ctx, type, item.uri)
            val label = when (type) {
                RingtoneManager.TYPE_RINGTONE -> getString(R.string.files_ringtone_set)
                RingtoneManager.TYPE_NOTIFICATION -> getString(R.string.files_notification_set)
                else -> getString(R.string.files_alarm_set)
            }
            requireContext().toast(label)
        } catch (e: Exception) {
            requireContext().toast(getString(R.string.files_setting_failed, e.message ?: ""))
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
        } catch (e: Exception) {
            requireContext().toast(getString(R.string.files_cant_open, e.message ?: "no app"))
        }
    }

    private fun shareItem(item: MediaItem) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = item.mimeType
                putExtra(Intent.EXTRA_STREAM, item.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", item.uri)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.files_share_with)))
        } catch (e: Exception) {
            requireContext().toast(getString(R.string.files_cant_share, e.message ?: ""))
        }
    }

    private fun shareSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        try {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("", uris.first()).apply {
                    uris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
            }
            startActivity(Intent.createChooser(intent, getString(R.string.files_share_with)))
        } catch (e: Exception) {
            requireContext().toast(getString(R.string.files_cant_share, e.message ?: ""))
        }
    }

    private fun sendSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        PendingSendHolder.set(uris)
        requireContext().toast(getString(R.string.files_send_ready))
        exitSelection()
        findNavController().navigate(R.id.homeFragment)
    }

    private fun deleteSelected() {
        val items = selectedItems()
        if (items.isEmpty()) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_delete_title)
            .setMessage(getString(R.string.files_delete_confirm_multi, items.size))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    var ok = 0
                    var fail = 0
                    for (it in items) {
                        if (deleteMedia(it)) ok++ else fail++
                    }
                    requireContext().toast(
                        if (fail == 0) getString(R.string.files_deleted_multi, ok)
                        else getString(R.string.files_delete_partial, ok, fail)
                    )
                    exitSelection()
                    loadData()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private suspend fun deleteMedia(item: MediaItem): Boolean = try {
        if (item.source == Source.RECEIVED && item.filePath != null) {
            File(item.filePath).delete()
        } else {
            requireContext().contentResolver.delete(item.uri, null, null) > 0
        }
    } catch (_: Exception) { false }

    private fun showMoreMenu(item: MediaItem, anchor: View) {
        val actions = arrayOf(
            getString(R.string.files_action_open),
            getString(R.string.files_action_share),
            getString(R.string.files_action_send),
            getString(R.string.files_action_rename),
            getString(R.string.files_action_info),
            getString(R.string.files_action_delete)
        )
        android.widget.PopupMenu(requireContext(), anchor).apply {
            actions.forEachIndexed { i, label -> menu.add(0, i, i, label) }
            setOnMenuItemClickListener { mi ->
                when (mi.itemId) {
                    0 -> openItem(item)
                    1 -> shareItem(item)
                    2 -> { PendingSendHolder.set(listOf(item.uri)); requireContext().toast(getString(R.string.files_send_ready)); findNavController().navigate(R.id.homeFragment) }
                    3 -> promptRename(item)
                    4 -> showInfo(item)
                    5 -> confirmDeleteSingle(item)
                }
                true
            }
            show()
        }
    }

    private fun showInfo(item: MediaItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_action_info)
            .setMessage(getString(
                R.string.files_info,
                item.name,
                humanSize(item.sizeBytes),
                item.mimeType,
                item.filePath ?: item.uri.toString()
            ))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmDeleteSingle(item: MediaItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_delete_title)
            .setMessage(getString(R.string.files_delete_confirm, item.name))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = deleteMedia(item)
                    requireContext().toast(getString(if (ok) R.string.files_deleted else R.string.files_delete_failed))
                    if (ok) loadData()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptRename(item: MediaItem) {
        val edit = EditText(requireContext()).apply {
            setText(item.name)
            setSelection(item.name.length)
            setPadding(48, 32, 48, 32)
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.files_rename_title)
            .setView(edit)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            edit.doAfterTextChanged { positive.isEnabled = !it.isNullOrBlank() }
            positive.setOnClickListener {
                val newName = edit.text.toString().trim()
                if (newName.isNotEmpty() && newName != item.name) {
                    dialog.dismiss()
                    performRename(item, newName)
                }
            }
        }
        dialog.show()
    }

    private fun performRename(item: MediaItem, rawName: String) {
        val newName = SafeName.sanitize(rawName)
        if (item.source == Source.RECEIVED && item.filePath != null) {
            val oldFile = File(item.filePath)
            val target = File(oldFile.parentFile, newName)
            if (!SafeName.isInside(oldFile.parentFile!!, target)) {
                requireContext().toast(getString(R.string.files_rename_failed))
                return
            }
            if (target.exists()) {
                requireContext().toast(getString(R.string.files_rename_exists))
                return
            }
            val ok = oldFile.renameTo(target)
            if (ok) {
                requireContext().toast(getString(R.string.files_renamed))
                loadData()
            } else {
                requireContext().toast(getString(R.string.files_rename_failed))
            }
            return
        }
        // MediaStore
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                pendingRename = item to newName
                val pi = MediaStore.createWriteRequest(
                    requireContext().contentResolver, listOf(item.uri)
                )
                writeRequestLauncher.launch(
                    IntentSenderRequest.Builder(pi.intentSender).build()
                )
            } catch (e: Exception) {
                requireContext().toast(getString(R.string.files_rename_failed))
            }
        } else {
            directRename(item, newName)
        }
    }

    private fun directRename(item: MediaItem, newName: String) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            }
            val count = requireContext().contentResolver.update(item.uri, values, null, null)
            if (count > 0) {
                requireContext().toast(getString(R.string.files_renamed))
                loadData()
            } else {
                requireContext().toast(getString(R.string.files_rename_failed))
            }
        } catch (e: Exception) {
            requireContext().toast(getString(R.string.files_rename_failed))
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }

    companion object {
        const val ARG_SOURCE = "source"
    }
}
