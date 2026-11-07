package com.downloadchecker.app.data

import com.downloadchecker.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient {
    private val json = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // tgcloud Serverless.call style via HTTPS endpoint path if exposed;
    // For Telegram Mini App serverless, Android should hit your deployed HTTPS gateway.
    // Default: POST {API_BASE}/api/{name} with JSON body.
    private fun url(name: String) = "${BuildConfig.API_BASE.trimEnd('/')}/api/$name"

    private fun post(name: String, body: JSONObject): JSONObject {
        val req = Request.Builder()
            .url(url(name))
            .post(body.toString().toRequestBody(json))
            .header("Content-Type", "application/json")
            .build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val err = runCatching { JSONObject(text) }.getOrNull()
                val msg = err?.optString("message")
                    ?: err?.optString("error")
                    ?: "HTTP ${res.code}"
                throw IllegalStateException(msg)
            }
            return JSONObject(text.ifBlank { "{}" })
        }
    }

    fun requestCode(usernameOrId: String): JSONObject =
        post("androidRequestCode", JSONObject().put("usernameOrId", usernameOrId))

    fun verifyCode(usernameOrId: String, code: String, deviceId: String): JSONObject =
        post(
            "androidVerifyCode",
            JSONObject()
                .put("usernameOrId", usernameOrId)
                .put("code", code)
                .put("deviceId", deviceId)
        )

    fun claimTask(
        token: String,
        taskType: Int,
        packages: List<String>,
        deviceId: String,
        idempotencyKey: String
    ): JSONObject {
        val installs = JSONArray()
        packages.forEach { p ->
            installs.put(JSONObject().put("packageName", p).put("installedAt", System.currentTimeMillis() / 1000))
        }
        return post(
            "androidClaimTask",
            JSONObject()
                .put("sessionToken", token)
                .put("taskType", taskType)
                .put("installs", installs)
                .put("deviceId", deviceId)
                .put("idempotencyKey", idempotencyKey)
        )
    }

    fun status(token: String): JSONObject =
        post("androidStatus", JSONObject().put("sessionToken", token))
}
