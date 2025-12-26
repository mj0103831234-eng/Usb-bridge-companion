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

class MainActivity : AppCompatActivity() {

    private lateinit var usbManager: UsbManager
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
        val devices = usbManager.deviceList.values.toList()
        deviceAdapter = DeviceAdapter(devices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceAdapter
    }

    private fun updateUI() {
        statusIcon.setImageResource(R.drawable.status_connected)
        statusText.text = "USB Bridge Ready"
    }

    override fun onResume() {
        super.onResume()
        updateUI()
        setupRecyclerView()
    }
}
