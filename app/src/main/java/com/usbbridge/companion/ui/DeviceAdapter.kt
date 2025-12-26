package com.usbbridge.companion.ui

import android.hardware.usb.UsbDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.usbbridge.companion.R

class DeviceAdapter(private var devices: List<UsbDevice>) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceInfo: TextView = itemView.findViewById(R.id.deviceInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position](holder.deviceName.text) = device.deviceName ?: "Unknown Device"
        holder.deviceInfo.text = "ID: ${device.vendorId}:${device.productId}"
    }

    override fun getItemCount(): Int = devices.size

    fun updateDevices(newDevices: List<UsbDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
