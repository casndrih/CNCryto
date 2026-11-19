package com.cndrih.cncryto
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cndrih.cncryto.ai.*
import kotlinx.coroutines.launch

private val Lime=Color(0xFFA4E804)
private val Dark=darkColorScheme(primary=Lime,onPrimary=Color.Black,secondary=Color(0xFF00BFA6),background=Color(0xFF10130D),surface=Color(0xFF1A2015))
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val vault=ProviderVault(this)
  setContent { MaterialTheme(colorScheme=Dark) {
   var tab by remember { mutableIntStateOf(0) }
   var symbol by remember { mutableStateOf("BTCUSDT") }
   var endpoint by remember { mutableStateOf("https://api.openai.com/v1") }
   var model by remember { mutableStateOf("gpt-4o-mini") }
   var key by remember { mutableStateOf("") }
   var output by remember { mutableStateOf<EnsembleResult?>(null) }
   var error by remember { mutableStateOf<String?>(null) }
   var busy by remember { mutableStateOf(false) }
   val scope= rememberCoroutineScope()
   Scaffold(bottomBar={NavigationBar(containerColor=Dark.surface){
    NavigationBarItem(selected=tab==0,onClick={tab=0},icon={Icon(Icons.Default.AutoGraph,null)},label={Text("Analysis")})
    NavigationBarItem(selected=tab==1,onClick={tab=1},icon={Icon(Icons.Default.Settings,null)},label={Text("Providers")})
   }}){ pad ->
    if(tab==0) LazyColumn(Modifier.fillMaxSize().padding(pad).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
     item{Text("CNCryto",style=MaterialTheme.typography.headlineLarge);Text("AI market intelligence",color=Color.LightGray)}
     item{OutlinedTextField(symbol,{symbol=it.uppercase()},label={Text("Symbol")},modifier=Modifier.fillMaxWidth())}
     item{Button(enabled=!busy&&symbol.isNotBlank(),onClick={
      if(key.isBlank()){error="Configure a provider API key first";return@Button}
      busy=true;error=null;output=null
      scope.launch{try{
       val cfg=ProviderConfig("primary",endpoint,model,key)
       val req=PredictionRequest(symbol,"1h","4h",listOf(cfg),MarketContext("No live OHLCV feed is connected; model must not invent market facts.",emptyList()))
       output=EnsembleEngine(listOf(OpenAiCompatibleProvider()),NeutralAccuracyLedger()).predict(req)
      }catch(t:Throwable){error=t.message?: "Prediction failed"}finally{busy=false}}
     },modifier=Modifier.fillMaxWidth()){if(busy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Text("Analyse market")}}
     if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
     output?.let{v->item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Text(v.direction.uppercase(),style=MaterialTheme.typography.headlineMedium,color=Lime)
      Text("Consensus score: %.1f / 100".format(v.score))
      Text("Model agreement: %.0f%%".format(v.agreement*100))
      Text("Contributors: ${v.contributors}")
      Text(v.summary)
     }}}}
     item{Text("Watchlist",style=MaterialTheme.typography.titleLarge)}
     item{Text("BTCUSDT   •   ETHUSDT   •   EURUSD   •   XAUUSD",color=Color.LightGray)}
     item{Text("No live prices or trading signals are shown until a verified market-data source is connected.",style=MaterialTheme.typography.bodySmall)}
     item{Text("Trading involves substantial risk. AI output is not financial advice and no orders are executed.",style=MaterialTheme.typography.bodySmall,color=Color.LightGray)}
    }else LazyColumn(Modifier.fillMaxSize().padding(pad).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item{Text("AI providers",style=MaterialTheme.typography.headlineLarge);Text("OpenAI-compatible API profile")}
     item{OutlinedTextField(endpoint,{endpoint=it},label={Text("Base URL")},modifier=Modifier.fillMaxWidth())}
     item{OutlinedTextField(model,{model=it},label={Text("Model ID")},modifier=Modifier.fillMaxWidth())}
     item{OutlinedTextField(key,{key=it},label={Text("API key")},modifier=Modifier.fillMaxWidth())}
     item{Button(onClick={vault.save(endpoint,model,key)},modifier=Modifier.fillMaxWidth()){Text("Save encrypted credentials")}}
     item{Text("The engine is structured for concurrent multi-model voting. This first UI iteration exposes one provider profile; provider management and persistent accuracy calibration are explicitly documented as remaining work.")}
    }
   }
  }}
 }
}
