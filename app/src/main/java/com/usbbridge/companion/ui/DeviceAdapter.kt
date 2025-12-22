package com.usbbridge.companion.ui

import android.graphics.drawable.GradientDrawable
import android.hardware.usb.UsbDevice
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.usbbridge.companion.R
import com.usbbridge.companion.databinding.ItemDeviceBinding

class DeviceAdapter : ListAdapter<UsbDevice, DeviceAdapter.DeviceViewHolder>(DeviceDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val binding = ItemDeviceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeviceViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class DeviceViewHolder(
        private val binding: ItemDeviceBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(device: UsbDevice) {
            binding.deviceName.text = device.productName 
                ?: "USB Device"
            
            binding.deviceInfo.text = String.format(
                "VID: %04X  PID: %04X\n%s",
                device.vendorId,
                device.productId,
                device.manufacturerName ?: "Unknown manufacturer"
            )
            
            binding.deviceStatus.text = "Connected"
            
            val drawable = binding.deviceStatusIndicator.background as? GradientDrawable
            drawable?.setColor(ContextCompat.getColor(itemView.context, R.color.success))
        }
    }
    
    class DeviceDiffCallback : DiffUtil.ItemCallback<UsbDevice>() {
        override fun areItemsTheSame(oldItem: UsbDevice, newItem: UsbDevice): Boolean {
            return oldItem.deviceName == newItem.deviceName
        }
        
        override fun areContentsTheSame(oldItem: UsbDevice, newItem: UsbDevice): Boolean {
            return oldItem.deviceName == newItem.deviceName &&
                   oldItem.vendorId == newItem.vendorId &&
                   oldItem.productId == newItem.productId
        }
    }
}