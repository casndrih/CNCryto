package com.cndrih.cncryto
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.cndrih.cncryto.ai.*
import kotlinx.coroutines.launch

private val Lime=Color(0xFFA4E804)
private val Dark=darkColorScheme(primary=Lime,onPrimary=Color.Black,secondary=Color(0xFF00BFA6),background=Color(0xFF10130D),surface=Color(0xFF1A2015))
private data class ProviderDraft(val id:Int,val name:String,val endpoint:String,val model:String,val key:String,val enabled:Boolean=true)
class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  val vault=ProviderVault(this)
  setContent { MaterialTheme(colorScheme=Dark) {
   var tab by remember { mutableIntStateOf(0) }
   var symbol by remember { mutableStateOf("BTCUSDT") }
   val initialProfiles = remember {
    vault.load().mapIndexed { index, profile ->
     ProviderDraft(index + 1, profile.id, profile.baseUrl, profile.model, profile.apiKey, profile.enabled)
    }.ifEmpty { listOf(ProviderDraft(1,"OpenAI","https://api.openai.com/v1","gpt-4o-mini","")) }
   }
   var profiles by remember { mutableStateOf(initialProfiles) }
   var nextId by remember { mutableIntStateOf((initialProfiles.maxOfOrNull { it.id } ?: 0) + 1) }
   var output by remember { mutableStateOf<EnsembleResult?>(null) }
   var error by remember { mutableStateOf<String?>(null) }
   var busy by remember { mutableStateOf(false) }
   val scope= rememberCoroutineScope()
   fun update(id:Int,change:(ProviderDraft)->ProviderDraft){profiles=profiles.map{if(it.id==id)change(it) else it}}
   Scaffold(bottomBar={NavigationBar(containerColor=Dark.surface){
    NavigationBarItem(selected=tab==0,onClick={tab=0},icon={Icon(Icons.Default.AutoGraph,null)},label={Text("Analysis")})
    NavigationBarItem(selected=tab==1,onClick={tab=1},icon={Icon(Icons.Default.Settings,null)},label={Text("Providers")})
   }}){ pad ->
    if(tab==0) LazyColumn(Modifier.fillMaxSize().padding(pad).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
     item{Text("CNCryto",style=MaterialTheme.typography.headlineLarge);Text("Multi-model market intelligence",color=Color.LightGray)}
     item{OutlinedTextField(symbol,{symbol=it.uppercase()},label={Text("Market symbol")},modifier=Modifier.fillMaxWidth())}
     item{Button(enabled=!busy&&symbol.isNotBlank(),onClick={
      val configs=profiles.filter{it.enabled&&it.key.isNotBlank()}.map{ProviderConfig(it.name,it.endpoint,it.model,it.key)}
      if(configs.isEmpty()){error="Enable and configure at least one provider";return@Button}
      busy=true;error=null;output=null
      scope.launch{try{
       val req=PredictionRequest(symbol,"1h","4h",configs,MarketContext("No live OHLCV feed is connected; models must not invent market facts.",emptyList()))
       output=EnsembleEngine(listOf(OpenAiCompatibleProvider()),NeutralAccuracyLedger()).predict(req)
      }catch(t:Throwable){error=t.message?: "Prediction failed"}finally{busy=false}}
     },modifier=Modifier.fillMaxWidth()){if(busy)CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp) else Text("Analyse with all enabled models")}}
     if(error!=null)item{Text(error!!,color=MaterialTheme.colorScheme.error)}
     output?.let{v->item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
      Text(v.direction.uppercase(),style=MaterialTheme.typography.headlineMedium,color=Lime)
      Text("Consensus score: %.1f / 100".format(v.score))
      Text("Weighted agreement: %.0f%%".format(v.agreement*100))
      Text("Successful models: ${v.contributors}")
      Text(v.summary)
     }}}}
     item{Text("Watchlist",style=MaterialTheme.typography.titleLarge)}
     item{Text("BTCUSDT   •   ETHUSDT   •   EURUSD   •   XAUUSD",color=Color.LightGray)}
     item{Text("No live prices or verified trade signals are shown until a market-data source is connected.",style=MaterialTheme.typography.bodySmall)}
     item{Text("Trading involves substantial risk. AI output is not financial advice and no orders are executed.",style=MaterialTheme.typography.bodySmall,color=Color.LightGray)}
    }else LazyColumn(Modifier.fillMaxSize().padding(pad).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
     item{Text("AI providers",style=MaterialTheme.typography.headlineLarge);Text("Each enabled model is queried concurrently. Add multiple compatible endpoints/models to form an ensemble.")}
     itemsIndexed(profiles,key={_,p->p.id}){_,p->
      ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
       Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()){
        Text(p.name,style=MaterialTheme.typography.titleMedium)
        Row { Switch(checked=p.enabled,onCheckedChange={v->update(p.id){it.copy(enabled=v)}}); if(profiles.size>1)TextButton(onClick={profiles=profiles.filterNot{it.id==p.id}}){Text("Remove")} }
       }
       OutlinedTextField(p.name,{v->update(p.id){it.copy(name=v)}},label={Text("Provider label")},modifier=Modifier.fillMaxWidth(),singleLine=true)
       OutlinedTextField(p.endpoint,{v->update(p.id){it.copy(endpoint=v)}},label={Text("OpenAI-compatible base URL")},modifier=Modifier.fillMaxWidth(),singleLine=true)
       OutlinedTextField(p.model,{v->update(p.id){it.copy(model=v)}},label={Text("Model ID")},modifier=Modifier.fillMaxWidth(),singleLine=true)
       OutlinedTextField(p.key,{v->update(p.id){it.copy(key=v)}},label={Text("API key")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
      }}
     }
     item{OutlinedButton(onClick={profiles=profiles+ProviderDraft(nextId,"Provider $nextId","https://api.openai.com/v1","model-id","");nextId++},modifier=Modifier.fillMaxWidth()){Text("Add another model/provider")}}
     item{Button(onClick={vault.save(profiles.map{ProviderConfig(it.name,it.endpoint,it.model,it.key,it.enabled)})},modifier=Modifier.fillMaxWidth()){Text("Save encrypted provider profiles")}}
     item{Text("All providers currently use the OpenAI-compatible chat-completions protocol. Vendor-specific adapters can be added for APIs with different request formats.",style=MaterialTheme.typography.bodySmall)}
    }
   }
  }}
 }
}
