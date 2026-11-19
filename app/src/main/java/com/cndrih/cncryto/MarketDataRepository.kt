package com.cndrih.cncryto

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

data class MarketQuote(
    val symbol: String,
    val name: String,
    val category: String,
    val price: Double?,
    val changePct: Double?,
    val timestamp: String,
    val source: String,
    val delayLabel: String,
    val error: String? = null
)

data class MarketCandle(val time: Long, val open: Double, val high: Double, val low: Double, val close: Double)
data class BondYield(val label: String, val value: Double?, val date: String?, val source: String)

object MarketDataRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()
    private val quoteCache = ConcurrentHashMap<String, Pair<Long, List<MarketQuote>>>()
    private val candleCache = ConcurrentHashMap<String, Pair<Long, List<MarketCandle>>>()
    private const val CACHE_TTL_MS = 300_000L

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "CNCryto-Android/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }

    private fun alphaJson(function: String, params: String, key: String): JSONObject {
        require(key.isNotBlank()) { "Add a free Alpha Vantage API key in Settings to enable this market." }
        val json = JSONObject(get("https://www.alphavantage.co/query?function=$function&$params&apikey=$key"))
        val issue = json.optString("Information").ifBlank { json.optString("Note") }.ifBlank { json.optString("Error Message") }
        if (issue.isNotBlank()) throw IOException(issue.take(220))
        return json
    }

    suspend fun quotes(category: String, alphaKey: String = ""): List<MarketQuote> = withContext(Dispatchers.IO) {
        val cacheKey = "$category:${alphaKey.hashCode()}"
        quoteCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.let { return@withContext it.second }
        val rows = when (category) {
            "Crypto" -> listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT").map { symbol ->
                runCatching { binanceQuote(symbol) }.getOrElse { MarketQuote(symbol, symbol, category, null, null, "", "Binance public API", "Live · 24h", it.message) }
            }
            "Forex" -> listOf("EUR/USD", "GBP/USD", "USD/JPY", "AUD/USD", "USD/CAD", "NZD/USD").map { pair ->
                runCatching { frankfurterQuote(pair) }.getOrElse { MarketQuote(pair, pair, category, null, null, "", "Frankfurter", "Daily reference", it.message) }
            }
            "Equities" -> listOf("S&P 500" to "SPY", "NASDAQ 100" to "QQQ", "DOW 30" to "DIA", "Russell 2000" to "IWM").map { (label, ticker) ->
                runCatching { alphaDailyQuote(label, ticker, alphaKey) }.getOrElse {
                    MarketQuote(ticker, label, category, null, null, "", "Alpha Vantage", "Daily · free key", it.message)
                }
            }
            else -> listOf("Gold" to "GOLD", "Silver" to "SILVER", "WTI Crude" to "WTI", "Brent Crude" to "BRENT").map { (label, instrument) ->
                runCatching { alphaCommodityQuote(label, instrument, alphaKey) }.getOrElse {
                    MarketQuote(instrument, label, category, null, null, "", "Alpha Vantage", "Daily/spot · free key", it.message)
                }
            }
        }
        quoteCache[cacheKey] = System.currentTimeMillis() to rows
        rows
    }

    private fun binanceQuote(symbol: String): MarketQuote {
        val o = JSONObject(get("https://data-api.binance.vision/api/v3/ticker/24hr?symbol=$symbol"))
        return MarketQuote(symbol, symbol, "Crypto", o.optString("lastPrice").toDoubleOrNull(),
            o.optString("priceChangePercent").toDoubleOrNull(), o.optString("closeTime"), "Binance", "Live · 24h")
    }

    private fun frankfurterQuote(pair: String): MarketQuote {
        val parts = pair.split("/")
        val a = JSONArray(get("https://api.frankfurter.dev/v2/rates?base=${parts[0]}&quotes=${parts[1]}"))
        val first = a.optJSONObject(0) ?: throw IOException("No reference rate published for $pair")
        return MarketQuote(pair, pair, "Forex", first.optDouble("rate").takeIf { it > 0 },
            null, first.optString("date"), "Frankfurter / central banks", "Daily reference")
    }

    private fun alphaDailyQuote(label: String, symbol: String, key: String): MarketQuote {
        val json = alphaJson("TIME_SERIES_DAILY", "symbol=$symbol&outputsize=compact", key)
        val seriesKey = json.keys().asSequence().firstOrNull { it.startsWith("Time Series") }
            ?: throw IOException("No daily series returned for $symbol")
        val series = json.getJSONObject(seriesKey)
        val dates = series.keys().asSequence().toList().sortedDescending().take(2)
        val latest = series.getJSONObject(dates[0]).getString("4. close").toDouble()
        val previous = dates.getOrNull(1)?.let { series.getJSONObject(it).optString("4. close").toDoubleOrNull() }
        val change = if (previous != null && previous != 0.0) (latest - previous) / previous * 100.0 else null
        return MarketQuote(symbol, label, "Equities", latest, change, dates[0], "Alpha Vantage", "Daily close")
    }

    private fun alphaCommodityQuote(label: String, instrument: String, key: String): MarketQuote {
        if (instrument == "GOLD" || instrument == "SILVER") {
            val json = alphaJson("GOLD_SILVER_SPOT", "symbol=$instrument", key)
            val price = json.optString("price").toDoubleOrNull()
                ?: json.optJSONObject("data")?.optString("price")?.toDoubleOrNull()
                ?: throw IOException("Spot price not present in provider response")
            return MarketQuote(instrument, label, "Metals", price, null,
                json.optString("last_updated").ifBlank { json.optString("last_updated_utc") },
                "Alpha Vantage", "Spot snapshot")
        }
        val json = alphaJson(instrument, "interval=daily", key)
        val data = json.optJSONArray("data") ?: throw IOException("No commodity history returned")
        val latest = data.optJSONObject(0) ?: throw IOException("No latest commodity observation")
        val price = latest.optString("value").toDoubleOrNull() ?: throw IOException("Invalid commodity price")
        return MarketQuote(instrument, label, "Metals", price, null, latest.optString("date"), "Alpha Vantage / EIA", "Daily")
    }

    suspend fun candles(symbol: String, category: String, alphaKey: String = ""): List<MarketCandle> = withContext(Dispatchers.IO) {
        val cacheKey = "$category:$symbol:${alphaKey.hashCode()}"
        candleCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.first < CACHE_TTL_MS }?.let { return@withContext it.second }
        val rows = when (category) {
            "Crypto" -> {
                val pair = symbol.replace("/", "").uppercase(Locale.US).let { if (it.endsWith("USD") && !it.endsWith("USDT")) it + "T" else it }
                val a = JSONArray(get("https://data-api.binance.vision/api/v3/klines?symbol=$pair&interval=1h&limit=80"))
                (0 until a.length()).map { i ->
                    val c = a.getJSONArray(i)
                    MarketCandle(c.getLong(0), c.getString(1).toDouble(), c.getString(2).toDouble(), c.getString(3).toDouble(), c.getString(4).toDouble())
                }
            }
            "Forex" -> {
                val compact = symbol.replace("/", "").uppercase(Locale.US)
                val parts = listOf(compact.substring(0, 3), compact.substring(3, 6))
                val end = LocalDate.now(ZoneOffset.UTC)
                val start = end.minusDays(90)
                val raw = get("https://api.frankfurter.dev/v2/rates?base=${parts[0]}&quotes=${parts[1]}&from=$start&to=$end")
                val a = JSONArray(raw)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i); val v = o.getDouble("rate")
                    val time = LocalDate.parse(o.getString("date")).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                    MarketCandle(time, v, v, v, v)
                }
            }
            "Equities" -> alphaEquityCandles(symbol, alphaKey)
            else -> alphaCommodityCandles(symbol, alphaKey)
        }
        candleCache[cacheKey] = System.currentTimeMillis() to rows
        rows
    }

    private fun alphaEquityCandles(symbol: String, key: String): List<MarketCandle> {
        val ticker = when (symbol.uppercase(Locale.US)) {
            "S&P 500", "SPX", "^GSPC" -> "SPY"
            "NASDAQ 100", "NDX" -> "QQQ"
            "DOW 30", "DJI" -> "DIA"
            "RUSSELL 2000" -> "IWM"
            else -> symbol.uppercase(Locale.US)
        }
        val json = alphaJson("TIME_SERIES_DAILY", "symbol=$ticker&outputsize=compact", key)
        val seriesKey = json.keys().asSequence().firstOrNull { it.startsWith("Time Series") }
            ?: throw IOException("No daily candles returned for $ticker")
        val series = json.getJSONObject(seriesKey)
        return series.keys().asSequence().toList().sorted().takeLast(80).map { date ->
            val o = series.getJSONObject(date)
            MarketCandle(LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                o.getString("1. open").toDouble(), o.getString("2. high").toDouble(),
                o.getString("3. low").toDouble(), o.getString("4. close").toDouble())
        }
    }

    private fun alphaCommodityCandles(symbol: String, key: String): List<MarketCandle> {
        val upper = symbol.uppercase(Locale.US)
        val instrument = when {
            upper.contains("XAU") || upper == "GOLD" -> "GOLD"
            upper.contains("XAG") || upper == "SILVER" -> "SILVER"
            upper.contains("WTI") -> "WTI"
            upper.contains("BRENT") -> "BRENT"
            else -> upper
        }
        val json = if (instrument == "GOLD" || instrument == "SILVER") {
            alphaJson("GOLD_SILVER_HISTORY", "symbol=$instrument&interval=daily", key)
        } else {
            alphaJson(instrument, "interval=daily", key)
        }
        val data = json.optJSONArray("data") ?: throw IOException("No daily history for $instrument")
        return (0 until data.length()).mapNotNull { i ->
            val o = data.optJSONObject(i) ?: return@mapNotNull null
            val value = o.optString("value").toDoubleOrNull() ?: return@mapNotNull null
            val date = o.optString("date")
            runCatching {
                val time = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                MarketCandle(time, value, value, value, value)
            }.getOrNull()
        }
    }

    suspend fun treasuryYields(): List<BondYield> = withContext(Dispatchers.IO) {
        listOf("DGS1MO" to "1 mo", "DGS1" to "1 yr", "DGS2" to "2 yr", "DGS5" to "5 yr", "DGS10" to "10 yr", "DGS30" to "30 yr").map { (series, label) ->
            runCatching {
                val csv = get("https://fred.stlouisfed.org/graph/fredgraph.csv?id=$series").trim()
                val row = csv.lines().drop(1).lastOrNull { it.contains(",") && !it.endsWith(".") } ?: throw IOException("No recent value")
                val parts = row.split(",")
                BondYield(label, parts.getOrNull(1)?.toDoubleOrNull(), parts.firstOrNull(), "FRED graph CSV")
            }.getOrElse { BondYield(label, null, null, "FRED · ${it.message ?: "unavailable"}") }
        }
    }
}
