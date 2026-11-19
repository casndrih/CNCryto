package com.cndrih.cncryto
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
class ProviderVault(context:Context){
 private val prefs=EncryptedSharedPreferences.create(context,"cncryto_vault",MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 fun save(url:String,model:String,key:String){prefs.edit().putString("url",url).putString("model",model).putString("key",key).apply()}
}
