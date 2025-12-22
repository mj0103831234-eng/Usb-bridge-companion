package com.usbbridge.companion.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.*
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.usbbridge.companion.R
import com.usbbridge.companion.api.*
import com.usbbridge.companion.ui.MainActivity
import kotlinx.coroutines.*
import java.nio.ByteBuffer

class UsbBridgeService : Service() {
    
    companion object {
        private const val TAG = "UsbBridgeService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "usb_bridge_service"
        private const val ACTION_USB_PERMISSION = "com.usbbridge.companion.USB_PERMISSION"
        private const val HEARTBEAT_INTERVAL = 30000L
        private const val COMMAND_POLL_INTERVAL = 1000L
    }
    
    private val binder = LocalBinder()
    private lateinit var usbManager: UsbManager
    private var apiClient: BridgeApiClient? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeatJob: Job? = null
    private var commandPollingJob: Job? = null
    
    private val connectedDevices = mutableMapOf<String, UsbDevice>()
    private val deviceConnections = mutableMapOf<String, UsbDeviceConnection>()
    private val registeredDeviceIds = mutableMapOf<String, String>()
    
    var onDevicesChanged: ((List<UsbDevice>) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let { handleDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    device?.let { handleDeviceDetached(it) }
                }
                ACTION_USB_PERMISSION -> {
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        openDevice(device)
                    }
                }
            }
        }
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): UsbBridgeService = this@UsbBridgeService
    }
    
    override fun onCreate() {
        super.onCreate()
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        
        createNotificationChannel()
        
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onDestroy() {
        super.onDestroy()
        disconnect()
        unregisterReceiver(usbReceiver)
        serviceScope.cancel()
    }
    
    fun connect(serverUrl: String) {
        serviceScope.launch {
            try {
                apiClient = BridgeApiClient(serverUrl)
                
                val deviceInfo = mapOf(
                    "model" to Build.MODEL,
                    "manufacturer" to Build.MANUFACTURER,
                    "android_version" to Build.VERSION.RELEASE,
                    "sdk_version" to Build.VERSION.SDK_INT.toString()
                )
                
                val result = apiClient!!.registerBridge("Android Bridge - ${Build.MODEL}", deviceInfo)
                
                result.fold(
                    onSuccess = { bridge ->
                        Log.i(TAG, "Registered as bridge: ${bridge.id}")
                        withContext(Dispatchers.Main) {
                            onConnectionStateChanged?.invoke(true)
                        }
                        startHeartbeat()
                        scanExistingDevices()
                        startCommandPolling()
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Registration failed", error)
                        withContext(Dispatchers.Main) {
                            onError?.invoke("Connection failed: ${error.message}")
                            onConnectionStateChanged?.invoke(false)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Connection error", e)
                withContext(Dispatchers.Main) {
                    onError?.invoke("Connection error: ${e.message}")
                    onConnectionStateChanged?.invoke(false)
                }
            }
        }
    }
    
    fun disconnect() {
        heartbeatJob?.cancel()
        commandPollingJob?.cancel()
        
        deviceConnections.values.forEach { it.close() }
        deviceConnections.clear()
        connectedDevices.clear()
        registeredDeviceIds.clear()
        
        apiClient = null
        onConnectionStateChanged?.invoke(false)
    }
    
    fun getConnectedDevices(): List<UsbDevice> = connectedDevices.values.toList()
    
    private fun startHeartbeat() {
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                apiClient?.sendHeartbeat()
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }
    
    private fun startCommandPolling() {
        commandPollingJob = serviceScope.launch {
            while (isActive) {
                registeredDeviceIds.forEach { (usbDeviceKey, apiDeviceId) ->
                    pollAndExecuteCommands(usbDeviceKey, apiDeviceId)
                }
                delay(COMMAND_POLL_INTERVAL)
            }
        }
    }
    
    private suspend fun pollAndExecuteCommands(usbDeviceKey: String, apiDeviceId: String) {
        val client = apiClient ?: return
        val device = connectedDevices[usbDeviceKey] ?: return
        val connection = deviceConnections[usbDeviceKey] ?: return
        
        client.getPendingCommands(apiDeviceId).fold(
            onSuccess = { commands ->
                commands.forEach { command ->
                    executeCommand(command, device, connection, client)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to get commands for $apiDeviceId", error)
            }
        )
    }
    
    private suspend fun executeCommand(
        command: PendingCommand,
        device: UsbDevice,
        connection: UsbDeviceConnection,
        client: BridgeApiClient
    ) {
        val response = when (command.transferType) {
            "control" -> executeControlTransfer(command, connection)
            "bulk" -> executeBulkTransfer(command, device, connection)
            else -> CommandResponse(command.id, false, error = "Unknown transfer type")
        }
        
        client.submitCommandResponse(command.id, response)
    }
    
    private fun executeControlTransfer(
        command: PendingCommand,
        connection: UsbDeviceConnection
    ): CommandResponse {
        return try {
            val requestType = command.requestType ?: 0
            val request = command.request ?: 0
            val value = command.value ?: 0
            val index = command.index ?: 0
            val length = command.length ?: 0
            
            val buffer = if (command.data != null) {
                Base64.decode(command.data, Base64.DEFAULT)
            } else {
                ByteArray(length)
            }
            
            val result = connection.controlTransfer(
                requestType,
                request,
                value,
                index,
                buffer,
                buffer.size,
                5000
            )
            
            if (result >= 0) {
                val responseData = if (result > 0) {
                    Base64.encodeToString(buffer.copyOf(result), Base64.NO_WRAP)
                } else null
                
                CommandResponse(command.id, true, responseData, result)
            } else {
                CommandResponse(command.id, false, error = "Control transfer failed: $result")
            }
        } catch (e: Exception) {
            CommandResponse(command.id, false, error = e.message)
        }
    }
    
    private fun executeBulkTransfer(
        command: PendingCommand,
        device: UsbDevice,
        connection: UsbDeviceConnection
    ): CommandResponse {
        return try {
            val endpointAddress = command.endpoint ?: return CommandResponse(
                command.id, false, error = "No endpoint specified"
            )
            
            var targetEndpoint: UsbEndpoint? = null
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                for (j in 0 until intf.endpointCount) {
                    val ep = intf.getEndpoint(j)
                    if (ep.address == endpointAddress) {
                        targetEndpoint = ep
                        connection.claimInterface(intf, true)
                        break
                    }
                }
                if (targetEndpoint != null) break
            }
            
            if (targetEndpoint == null) {
                return CommandResponse(command.id, false, error = "Endpoint not found")
            }
            
            val isOut = command.direction == "out"
            val buffer = if (isOut && command.data != null) {
                Base64.decode(command.data, Base64.DEFAULT)
            } else {
                ByteArray(command.length ?: 64)
            }
            
            val result = connection.bulkTransfer(targetEndpoint, buffer, buffer.size, 5000)
            
            if (result >= 0) {
                val responseData = if (!isOut && result > 0) {
                    Base64.encodeToString(buffer.copyOf(result), Base64.NO_WRAP)
                } else null
                
                CommandResponse(command.id, true, responseData, result)
            } else {
                CommandResponse(command.id, false, error = "Bulk transfer failed: $result")
            }
        } catch (e: Exception) {
            CommandResponse(command.id, false, error = e.message)
        }
    }
    
    private fun scanExistingDevices() {
        usbManager.deviceList.values.forEach { device ->
            handleDeviceAttached(device)
        }
    }
    
    private fun handleDeviceAttached(device: UsbDevice) {
        val deviceKey = "${device.vendorId}:${device.productId}:${device.deviceName}"
        
        if (!usbManager.hasPermission(device)) {
            val permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
        } else {
            openDevice(device)
        }
    }
    
    private fun openDevice(device: UsbDevice) {
        val deviceKey = "${device.vendorId}:${device.productId}:${device.deviceName}"
        
        if (connectedDevices.containsKey(deviceKey)) return
        
        val connection = usbManager.openDevice(device)
        if (connection != null) {
            connectedDevices[deviceKey] = device
            deviceConnections[deviceKey] = connection
            
            registerDeviceWithServer(device, deviceKey)
            
            Log.i(TAG, "Device connected: ${device.deviceName}")
            onDevicesChanged?.invoke(connectedDevices.values.toList())
        } else {
            Log.e(TAG, "Failed to open device: ${device.deviceName}")
        }
    }
    
    private fun registerDeviceWithServer(device: UsbDevice, deviceKey: String) {
        val client = apiClient ?: return
        
        serviceScope.launch {
            val registration = DeviceRegistration(
                vendorId = device.vendorId,
                productId = device.productId,
                manufacturer = device.manufacturerName,
                productName = device.productName,
                serialNumber = device.serialNumber,
                deviceClass = device.deviceClass,
                deviceSubclass = device.deviceSubclass,
                deviceProtocol = device.deviceProtocol,
                interfaceCount = device.interfaceCount
            )
            
            client.registerDevice(registration).fold(
                onSuccess = { registered ->
                    registeredDeviceIds[deviceKey] = registered.id
                    Log.i(TAG, "Device registered with server: ${registered.id}")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to register device", error)
                }
            )
        }
    }
    
    private fun handleDeviceDetached(device: UsbDevice) {
        val deviceKey = "${device.vendorId}:${device.productId}:${device.deviceName}"
        
        deviceConnections[deviceKey]?.close()
        deviceConnections.remove(deviceKey)
        connectedDevices.remove(deviceKey)
        
        val apiDeviceId = registeredDeviceIds.remove(deviceKey)
        if (apiDeviceId != null) {
            serviceScope.launch {
                apiClient?.unregisterDevice(apiDeviceId)
            }
        }
        
        Log.i(TAG, "Device disconnected: ${device.deviceName}")
        onDevicesChanged?.invoke(connectedDevices.values.toList())
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("USB Bridge Active")
            .setContentText("Bridging USB devices to server")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}