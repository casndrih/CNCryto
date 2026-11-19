package com.cndrih.cncryto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cndrih.cncryto.ai.*
import kotlinx.coroutines.launch

private val Lime = Color(0xFFA4E804)
private val Dark = darkColorScheme(
    primary = Lime, onPrimary = Color.Black, secondary = Color(0xFF00BFA6),
    background = Color.Black, surface = Color(0xFF111111), surfaceVariant = Color(0xFF202020),
    onBackground = Color(0xFFF5F5F5), onSurface = Color(0xFFF5F5F5)
)
private val Light = lightColorScheme(
    primary = Color(0xFF5B8500), onPrimary = Color.White, secondary = Color(0xFF00796B),
    background = Color(0xFFF7F8F4), surface = Color.White, surfaceVariant = Color(0xFFECEFE7),
    onBackground = Color(0xFF171A14), onSurface = Color(0xFF171A14)
)
private data class ProviderDraft(val id: Int, val name: String, val endpoint: String, val model: String, val key: String, val enabled: Boolean = true)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vault = ProviderVault(this)
        val preferences = getSharedPreferences("cncryto_ui", MODE_PRIVATE)
        setContent {
            var themeChoice by remember { mutableStateOf(preferences.getString("theme", "system") ?: "system") }
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkMode = when (themeChoice) { "dark" -> true; "light" -> false; else -> systemDark }
            val shapes = androidx.compose.material3.Shapes(
                extraSmall = RoundedCornerShape(18.dp), small = RoundedCornerShape(22.dp),
                medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp),
                extraLarge = RoundedCornerShape(32.dp)
            )
            MaterialTheme(colorScheme = if (darkMode) Dark else Light, shapes = shapes) {
                var tab by remember { mutableIntStateOf(0) }
                var symbol by remember { mutableStateOf("BTCUSDT") }
                val initialProfiles = remember {
                    vault.load().mapIndexed { index, profile ->
                        ProviderDraft(index + 1, profile.id, profile.baseUrl, profile.model, profile.apiKey, profile.enabled)
                    }.ifEmpty { listOf(ProviderDraft(1, "OpenAI", "https://api.openai.com/v1", "", "")) }
                }
                var profiles by remember { mutableStateOf(initialProfiles) }
                var nextId by remember { mutableIntStateOf((initialProfiles.maxOfOrNull { it.id } ?: 0) + 1) }
                val modelOptions = remember { mutableStateMapOf<Int, List<String>>() }
                val loadingModels = remember { mutableStateMapOf<Int, Boolean>() }
                val modelErrors = remember { mutableStateMapOf<Int, String>() }
                val expandedModels = remember { mutableStateMapOf<Int, Boolean>() }
                var output by remember { mutableStateOf<EnsembleResult?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                fun update(id: Int, change: (ProviderDraft) -> ProviderDraft) {
                    profiles = profiles.map { if (it.id == id) change(it) else it }
                }
                fun saveProfiles() = vault.save(profiles.map {
                    ProviderConfig(it.name, it.endpoint, it.model, it.key, it.enabled)
                })
                fun loadModels(profile: ProviderDraft) {
                    if (profile.endpoint.isBlank() || profile.key.isBlank()) {
                        modelErrors[profile.id] = "Enter the provider URL and API key first."
                        return
                    }
                    loadingModels[profile.id] = true
                    modelErrors.remove(profile.id)
                    scope.launch {
                        try {
                            val models = ProviderModelCatalog().fetch(profile.endpoint, profile.key)
                            modelOptions[profile.id] = models
                            if (profile.model.isBlank() && models.isNotEmpty()) {
                                update(profile.id) { it.copy(model = models.first()) }
                            }
                            if (models.isEmpty()) modelErrors[profile.id] = "The provider returned no models."
                        } catch (t: Throwable) {
                            modelErrors[profile.id] = t.message ?: "Unable to load models."
                        } finally {
                            loadingModels[profile.id] = false
                        }
                    }
                }

                LaunchedEffect(tab) {
                    if (tab == 4) profiles.filter { it.key.isNotBlank() && modelOptions[it.id] == null }.forEach { loadModels(it) }
                }

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(
                            containerColor = if (darkMode) Color(0xFF101010) else Color(0xFFF0F1EC),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            val navColors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Lime,
                            selectedTextColor = if (darkMode) Lime else Dark.primary,
                            indicatorColor = if (darkMode) Color(0xFF26300F) else Color(0xFFE5F2C9)
                        )
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 },
                            icon = { Icon(Icons.Default.ShowChart, null) }, label = { Text("Markets") }, colors = navColors)
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 },
                            icon = { Icon(Icons.Default.Wallet, null) }, label = { Text("Trades") }, colors = navColors)
                        NavigationBarItem(selected = tab == 2, onClick = { tab = 2 },
                            icon = { Icon(Icons.Default.AutoGraph, null) }, label = { Text("Analysis") }, colors = navColors)
                        NavigationBarItem(selected = tab == 3, onClick = { tab = 3 },
                            icon = { Icon(Icons.Default.ListAlt, null) }, label = { Text("Engine") }, colors = navColors)
                        NavigationBarItem(selected = tab == 4, onClick = { tab = 4 },
                            icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") }, colors = navColors)
                        }
                    }
                ) { pad ->
                    if (tab == 0) {
                        MarketsScreen(onOpenTrades = { tab = 1 }, modifier = Modifier.fillMaxSize().padding(pad))
                    } else if (tab == 1) {
                        TradesScreen(Modifier.fillMaxSize().padding(pad))
                    } else if (tab == 2) {
                        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            item { Text("CNCryto", style = MaterialTheme.typography.headlineLarge); Text("Multi-model market intelligence", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item {
                                OutlinedTextField(
                                    value = symbol, onValueChange = { symbol = it.uppercase() },
                                    label = { Text("Market symbol") }, modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(28.dp), singleLine = true
                                )
                            }
                            item {
                                Button(enabled = !busy && symbol.isNotBlank(), onClick = {
                                    val configs = profiles.filter { it.enabled && it.key.isNotBlank() && it.model.isNotBlank() }
                                        .map { ProviderConfig(it.name, it.endpoint, it.model, it.key) }
                                    if (configs.isEmpty()) { error = "Load and select a model for at least one enabled provider."; return@Button }
                                    busy = true; error = null; output = null
                                    scope.launch {
                                        try {
                                            val marketCategory = when {
                                                symbol.contains("/") -> "Forex"
                                                symbol.uppercase().contains("BTC") || symbol.uppercase().contains("ETH") ||
                                                    symbol.uppercase().contains("SOL") || symbol.uppercase().contains("XRP") ||
                                                    symbol.uppercase().endsWith("USDT") -> "Crypto"
                                                symbol.uppercase().contains("XAU") || symbol.uppercase().contains("XAG") ||
                                                    symbol.uppercase().contains("WTI") || symbol.uppercase().contains("BRENT") -> "Metals"
                                                else -> "Equities"
                                            }
                                            val bars = runCatching { MarketDataRepository.candles(symbol, marketCategory) }.getOrDefault(emptyList())
                                            val features = bars.takeLast(30).flatMap { listOf(it.open, it.high, it.low, it.close) }
                                            val dataNote = if (bars.isNotEmpty()) {
                                                "Public market data source: " + when (marketCategory) {
                                                    "Crypto" -> "Binance hourly OHLCV (public API)"
                                                    "Forex" -> "ECB daily reference rates via Frankfurter; these are not intraday quotes"
                                                    "Metals" -> "Stooq daily market data; availability varies"
                                                    else -> "Stooq daily OHLC; source availability and delay vary"
                                                } + ". Supplied ${bars.size} bars; latest close ${bars.last().close}. Treat as indicative, not broker execution data."
                                            } else {
                                                "Public market data could not be retrieved for $symbol. No OHLCV features supplied; do not invent market facts or prices."
                                            }
                                            val req = PredictionRequest(symbol, "1h", "4h", configs,
                                                MarketContext(dataNote, features))
                                            output = EnsembleEngine(listOf(OpenAiCompatibleProvider()), NeutralAccuracyLedger()).predict(req)
                                        } catch (t: Throwable) { error = t.message ?: "Prediction failed" }
                                        finally { busy = false }
                                    }
                                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Analyse with all enabled models")
                                }
                            }
                            if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                            output?.let { v -> item {
                                ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(v.direction.uppercase(), style = MaterialTheme.typography.headlineMedium, color = Lime)
                                    Text("Consensus score: %.1f / 100".format(v.score))
                                    Text("Weighted agreement: %.0f%%".format(v.agreement * 100))
                                    Text("Successful models: ${v.contributors}")
                                    Text(v.summary)
                                } }
                            } }
                            item { Text("Watchlist", style = MaterialTheme.typography.titleLarge) }
                            item { Text("BTCUSDT   •   ETHUSDT   •   EURUSD   •   XAUUSD", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { Text("No live prices or verified trade signals are shown until a market-data source is connected.", style = MaterialTheme.typography.bodySmall) }
                            item { Text("Trading involves substantial risk. AI output is not financial advice and no orders are executed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    } else if (tab == 4) {
                        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            item {
                                Text("AI providers", style = MaterialTheme.typography.headlineLarge)
                                Text("Load available models directly from each provider, then select which models join the ensemble.")
                            }
                            item {
                                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEachIndexed { index, (value, label) ->
                                        SegmentedButton(
                                            selected = themeChoice == value,
                                            onClick = { themeChoice = value; preferences.edit().putString("theme", value).apply() },
                                            shape = SegmentedButtonDefaults.itemShape(index, 3)
                                        ) { Text(label) }
                                    }
                                }
                            }
                            itemsIndexed(profiles, key = { _, p -> p.id }) { _, p ->
                                ElevatedCard(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(p.name.ifBlank { "Provider ${p.id}" }, style = MaterialTheme.typography.titleMedium)
                                            Row {
                                                Switch(checked = p.enabled, onCheckedChange = { v -> update(p.id) { it.copy(enabled = v) } })
                                                if (profiles.size > 1) TextButton(onClick = { profiles = profiles.filterNot { it.id == p.id }; modelOptions.remove(p.id) }) { Text("Remove") }
                                            }
                                        }
                                        OutlinedTextField(p.name, { v -> update(p.id) { it.copy(name = v) } },
                                            label = { Text("Provider label") }, modifier = Modifier.fillMaxWidth(),
                                            singleLine = true, shape = RoundedCornerShape(28.dp))
                                        OutlinedTextField(p.endpoint, { v -> update(p.id) { it.copy(endpoint = v) }; modelOptions.remove(p.id); modelErrors.remove(p.id) },
                                            label = { Text("OpenAI-compatible base URL") }, modifier = Modifier.fillMaxWidth(),
                                            singleLine = true, shape = RoundedCornerShape(28.dp))
                                        OutlinedTextField(p.key, { v -> update(p.id) { it.copy(key = v) }; modelOptions.remove(p.id); modelErrors.remove(p.id) },
                                            label = { Text("API key") }, modifier = Modifier.fillMaxWidth(),
                                            singleLine = true, visualTransformation = PasswordVisualTransformation(),
                                            shape = RoundedCornerShape(28.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = { loadModels(p) }, enabled = loadingModels[p.id] != true,
                                                modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp)) {
                                                if (loadingModels[p.id] == true) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                                else Text("Load models")
                                            }
                                            Box(Modifier.weight(1f)) {
                                                OutlinedButton(
                                                    onClick = { expandedModels[p.id] = true },
                                                    enabled = !modelOptions[p.id].isNullOrEmpty(),
                                                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)
                                                ) { Text(p.model.ifBlank { "Select model" }, maxLines = 1) }
                                                DropdownMenu(expanded = expandedModels[p.id] == true, onDismissRequest = { expandedModels[p.id] = false }) {
                                                    modelOptions[p.id].orEmpty().forEach { model ->
                                                        DropdownMenuItem(text = { Text(model) }, onClick = {
                                                            update(p.id) { it.copy(model = model) }
                                                            expandedModels[p.id] = false
                                                        })
                                                    }
                                                }
                                            }
                                        }
                                        modelErrors[p.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                                        Text("Models are retrieved from the provider's /models endpoint. The endpoint must expose the OpenAI-compatible API.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            item {
                                OutlinedButton(onClick = {
                                    profiles = profiles + ProviderDraft(nextId, "Provider $nextId", "https://api.openai.com/v1", "", "")
                                    nextId++
                                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) { Text("Add provider") }
                            }
                            item {
                                Button(onClick = { saveProfiles() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
                                    Text("Save encrypted provider profiles")
                                }
                            }
                            item {
                                Text("Provider API keys remain encrypted on this device. Native vendor-specific APIs that do not implement the OpenAI-compatible /models and /chat/completions routes need dedicated adapters.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        ForexEngineScreen(Modifier.fillMaxSize().padding(pad))
                    }
                }
            }
        }
    }
}
