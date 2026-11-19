package com.cndrih.cncryto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cndrih.cncryto.ai.ProviderConfig
import com.cndrih.cncryto.ai.ProviderProtocol
import org.json.JSONArray
import org.json.JSONObject

class ProviderVault(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "cncryto_vault",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(profiles: List<ProviderConfig>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(JSONObject()
                .put("id", profile.id)
                .put("url", profile.baseUrl)
                .put("model", profile.model)
                .put("key", profile.apiKey)
                .put("enabled", profile.enabled)
                .put("protocol", profile.protocol.name)
                .put("timeoutMillis", profile.timeoutMillis))
        }
        prefs.edit().putString("profiles", array.toString()).apply()
    }

    fun load(): List<ProviderConfig> {
        val raw = prefs.getString("profiles", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val protocol = runCatching {
                        ProviderProtocol.valueOf(item.optString("protocol", ProviderProtocol.OPENAI_COMPATIBLE.name))
                    }.getOrDefault(ProviderProtocol.OPENAI_COMPATIBLE)
                    add(ProviderConfig(
                        id = item.getString("id"),
                        baseUrl = item.getString("url"),
                        model = item.getString("model"),
                        apiKey = item.optString("key", ""),
                        enabled = item.optBoolean("enabled", true),
                        protocol = protocol,
                        timeoutMillis = item.optLong("timeoutMillis", 12_000L)
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }
}
