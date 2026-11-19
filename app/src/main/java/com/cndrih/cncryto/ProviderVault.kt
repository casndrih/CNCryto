package com.cndrih.cncryto
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.cndrih.cncryto.ai.ProviderConfig
import org.json.JSONArray
import org.json.JSONObject

class ProviderVault(context:Context){
 private val prefs=EncryptedSharedPreferences.create(context,"cncryto_vault",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 fun save(profiles:List<ProviderConfig>){
  val array=JSONArray()
  profiles.forEach{p->array.put(JSONObject().put("id",p.id).put("url",p.baseUrl).put("model",p.model).put("key",p.apiKey).put("enabled",p.enabled))}
  prefs.edit().putString("profiles",array.toString()).apply()
 }
}
