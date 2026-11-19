package com.cndrih.cncryto

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** JSON-only Kotlin boundary for the bundled Python analysis engine. */
object PythonForexEngine {
    private val startupLock = Any()

    suspend fun call(context: Context, request: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            synchronized(startupLock) {
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(context.applicationContext))
                }
            }
            val responseText = Python.getInstance()
                .getModule("cncryto_bridge")
                .callAttr("handle", request.toString())
                .toString()
            val response = JSONObject(responseText)
            if (!response.optBoolean("ok")) {
                throw IllegalStateException(response.optString("error", "Python engine request failed"))
            }
            response.optJSONObject("data") ?: JSONObject()
        }
}
