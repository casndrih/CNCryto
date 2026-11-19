package com.cndrih.cncryto.ai
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.time.Instant

data class ProviderConfig(val id:String,val baseUrl:String,val model:String,val apiKey:String,val enabled:Boolean=true)
data class MarketContext(val qualityNote:String,val features:List<Double>)
data class PredictionRequest(val symbol:String,val timeframe:String,val horizon:String,val providers:List<ProviderConfig>,val market:MarketContext)
data class ProviderForecast(val providerId:String,val model:String,val direction:String,val score:Double,val confidence:Double,val rationale:String)
data class EnsembleResult(val direction:String,val score:Double,val agreement:Double,val contributors:Int,val summary:String,val generatedAt:String)
interface AIProvider { suspend fun predict(config:ProviderConfig,request:PredictionRequest):ProviderForecast }
class OpenAiCompatibleProvider:AIProvider{
 private val http=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(12,TimeUnit.SECONDS).callTimeout(15,TimeUnit.SECONDS).build()
 override suspend fun predict(config:ProviderConfig,request:PredictionRequest)=withContext(Dispatchers.IO){
  require(config.baseUrl.startsWith("https://")||config.baseUrl.startsWith("http://10.")||config.baseUrl.startsWith("http://192.168.")||config.baseUrl.startsWith("http://127.0.0.1")){"Use HTTPS or a private/local endpoint"}
  val prompt="""Analyse ${request.symbol}, timeframe ${request.timeframe}, horizon ${request.horizon}. Data note: ${request.market.qualityNote}. No candles are attached; do not invent prices or probabilities. Return JSON only with direction (bullish/bearish/neutral), score 0-100, confidence 0-1 and rationale. Be neutral if evidence is insufficient."""
  val body=JSONObject().put("model",config.model).put("temperature",0).put("max_tokens",220).put("messages",org.json.JSONArray()
   .put(JSONObject().put("role","system").put("content","You are cautious. Never fabricate live market data or claim certainty."))
   .put(JSONObject().put("role","user").put("content",prompt))).toString().toRequestBody("application/json".toMediaType())
  val req=Request.Builder().url(config.baseUrl.trimEnd('/')+"/chat/completions").header("Authorization","Bearer ${config.apiKey}").post(body).build()
  http.newCall(req).execute().use{res->
   val raw=res.body?.string().orEmpty();check(res.isSuccessful){"${config.id}: HTTP ${res.code}: ${raw.take(160)}"}
   val content=JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
   val obj=JSONObject("{"+content.substringAfter('{').substringBeforeLast('}')+"}")
   val dir=obj.optString("direction","neutral").lowercase().let{if(it in listOf("bullish","bearish","neutral"))it else "neutral"}
   ProviderForecast(config.id,config.model,dir,obj.optDouble("score",50.0).coerceIn(0.0,100.0),obj.optDouble("confidence",0.0).coerceIn(0.0,1.0),obj.optString("rationale","No rationale"))
  }
 }
}
interface AccuracyLedger { fun reliability(providerId:String):Double }
class NeutralAccuracyLedger:AccuracyLedger{override fun reliability(providerId:String)=0.5}
class EnsembleEngine(private val providers:List<AIProvider>,private val ledger:AccuracyLedger){
 suspend fun predict(request:PredictionRequest):EnsembleResult=coroutineScope{
  val configs=request.providers.filter{it.enabled&&it.apiKey.isNotBlank()};require(configs.isNotEmpty()){"Enable at least one provider"}
  val forecasts=configs.map{cfg->async{runCatching{(providers.firstOrNull()?:error("Provider adapter missing")).predict(cfg,request)}.getOrNull()}}.awaitAll().filterNotNull()
  require(forecasts.isNotEmpty()){"Every provider failed or timed out"}
  val rows=forecasts.map{f->val w=ledger.reliability(f.providerId).coerceIn(0.05,1.0)*f.confidence.coerceIn(0.05,1.0);Triple(f,w,when(f.direction){"bullish"->1.0;"bearish"-> -1.0;else->0.0})}
  val total=rows.sumOf{it.second}.coerceAtLeast(1e-9);val vote=rows.sumOf{it.second*it.third}/total
  val dir=when{vote > 0.15->"bullish";vote < -0.15->"bearish";else->"neutral"}
  val agree=rows.filter{if(dir=="bullish")it.third>0 else if(dir=="bearish")it.third<0 else it.third==0.0}.sumOf{it.second}/total
  val score=rows.sumOf{it.second*it.first.score}/total
  EnsembleResult(dir,score,agree,forecasts.size,forecasts.joinToString(" | "){it.providerId+": "+it.rationale}.take(800),Instant.now().toString())
 }
}
