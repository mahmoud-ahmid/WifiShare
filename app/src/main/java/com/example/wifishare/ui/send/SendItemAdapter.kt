package com.example.wifishare.ui.send

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.example.wifishare.R
import com.example.wifishare.data.send.SendItem
import com.example.wifishare.data.send.ThumbnailLoader
import com.example.wifishare.databinding.ItemSectionHeaderBinding
import com.example.wifishare.databinding.ItemSendBinding

class SendItemAdapter(
    private val selection: SelectionState,
    private val onClick: (SendItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Row {
        data class Header(val title: String, val count: Int) : Row()
        data class Item(val item: SendItem) : Row()
    }

    private var rows: List<Row> = emptyList()

    fun submit(items: List<SendItem>, currentCategory: SendItem.Category) {
        val newRows = mutableListOf<Row>()
        if (currentCategory == SendItem.Category.APPLICATIONS) {
            val installed = items.filter { it.isInstalledApp }
            val notInstalled = items.filterNot { it.isInstalledApp }
            if (installed.isNotEmpty()) {
                newRows.add(Row.Header("التطبيقات المثبتة", installed.size))
                installed.forEach { newRows.add(Row.Item(it)) }
            }
            if (notInstalled.isNotEmpty()) {
                newRows.add(Row.Header("ملفات APK غير المثبتة", notInstalled.size))
                notInstalled.forEach { newRows.add(Row.Item(it)) }
            }
        } else {
            items.forEach { newRows.add(Row.Item(it)) }
        }
        rows = newRows
        notifyDataSetChanged()
    }

    fun refreshSelection() = notifyDataSetChanged()

    override fun getItemCount(): Int = rows.size
    override fun getItemViewType(position: Int): Int =
        when (rows[position]) { is Row.Header -> TYPE_HEADER; is Row.Item -> TYPE_ITEM }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderVH(ItemSectionHeaderBinding.inflate(inf, parent, false))
        } else {
            ItemVH(ItemSendBinding.inflate(inf, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val r = rows[position]) {
            is Row.Header -> (holder as HeaderVH).bind(r.title, r.count)
            is Row.Item -> (holder as ItemVH).bind(r.item)
        }
    }

    inner class HeaderVH(private val b: ItemSectionHeaderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(title: String, count: Int) {
            b.tvHeader.text = title
            b.tvCount.text = "($count)"
        }
    }

    inner class ItemVH(private val b: ItemSendBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: SendItem) {
            val ctx = b.root.context
            b.tvName.text = item.name
            b.tvMeta.text = if (item.mimeType == "inode/directory")
                ctx.getString(R.string.send_folder)
            else humanSize(item.sizeBytes)

            // إعادة تعيين للـ recycling
            b.ivIcon.tag = null
            b.ivIcon.scaleType = ImageView.ScaleType.CENTER_INSIDE
            b.ivIcon.setPadding(dp(12), dp(12), dp(12), dp(12))
            b.ivIcon.setImageDrawable(null)

            val fallback = iconFor(item)
            b.ivIcon.clearColorFilter()

            when {
                item.appIcon != null -> {
                    b.ivIcon.setImageDrawable(item.appIcon)
                    b.ivIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                    b.ivIcon.setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                item.mimeType.startsWith("image/") ||
                item.mimeType.startsWith("video/") ||
                item.mimeType.startsWith("audio/") -> {
                    b.ivIcon.setPadding(0, 0, 0, 0)
                    ThumbnailLoader.load(ctx, item, b.ivIcon, fallback)
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

            b.cbSelect.visibility = View.VISIBLE
            b.cbSelect.isChecked = selection.isSelected(item)
            b.root.setOnClickListener { onClick(item) }
        }

        private fun dp(v: Int): Int =
            (v * b.root.resources.displayMetrics.density).toInt()
    }

    private fun iconFor(item: SendItem): Int = when {
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
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }
}
