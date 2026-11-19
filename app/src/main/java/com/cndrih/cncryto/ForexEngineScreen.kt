package com.cndrih.cncryto

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForexEngineScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var runtime by remember { mutableStateOf("Python engine not checked") }
    var pastedSignals by remember { mutableStateOf("") }
    var parsedSignals by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var journalMessage by remember { mutableStateOf<String?>(null) }
    var journal by remember { mutableStateOf<List<JSONObject>>(emptyList()) }

    var symbol by remember { mutableStateOf("EURUSD") }
    var direction by remember { mutableStateOf("Buy") }
    var entry by remember { mutableStateOf("1.08500") }
    var stop by remember { mutableStateOf("1.08000") }
    var target by remember { mutableStateOf("1.09500") }
    var equity by remember { mutableStateOf("10000") }
    var signalTime by remember { mutableStateOf("") }
    var riskResult by remember { mutableStateOf<JSONObject?>(null) }

    var csvText by remember { mutableStateOf<String?>(null) }
    var csvName by remember { mutableStateOf("No OHLCV file selected") }
    var regimeResult by remember { mutableStateOf<JSONObject?>(null) }
    var backtestResult by remember { mutableStateOf<JSONObject?>(null) }

    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                csvText = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: throw IllegalStateException("Unable to read selected CSV")
                }
                csvName = uri.lastPathSegment ?: "OHLCV CSV loaded"
                regimeResult = null
                backtestResult = null
                errorMessage = null
            } catch (t: Throwable) {
                errorMessage = t.message ?: "CSV import failed"
            }
        }
    }

    fun execute(request: JSONObject, success: (JSONObject) -> Unit) {
        if (busy) return
        busy = true
        errorMessage = null
        scope.launch {
            try {
                success(PythonForexEngine.call(context, request))
            } catch (t: Throwable) {
                errorMessage = t.message ?: "Python engine operation failed"
            } finally {
                busy = false
            }
        }
    }

    fun buildSignal(): JSONObject = JSONObject()
        .put("symbol", symbol.trim().uppercase())
        .put("direction", direction)
        .put("strength", "General")
        .put("timestamp", signalTime.ifBlank { java.time.Instant.now().toString() })
        .put("entry_price", entry.trim())
        .put("stop_loss", stop.trim())
        .put("take_profit", target.trim())

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Forex engine", style = MaterialTheme.typography.headlineLarge)
            Text("Bundled Python analytics · NumPy · pandas · SQLite",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Engine runtime", style = MaterialTheme.typography.titleLarge)
                    Text(runtime, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = {
                            execute(JSONObject().put("operation", "health")) { data ->
                                runtime = "Ready · Python " + data.optString("python") +
                                    " · NumPy " + data.optString("numpy") +
                                    " · pandas " + data.optString("pandas")
                            }
                        },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp)
                    ) { Text("Check Python engine") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("XTrend Speed parser", style = MaterialTheme.typography.titleLarge)
                    Text("Paste copied signal text or JSON. Structured JSON can include entry, stop-loss and take-profit prices.")
                    OutlinedTextField(
                        value = pastedSignals, onValueChange = { pastedSignals = it },
                        label = { Text("Signal text / JSON") }, minLines = 4, maxLines = 8,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)
                    )
                    Button(
                        onClick = {
                            execute(JSONObject().put("operation", "parse_signals").put("text", pastedSignals)) { data ->
                                val rows = data.optJSONArray("signals") ?: JSONArray()
                                parsedSignals = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                                journalMessage = "Parsed " + parsedSignals.size + " signal(s)"
                            }
                        },
                        enabled = !busy && pastedSignals.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Parse signals")
                    }
                    if (parsedSignals.isNotEmpty()) {
                        Text(parsedSignals.size.toString() + " parsed signal(s)", fontWeight = FontWeight.SemiBold)
                        parsedSignals.forEach { row ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(row.optString("symbol") + " · " + row.optString("direction"),
                                        fontWeight = FontWeight.SemiBold)
                                    Text(row.optString("strength") + " · " + row.optString("timestamp"),
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Entry " + row.optString("entry_price", "—") +
                                            "  SL " + row.optString("stop_loss", "—") +
                                            "  TP " + row.optString("take_profit", "—"),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                val array = JSONArray().apply { parsedSignals.forEach { put(it) } }
                                execute(JSONObject().put("operation", "track_signals").put("signals", array)) { data ->
                                    journalMessage = "Stored " + data.optInt("tracked") + " signal(s) in local journal"
                                }
                            },
                            enabled = !busy, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(26.dp)
                        ) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Save signals to journal")
                        }
                    }
                    journalMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Risk validation & position sizing", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(symbol, { symbol = it.uppercase() }, label = { Text("Symbol") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf("Buy", "Sell").forEachIndexed { index, side ->
                            SegmentedButton(direction == side, { direction = side },
                                shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(side) }
                        }
                    }
                    OutlinedTextField(entry, { entry = it }, label = { Text("Entry price") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                    OutlinedTextField(stop, { stop = it }, label = { Text("Stop loss") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                    OutlinedTextField(target, { target = it }, label = { Text("Take profit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                    OutlinedTextField(equity, { equity = it }, label = { Text("Account equity (USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                    Button(
                        onClick = {
                            execute(
                                JSONObject().put("operation", "validate_signal")
                                    .put("signal", buildSignal())
                                    .put("account", JSONObject().put("equity", equity.trim()))
                            ) { riskResult = it }
                        },
                        enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp)
                    ) { Text("Validate setup") }
                    riskResult?.let { result ->
                        Text(if (result.optBoolean("approved")) "APPROVED" else "NOT APPROVED",
                            color = if (result.optBoolean("approved")) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        Text(result.optString("reason"))
                        result.optJSONObject("position_size")?.let { size ->
                            Text("Size " + size.optString("lots") + " lots · Risk " +
                                size.optString("risk_amount") + " · Margin " + size.optString("margin_required"))
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("OHLCV market data", style = MaterialTheme.typography.titleLarge)
                    Text("Import a historical CSV with timestamp, open, high, low, close and optional volume columns.")
                    OutlinedButton(
                        onClick = { csvPicker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(csvName)
                    }
                    Button(
                        onClick = {
                            execute(
                                JSONObject().put("operation", "analyze_csv")
                                    .put("symbol", symbol.trim().uppercase())
                                    .put("csv_text", csvText)
                            ) { regimeResult = it }
                        },
                        enabled = !busy && !csvText.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)
                    ) { Text("Analyse market regime") }
                    regimeResult?.let { result ->
                        val regime = result.optJSONObject("regime")
                        Text(result.optString("symbol") + " · " + result.optInt("bars") + " bars")
                        if (regime != null) {
                            Text(regime.optString("regime"), style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text("ADX " + regime.optString("adx") + " · Confidence " + regime.optString("confidence"))
                            Text("Trend strength " + regime.optString("trend_strength") + " · ATR " + regime.optString("atr_pct"))
                        }
                    }
                }
            }
        }

        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Historical backtest", style = MaterialTheme.typography.titleLarge)
                    Text("Uses the imported OHLCV file and the signal fields above. Set a timestamp inside the CSV period.")
                    OutlinedTextField(signalTime, { signalTime = it },
                        label = { Text("Signal timestamp (ISO-8601)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp))
                    Button(
                        onClick = {
                            execute(
                                JSONObject().put("operation", "backtest_csv")
                                    .put("symbol", symbol.trim().uppercase())
                                    .put("csv_text", csvText)
                                    .put("signals", JSONArray().put(buildSignal()))
                                    .put("initial_equity", equity.trim())
                                    .put("timeframe", "M15")
                            ) { backtestResult = it }
                        },
                        enabled = !busy && !csvText.isNullOrBlank() && signalTime.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(26.dp)
                    ) { Text("Run backtest") }
                    backtestResult?.let { result ->
                        Text(result.optInt("trade_count").toString() + " completed trade(s)", fontWeight = FontWeight.SemiBold)
                        result.optJSONObject("summary")?.let { summary ->
                            Text("Net profit " + summary.optString("net_profit") +
                                " · Win rate " + summary.optString("win_rate"))
                            Text("Max drawdown " + summary.optString("max_drawdown_pct") +
                                "% · Average R " + summary.optString("avg_r_multiple"))
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = {
                    execute(JSONObject().put("operation", "list_signals").put("limit", 30)) { data ->
                        val rows = data.optJSONArray("signals") ?: JSONArray()
                        journal = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
                    }
                },
                enabled = !busy, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp)
            ) { Text("Load local signal journal") }
        }
        items(journal) { row ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(row.optString("symbol") + " · " + row.optString("direction"), fontWeight = FontWeight.SemiBold)
                    Text(row.optString("status") + " · " + row.optString("timestamp"),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (errorMessage != null) {
            item { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
        }
        item {
            Text(
                "No live market feed or broker execution is connected. Results depend on the supplied data and assumptions; they are not financial advice or guaranteed outcomes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
