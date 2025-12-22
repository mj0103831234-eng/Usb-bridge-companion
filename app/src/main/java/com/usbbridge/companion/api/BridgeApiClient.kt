package com.usbbridge.companion.api

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class BridgeRegistration(
    val name: String,
    @SerializedName("device_info") val deviceInfo: Map<String, String>
)

data class RegisteredBridge(
    val id: String,
    @SerializedName("api_key") val apiKey: String,
    val name: String,
    @SerializedName("device_info") val deviceInfo: Map<String, String>,
    @SerializedName("registered_at") val registeredAt: String,
    @SerializedName("last_seen") val lastSeen: String
)

data class DeviceRegistration(
    @SerializedName("vendor_id") val vendorId: Int,
    @SerializedName("product_id") val productId: Int,
    val manufacturer: String? = null,
    @SerializedName("product_name") val productName: String? = null,
    @SerializedName("serial_number") val serialNumber: String? = null,
    @SerializedName("device_class") val deviceClass: Int = 0,
    @SerializedName("device_subclass") val deviceSubclass: Int = 0,
    @SerializedName("device_protocol") val deviceProtocol: Int = 0,
    @SerializedName("interface_count") val interfaceCount: Int = 1
)

data class RegisteredDevice(
    val id: String,
    @SerializedName("bridge_id") val bridgeId: String,
    @SerializedName("vendor_id") val vendorId: Int,
    @SerializedName("product_id") val productId: Int,
    val manufacturer: String?,
    @SerializedName("product_name") val productName: String?,
    @SerializedName("serial_number") val serialNumber: String?,
    val status: String,
    @SerializedName("registered_at") val registeredAt: String
)

data class PendingCommand(
    val id: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("transfer_type") val transferType: String,
    @SerializedName("request_type") val requestType: Int?,
    val request: Int?,
    val value: Int?,
    val index: Int?,
    val length: Int?,
    val endpoint: Int?,
    val direction: String?,
    val data: String?,
    @SerializedName("created_at") val createdAt: String
)

data class CommandResponse(
    @SerializedName("command_id") val commandId: String,
    val success: Boolean,
    val data: String? = null,
    @SerializedName("bytes_transferred") val bytesTransferred: Int? = null,
    val error: String? = null
)

class BridgeApiClient(private var baseUrl: String) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    
    var apiKey: String? = null
    var bridgeId: String? = null
    
    fun updateBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }
    
    suspend fun registerBridge(name: String, deviceInfo: Map<String, String>): Result<RegisteredBridge> {
        return withContext(Dispatchers.IO) {
            try {
                val registration = BridgeRegistration(name, deviceInfo)
                val json = gson.toJson(registration)
                
                val request = Request.Builder()
                    .url("$baseUrl/bridges/register")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val bridge = gson.fromJson(body, RegisteredBridge::class.java)
                    apiKey = bridge.apiKey
                    bridgeId = bridge.id
                    Result.success(bridge)
                } else {
                    Result.failure(IOException("Registration failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun sendHeartbeat(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/bridges/$bridgeId/heartbeat?api_key=$apiKey")
                    .post("".toRequestBody(jsonMediaType))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Heartbeat failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun registerDevice(device: DeviceRegistration): Result<RegisteredDevice> {
        return withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(device)
                
                val request = Request.Builder()
                    .url("$baseUrl/devices/register?api_key=$apiKey")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    Result.success(gson.fromJson(body, RegisteredDevice::class.java))
                } else {
                    Result.failure(IOException("Device registration failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun unregisterDevice(deviceId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/devices/$deviceId?api_key=$apiKey")
                    .delete()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Device unregistration failed: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun getPendingCommands(deviceId: String): Result<List<PendingCommand>> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/commands/$deviceId/pending?api_key=$apiKey")
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val commands = gson.fromJson(body, Array<PendingCommand>::class.java)
                    Result.success(commands.toList())
                } else {
                    Result.failure(IOException("Failed to get commands: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    suspend fun submitCommandResponse(commandId: String, response: CommandResponse): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(response)
                
                val request = Request.Builder()
                    .url("$baseUrl/commands/$commandId/response?api_key=$apiKey")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()
                
                val httpResponse = client.newCall(request).execute()
                
                if (httpResponse.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Failed to submit response: ${httpResponse.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}