package com.example.wifishare.ui.files

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.wifishare.R
import com.example.wifishare.data.files.MediaItem
import com.example.wifishare.data.send.ThumbnailLoader
import com.example.wifishare.databinding.ItemFileBinding

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
            val ctx = b.root.context
            b.tvName.text = item.name
            b.tvMeta.text = if (item.mimeType == "inode/directory")
                ctx.getString(R.string.send_folder)
            else humanSize(item.sizeBytes)

            val fallback = iconFor(item)
            b.ivIcon.setPadding(0, 0, 0, 0)
            b.ivIcon.tag = null
            b.ivIcon.clearColorFilter()
            when {
                item.appIcon != null -> {
                    b.ivIcon.setImageDrawable(item.appIcon)
                    b.ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                    b.ivIcon.setPadding(dp(6), dp(6), dp(6), dp(6))
                }
                item.mimeType.startsWith("image/") ||
                item.mimeType.startsWith("video/") ||
                item.mimeType.startsWith("audio/") -> {
                    b.ivIcon.scaleType = ImageView.ScaleType.CENTER_CROP
                    ThumbnailLoader.loadRaw(ctx, item.uri, item.mimeType, null, b.ivIcon, fallback)
                }
                item.mimeType == "inode/directory" -> {
                    b.ivIcon.setImageResource(R.drawable.ic_folder)
                    b.ivIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.icon_folder))
                    b.ivIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    b.ivIcon.setPadding(dp(10), dp(10), dp(10), dp(10))
                }
                item.mimeType == "application/pdf" -> {
                    b.ivIcon.setImageResource(R.drawable.ic_doc)
                    b.ivIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.icon_pdf))
                    b.ivIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    b.ivIcon.setPadding(dp(10), dp(10), dp(10), dp(10))
                }
                else -> {
                    b.ivIcon.setImageResource(fallback)
                    b.ivIcon.setColorFilter(ContextCompat.getColor(ctx, R.color.icon_file))
                    b.ivIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    b.ivIcon.setPadding(dp(10), dp(10), dp(10), dp(10))
                }
            }

            val sm = selectionMode()
            b.cbSelect.visibility = if (sm) View.VISIBLE else View.GONE
            b.cbSelect.isChecked = isSelected(item)
            b.btnMore.visibility = if (sm) View.GONE else View.VISIBLE
            b.root.setOnClickListener {
                if (sm) onLongClick(item) else onClick(item)
            }
            b.root.setOnLongClickListener {
                if (!sm) onLongClick(item); true
            }
            b.btnMore.setOnClickListener { v -> onMoreClick(item, v) }
            b.cbSelect.setOnClickListener { onLongClick(item) }
        }

        private fun dp(v: Int): Int =
            (v * b.root.resources.displayMetrics.density).toInt()
    }

    private fun iconFor(item: MediaItem): Int = when {
        item.mimeType == "inode/directory" -> R.drawable.ic_folder
        item.mimeType == "application/vnd.android.package-archive" -> R.drawable.ic_app
        item.mimeType.startsWith("image/") -> R.drawable.ic_image
        item.mimeType.startsWith("video/") -> R.drawable.ic_video
        item.mimeType.startsWith("audio/") -> R.drawable.ic_audio
        item.mimeType == "application/pdf" -> R.drawable.ic_doc
        else -> R.drawable.ic_file
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
            override fun areContentsTheSame(a: MediaItem, b: MediaItem): Boolean =
                a.copy(appIcon = null) == b.copy(appIcon = null)
        }
    }
}
