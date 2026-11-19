package com.cndrih.cncryto

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val TradeGreen = Color(0xFF20E879)
private val TradeRed = Color(0xFFE45B70)
private val TradePanel = Color(0xFF171D1A)

data class OpenTrade(
    val id: Long,
    val symbol: String,
    val side: String,
    val units: Double,
    val entry: Double,
    val current: Double,
    val stop: Double,
    val target: Double,
    val openedAt: String,
    val status: String = "OPEN"
) {
    val pnl: Double get() = (current - entry) * units * if (side == "BUY") 1.0 else -1.0
    val risk: Double get() = abs(entry - stop) * units
    val reward: Double get() = abs(target - entry) * units
    val rr: Double get() = if (risk > 0.0) reward / risk else 0.0
}

private object TradeStore {
    private const val PREFS = "cncryto_trades"
    private const val KEY = "items"
    fun load(context: Context): List<OpenTrade> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                OpenTrade(o.getLong("id"), o.getString("symbol"), o.getString("side"),
                    o.getDouble("units"), o.getDouble("entry"), o.getDouble("current"),
                    o.getDouble("stop"), o.getDouble("target"), o.optString("openedAt"), o.optString("status", "OPEN"))
            }
        }.getOrDefault(emptyList())
    }
    fun save(context: Context, trades: List<OpenTrade>) {
        val array = JSONArray()
        trades.forEach { t ->
            array.put(JSONObject().put("id", t.id).put("symbol", t.symbol).put("side", t.side)
                .put("units", t.units).put("entry", t.entry).put("current", t.current)
                .put("stop", t.stop).put("target", t.target).put("openedAt", t.openedAt).put("status", t.status))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

@Composable
fun MarketsScreen(onOpenTrades: () -> Unit, modifier: Modifier = Modifier) {
    var category by remember { mutableStateOf("Forex") }
    var search by remember { mutableStateOf("") }
    val instruments = when (category) {
        "Crypto" -> listOf("BTC/USD", "ETH/USD", "SOL/USD", "XRP/USD")
        "Metals" -> listOf("XAU/USD", "XAG/USD", "WTI/USD", "BRENT")
        "Equities" -> listOf("S&P 500", "NASDAQ 100", "DOW 30", "Russell 2000")
        else -> listOf("EUR/USD", "GBP/USD", "USD/JPY", "AUD/USD", "USD/CAD", "NZD/USD")
    }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Markets", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Your market overview", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(50), color = TradePanel) {
                    Icon(Icons.Default.ShowChart, null, tint = TradeGreen, modifier = Modifier.padding(12.dp).size(22.dp))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = TradePanel), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MARKET DATA", style = MaterialTheme.typography.labelMedium, color = TradeGreen)
                    Text("Connect a price provider", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Live quotes, market movers, bond yields and interactive charts will appear here when a market-data API is configured. No sample prices are presented as real.", color = Color(0xFFB7C0BA))
                    OutlinedButton(onClick = onOpenTrades, shape = RoundedCornerShape(24.dp)) { Text("View trade journal") }
                }
            }
        }
        item {
            Text("Markets at-a-glance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Forex", "Crypto", "Metals", "Equities").forEachIndexed { index, label ->
                    SegmentedButton(category == label, { category = label },
                        shape = SegmentedButtonDefaults.itemShape(index, 4)) { Text(label, maxLines = 1) }
                }
            }
        }
        item {
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Search markets") }, singleLine = true, shape = RoundedCornerShape(28.dp))
        }
        items(instruments.filter { it.contains(search, true) }) { symbol ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenTrades() },
                colors = CardDefaults.cardColors(containerColor = TradePanel),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF26352D)) {
                        Icon(Icons.Default.TrendingUp, null, tint = TradeGreen, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(symbol, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(category, color = Color(0xFF9BA69E), style = MaterialTheme.typography.bodySmall)
                    }
                    Text("—", color = Color(0xFF9BA69E))
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = TradePanel), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bond yields", style = MaterialTheme.typography.titleLarge, color = Color.White)
                    Text("Treasuries and corporate yields", color = Color(0xFFB7C0BA))
                    listOf("U.S. Treasury", "Corporate AAA", "Corporate AA", "Corporate A", "Corporate BBB").forEach {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(it, color = Color.White)
                            Text("Connect data feed", color = Color(0xFF9BA69E), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item { Text("Tap a market to continue to your trade journal. Market prices are intentionally blank until a verified feed is configured.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun TradesScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var trades by remember { mutableStateOf(TradeStore.load(context)) }
    var selected by remember { mutableStateOf<OpenTrade?>(null) }
    var editing by remember { mutableStateOf<OpenTrade?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    fun persist(next: List<OpenTrade>) { trades = next; TradeStore.save(context, next) }

    if (selected != null) {
        TradeDetailScreen(
            trade = selected!!,
            onBack = { selected = null },
            onModify = { editing = it; showEditor = true },
            onClose = { t -> persist(trades.map { if (it.id == t.id) t.copy(status = "CLOSED") else it }); selected = null }
        )
    } else {
        val open = trades.filter { it.status == "OPEN" }
        val closed = trades.filter { it.status == "CLOSED" }
        LazyColumn(modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("My trades", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("Manage open positions and trade history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledIconButton(onClick = { editing = null; showEditor = true }, shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Add trade")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("OPEN", open.size.toString(), Modifier.weight(1f))
                    MetricCard("OPEN P/L", money(open.sumOf { it.pnl }), Modifier.weight(1f))
                    MetricCard("CLOSED", closed.size.toString(), Modifier.weight(1f))
                }
            }
            if (open.isEmpty()) item { EmptyTradesCard { editing = null; showEditor = true } }
            items(open, key = { it.id }) { trade -> TradeRow(trade) { selected = trade } }
            if (closed.isNotEmpty()) {
                item { Text("Trade history", style = MaterialTheme.typography.titleLarge) }
                items(closed, key = { it.id }) { trade -> TradeRow(trade) { selected = trade } }
            }
            item { Text("Positions are stored locally on this device. They are journal entries only; this app is not connected to a broker and does not place or close real orders.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)) }
        }
    }
    if (showEditor) {
        TradeEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false; editing = null },
            onSave = { value ->
                val next = if (editing == null) trades + value else trades.map { if (it.id == value.id) value else it }
                persist(next)
                if (selected?.id == value.id) selected = value
                showEditor = false; editing = null
            }
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = TradePanel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9BA69E))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyTradesCard(onAdd: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = TradePanel), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Wallet, null, tint = TradeGreen, modifier = Modifier.size(42.dp))
            Text("No open positions yet", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text("Add a position to track entry, stop loss, take profit, risk/reward and open P/L.", color = Color(0xFFB7C0BA))
            Button(onClick = onAdd, shape = RoundedCornerShape(24.dp)) { Text("Add a trade") }
        }
    }
}

@Composable
private fun TradeRow(trade: OpenTrade, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = TradePanel), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(trade.symbol, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${trade.side} · ${number(trade.units)} units · ${trade.status}", color = Color(0xFF9BA69E), style = MaterialTheme.typography.bodySmall)
                Text("Entry ${number(trade.entry)}  ·  SL ${number(trade.stop)}  ·  TP ${number(trade.target)}", color = Color(0xFFB7C0BA), style = MaterialTheme.typography.bodySmall)
            }
            Text(money(trade.pnl), color = if (trade.pnl >= 0) TradeGreen else TradeRed, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TradeDetailScreen(
    trade: OpenTrade,
    onBack: () -> Unit,
    onModify: (OpenTrade) -> Unit,
    onClose: (OpenTrade) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text(trade.symbol, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(trade.status, color = if (trade.status == "OPEN") TradeGreen else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(colors = CardDefaults.cardColors(containerColor = TradePanel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${trade.side} · ${number(trade.units)} units", color = if (trade.side == "BUY") TradeGreen else TradeRed, fontWeight = FontWeight.SemiBold)
                Text(number(trade.current), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text("Mark price entered in your journal · not a live quote", color = Color(0xFF9BA69E), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = TradePanel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Position chart", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Illustrative candles · horizontal levels reflect this trade", color = Color(0xFF9BA69E), style = MaterialTheme.typography.bodySmall)
                PositionChart(trade, Modifier.fillMaxWidth().height(250.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("SL  ${number(trade.stop)}", color = TradeRed, style = MaterialTheme.typography.labelSmall)
                    Text("ENTRY  ${number(trade.entry)}", color = Color(0xFF70A7FF), style = MaterialTheme.typography.labelSmall)
                    Text("TP  ${number(trade.target)}", color = TradeGreen, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("OPEN P/L", money(trade.pnl), Modifier.weight(1f))
            MetricCard("RISK / REWARD", "1 : ${String.format(Locale.US, "%.2f", trade.rr)}", Modifier.weight(1f))
            MetricCard("POSITION", number(trade.units), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onClose(trade) }, enabled = trade.status == "OPEN",
                modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TradeRed),
                shape = RoundedCornerShape(22.dp)) { Text("Close trade") }
            OutlinedButton(onClick = { onModify(trade) }, enabled = trade.status == "OPEN",
                modifier = Modifier.weight(1f), shape = RoundedCornerShape(22.dp)) { Text("Modify") }
        }
        Text("Close trade records the position as closed in this local journal only. It does not send an order to a broker.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PositionChart(trade: OpenTrade, modifier: Modifier = Modifier) {
    val grid = Color(0xFF39433D)
    Canvas(modifier.background(Color(0xFF111713), RoundedCornerShape(12.dp))) {
        val left = 8f; val right = size.width - 8f; val top = 12f; val bottom = size.height - 12f
        for (i in 0..4) {
            val y = top + (bottom - top) * i / 4f
            drawLine(grid, Offset(left, y), Offset(right, y), 1f)
        }
        val low = min(trade.stop, min(trade.entry, trade.target))
        val high = max(trade.stop, max(trade.entry, trade.target))
        val range = (high - low).coerceAtLeast(abs(high) * 0.0005).toFloat()
        fun y(price: Double) = bottom - ((price - low).toFloat() / range) * (bottom - top)
        val levels = listOf(trade.stop to TradeRed, trade.entry to Color(0xFF70A7FF), trade.target to TradeGreen)
        levels.forEach { (price, color) ->
            val yy = y(price)
            drawLine(color, Offset(left, yy), Offset(right, yy), 2f)
        }
        // A neutral, illustrative candle pattern; it is deliberately not market history.
        val values = listOf(.82f,.74f,.78f,.63f,.68f,.54f,.59f,.47f,.52f,.40f,.44f,.36f,.42f,.31f,.35f,.27f,.32f,.22f)
        val step = (right - left) / (values.size + 1)
        values.forEachIndexed { i, v ->
            val x = left + step * (i + 1)
            val center = top + v * (bottom - top)
            val up = i % 3 != 0
            val candle = if (up) TradeGreen else TradeRed
            val body = 7f
            drawLine(candle, Offset(x, center - 13f), Offset(x, center + 13f), 2f)
            drawRect(candle, Offset(x - body / 2, center - 6f), androidx.compose.ui.geometry.Size(body, 12f))
        }
    }
}

@Composable
private fun TradeEditorDialog(initial: OpenTrade?, onDismiss: () -> Unit, onSave: (OpenTrade) -> Unit) {
    var symbol by remember(initial) { mutableStateOf(initial?.symbol ?: "EUR/USD") }
    var side by remember(initial) { mutableStateOf(initial?.side ?: "BUY") }
    var units by remember(initial) { mutableStateOf(initial?.units?.toString() ?: "10000") }
    var entry by remember(initial) { mutableStateOf(initial?.entry?.toString() ?: "") }
    var current by remember(initial) { mutableStateOf(initial?.current?.toString() ?: "") }
    var stop by remember(initial) { mutableStateOf(initial?.stop?.toString() ?: "") }
    var target by remember(initial) { mutableStateOf(initial?.target?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add position" else "Modify position") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(symbol, { symbol = it.uppercase() }, label = { Text("Instrument") }, singleLine = true, shape = RoundedCornerShape(24.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("BUY", "SELL").forEachIndexed { i, value ->
                        SegmentedButton(side == value, { side = value }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(value) }
                    }
                }
                listOf("Position size (units)" to units, "Entry price" to entry, "Current / mark price" to current, "Stop loss" to stop, "Take profit" to target).forEach { (label, value) ->
                    OutlinedTextField(value,
                        { v -> when(label) { "Position size (units)" -> units = v; "Entry price" -> entry = v; "Current / mark price" -> current = v; "Stop loss" -> stop = v; else -> target = v } },
                        label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth())
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val u = units.toDoubleOrNull(); val e = entry.toDoubleOrNull(); val c = current.toDoubleOrNull()
                val s = stop.toDoubleOrNull(); val t = target.toDoubleOrNull()
                val validLevels = if (side == "BUY" && e != null && s != null && t != null) s < e && e < t
                    else if (side == "SELL" && e != null && s != null && t != null) t < e && e < s else false
                if (symbol.isBlank() || u == null || u <= 0 || e == null || e <= 0 || c == null || c <= 0 || s == null || t == null || !validLevels) {
                    error = "Enter valid positive values. BUY requires SL < Entry < TP; SELL requires TP < Entry < SL."
                } else {
                    onSave(OpenTrade(initial?.id ?: System.currentTimeMillis(), symbol.trim(), side, u, e, c, s, t,
                        initial?.openedAt ?: java.time.Instant.now().toString(), initial?.status ?: "OPEN"))
                }
            }) { Text("Save position") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun number(value: Double): String = String.format(Locale.US, "%.5f", value).trimEnd('0').trimEnd('.')
private fun money(value: Double): String = String.format(Locale.US, "%s%.2f USD", if (value < 0) "−" else "+", abs(value))
