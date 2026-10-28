package com.example.wifishare.ui.files

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wifishare.R
import com.example.wifishare.data.files.ReceivedFile
import com.example.wifishare.databinding.ItemFileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileListAdapter(
    private val onClick: (ReceivedFile) -> Unit,
    private val onLongClick: (ReceivedFile) -> Unit
) : ListAdapter<ReceivedFile, FileListAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ReceivedFile) {
            b.ivIcon.setImageResource(R.drawable.ic_file)
            b.tvName.text = item.name
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(item.modifiedMs))
            b.tvMeta.text = "${humanSize(item.sizeBytes)}  ·  $date"
            b.root.setOnClickListener { onClick(item) }
            b.root.setOnLongClickListener { onLongClick(item); true }
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReceivedFile>() {
            override fun areItemsTheSame(a: ReceivedFile, b: ReceivedFile) = a.file.absolutePath == b.file.absolutePath
            override fun areContentsTheSame(a: ReceivedFile, b: ReceivedFile) = a == b
        }
    }
}
