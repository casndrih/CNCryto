package com.cndrih.cncryto.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Retrieves model identifiers from an OpenAI-compatible GET /models endpoint. */
class ProviderModelCatalog {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("https://") ||
            baseUrl.startsWith("http://10.") ||
            baseUrl.startsWith("http://192.168.") ||
            baseUrl.startsWith("http://127.0.0.1")) {
            "Use HTTPS or a private/local endpoint."
        }
        require(apiKey.isNotBlank()) { "API key is required to retrieve models." }
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/models")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                "Model discovery failed (HTTP ${response.code}): ${body.take(180)}"
            }
            val data = JSONObject(body).optJSONArray("data")
                ?: throw IllegalStateException("The provider response did not contain a models data array.")
            buildList {
                for (index in 0 until data.length()) {
                    val id = data.optJSONObject(index)?.optString("id").orEmpty().trim()
                    if (id.isNotBlank()) add(id)
                }
            }.distinct().sorted()
        }
    }
}
