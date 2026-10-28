package com.example.wifishare.ui.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wifishare.R
import com.example.wifishare.data.files.MediaItem
import com.example.wifishare.data.files.Source
import com.example.wifishare.databinding.ItemFileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaAdapter(
    private val onClick: (MediaItem) -> Unit,
    private val onLongClick: (MediaItem) -> Unit,
    private val onMoreClick: (MediaItem, View) -> Unit,
    private val isSelected: (MediaItem) -> Boolean,
    private val selectionMode: () -> Boolean
) : ListAdapter<MediaItem, MediaAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: MediaItem) {
            b.ivIcon.setImageResource(iconFor(item))
            b.tvName.text = item.name
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(item.modifiedMs))
            b.tvMeta.text = "${humanSize(item.sizeBytes)}  ·  $date"

            val selectionModeNow = selectionMode()
            val selected = isSelected(item)
            b.cbSelect.visibility = if (selectionModeNow) View.VISIBLE else View.GONE
            b.cbSelect.isChecked = selected
            b.btnMore.visibility = if (selectionModeNow) View.GONE else View.VISIBLE

            b.root.setOnClickListener {
                if (selectionModeNow) onLongClick(item)  // in selection mode, tap = toggle
                else onClick(item)
            }
            b.root.setOnLongClickListener {
                if (!selectionModeNow) onLongClick(item)
                true
            }
            b.btnMore.setOnClickListener { v -> onMoreClick(item, v) }
            b.cbSelect.setOnClickListener { onLongClick(item) }
        }
    }

    private fun iconFor(item: MediaItem): Int = when (item.source) {
        Source.IMAGES -> R.drawable.ic_file
        Source.VIDEOS -> R.drawable.ic_file
        Source.AUDIO -> R.drawable.ic_file
        Source.RECEIVED -> R.drawable.ic_receive
        Source.DOCUMENTS -> R.drawable.ic_file
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(a: MediaItem, b: MediaItem) = a.uri == b.uri
            override fun areContentsTheSame(a: MediaItem, b: MediaItem) = a == b
        }
    }
}
