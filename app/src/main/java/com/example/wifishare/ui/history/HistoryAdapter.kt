package com.example.wifishare.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wifishare.R
import com.example.wifishare.data.history.TransferRecord
import com.example.wifishare.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (TransferRecord) -> Unit
) : ListAdapter<TransferRecord, HistoryAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    inner class VH(private val b: ItemHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: TransferRecord) {
            val ctx = b.root.context
            b.ivIcon.setImageResource(
                if (r.direction == "SEND") R.drawable.ic_send else R.drawable.ic_receive
            )
            b.tvName.text = r.fileName
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(r.timestampMs))
            val size = humanSize(r.sizeBytes)
            val dir = if (r.direction == "SEND") "↑" else "↓"
            b.tvMeta.text = "$dir  $size  ·  $date  ·  ${r.peerName}"
            b.root.setOnClickListener { onClick(r) }
        }
    }

    private fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TransferRecord>() {
            override fun areItemsTheSame(a: TransferRecord, b: TransferRecord) = a.id == b.id
            override fun areContentsTheSame(a: TransferRecord, b: TransferRecord) = a == b
        }
    }
}
