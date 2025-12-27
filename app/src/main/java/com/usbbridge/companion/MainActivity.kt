package com.usbbridge.companion

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recyclerView = findViewById(R.id.recyclerView)

        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        
        setupRecyclerView()
        updateStatus()
    }

    private fun setupRecyclerView() {
        val devices = usbManager.deviceList.values.toList()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = DeviceAdapter(devices)
    }

    private fun updateStatus() {
        val deviceCount = usbManager.deviceList.size
        statusText.text = "USB Bridge Ready - $deviceCount devices found"
    }
}
