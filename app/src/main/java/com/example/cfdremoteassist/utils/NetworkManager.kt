package com.example.cfdremoteassist.utils

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetworkManager(private val context: Context, private val configManager: ManagedConfigManager) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun register(deviceInfo: Map<String, String>, callback: (Boolean, String?) -> Unit) {
        val baseUrl = configManager.getTrackingServerUrl()
        if (baseUrl.isEmpty()) {
            callback(false, "Server URL not configured")
            return
        }

        val url = "$baseUrl/api/v1/register"
        val json = gson.toJson(deviceInfo)
        val body = json.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NetworkManager", "Registration failed: ${e.message}")
                callback(false, "Connection error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if ((response.code == 200 || response.code == 201) && responseBody != null) {
                    try {
                        val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                        val secret = jsonObject.get("connection_secret")?.asString
                        if (!secret.isNullOrEmpty()) {
                            configManager.setConnectionSecret(secret)
                            configManager.setRegistered(true)
                            callback(true, null)
                        } else {
                            callback(false, "Server response missing connection secret")
                        }
                    } catch (e: Exception) {
                        callback(false, "Failed to parse server response")
                    }
                } else {
                    callback(false, "Server error: ${response.code}")
                }
            }
        })
    }

    fun ping(uid: String, callback: (Boolean, String?) -> Unit) {
        val baseUrl = configManager.getTrackingServerUrl()
        if (baseUrl.isEmpty()) {
            callback(false, "Server URL not configured")
            return
        }

        val url = "$baseUrl/api/v1/ping?uid=$uid"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Connection error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.code == 200 && responseBody != null) {
                    val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
                    val ok = jsonObject.get("ok")?.asBoolean == true
                    if (ok) {
                        callback(true, null)
                    } else {
                        callback(false, "Device recognized as false by server")
                    }
                } else if (response.code == 404) {
                    configManager.clearConnectionSecret()
                    callback(false, "Device not recognized. Re-registration required.")
                } else {
                    callback(false, "Server error: ${response.code}")
                }
            }
        })
    }

    fun sendTelemetry(payload: Map<String, Any>) {
        val baseUrl = configManager.getTrackingServerUrl()
        val secret = configManager.getConnectionSecret()
        if (baseUrl.isEmpty() || secret.isEmpty()) return

        val url = "$baseUrl/api/v1/telemetry"
        val json = gson.toJson(payload)
        val body = json.toRequestBody(jsonMediaType)

        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("X-Connection-Secret", secret)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("NetworkManager", "Telemetry failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 401) {
                    Log.w("NetworkManager", "Telemetry auth failed. Clearing secret.")
                    configManager.clearConnectionSecret()
                }
                response.close()
            }
        })
    }
}