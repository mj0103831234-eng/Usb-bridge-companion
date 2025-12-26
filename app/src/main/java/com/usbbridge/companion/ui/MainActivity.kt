package com.usbbridge.companion.ui

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.usbbridge.companion.R
import com.usbbridge.companion.service.USBBridgeService

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: USBManager
    private lateinit var deviceAdapter: DeviceAdapter
    private lateinit var statusIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupUSB()
        setupRecyclerView()
        updateUI()
    }

    private fun initializeViews() {
        statusIcon = findViewById(R.id.statusIcon)
        statusText = findViewById(R.id.statusText)
        recyclerView = findViewById(R.id.recyclerView)
    }

    private fun setupUSB() {
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(emptyList())
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter
        updateDeviceList()
    }

    private fun updateUI() {
        val isConnected = USBBridgeService.isRunning()
        
        if (isConnected) {
            statusIcon.setImageResource(R.drawable.status_connected)
            statusText.text = "Connected"
        } else {
            statusIcon.setImageResource(R.drawable.status_disconnected)
            statusText.text = "Disconnected"
        }
    }

    private fun updateDeviceList() {
        val devices = usbManager.deviceList.values.toList()
        deviceAdapter.updateDevices(devices)
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        updateDeviceList()
    }
}
