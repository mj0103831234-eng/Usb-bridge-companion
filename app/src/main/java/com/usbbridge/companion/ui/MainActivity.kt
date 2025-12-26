package com.usbbridge.companion.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.usbbridge.companion.R
import com.usbbridge.companion.databinding.ActivityMainBinding
import com.usbbridge.companion.service.UsbBridgeService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bridgeService: UsbBridgeService? = null
    private var isBound = false
    private lateinit var deviceAdapter: DeviceAdapter

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as UsbBridgeService.LocalBinder
            bridgeService = binder.getService()
            isBound = true
            setupServiceCallbacks()
            updateDeviceList()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bridgeService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        requestNotificationPermission()
        startAndBindService()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun setupUI() {
        // Pre-fill your Replit API URL
        binding.serverUrlInput.setText("https://api-connects-5--mj0103831234.replit.app")
        
        binding.connectButton.setOnClickListener {
            val serverUrl = binding.serverUrlInput.text.toString().trim()
            if (serverUrl.isNotEmpty()) {
                bridgeService?.connect(serverUrl)
                updateConnectionState(true, "Connecting...")
            } else {
                Toast.makeText(this, "Please enter server URL", Toast.LENGTH_SHORT).show()
            }
        }

        binding.disconnectButton.setOnClickListener {
            bridgeService?.disconnect()
        }

        deviceAdapter = DeviceAdapter(emptyList())
        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = deviceAdapter
        }

        updateConnectionState(false, "Disconnected")
    }

    private fun setupServiceCallbacks() {
        bridgeService?.apply {
            onConnectionStateChanged = { connected ->
                runOnUiThread {
                    updateConnectionState(connected, if (connected) "Connected" else "Disconnected")
                }
            }

            onDevicesChanged = { devices ->
                runOnUiThread {
                    updateDeviceList(devices)
                }
            }

            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                    updateConnectionState(false, "Error: $error")
                }
            }
        }
    }

    private fun updateConnectionState(connected: Boolean, status: String) {
        binding.apply {
            statusText.text = status
            statusIndicator.setImageResource(
                if (connected) R.drawable.status_connected else R.drawable.status_disconnected
            )
            connectButton.isEnabled = !connected
            disconnectButton.isEnabled = connected
            serverUrlInput.isEnabled = !connected
        }
    }

    private fun updateDeviceList(devices: List<UsbDevice> = emptyList()) {
        val deviceList = if (isBound) {
            bridgeService?.getConnectedDevices() ?: devices
        } else devices

        deviceAdapter.updateDevices(deviceList)
        
        binding.noDevicesText.visibility = if (deviceList.isEmpty()) View.VISIBLE else View.GONE
        binding.devicesRecyclerView.visibility = if (deviceList.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun startAndBindService() {
        val intent = Intent(this, UsbBridgeService::class.java)
        startForegroundService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}
