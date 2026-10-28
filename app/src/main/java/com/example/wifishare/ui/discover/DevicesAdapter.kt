package com.example.wifishare.ui.discover

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wifishare.databinding.ItemDeviceBinding
import com.example.wifishare.domain.model.PeerDevice

class DevicesAdapter(
    private val onClick: (PeerDevice) -> Unit
) : ListAdapter<PeerDevice, DevicesAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val binding: ItemDeviceBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(device: PeerDevice) {
            binding.tvName.text = device.name
            binding.tvAddress.text = device.address
            binding.root.setOnClickListener { onClick(device) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PeerDevice>() {
            override fun areItemsTheSame(a: PeerDevice, b: PeerDevice) = a.address == b.address
            override fun areContentsTheSame(a: PeerDevice, b: PeerDevice) = a == b
        }
    }
}
