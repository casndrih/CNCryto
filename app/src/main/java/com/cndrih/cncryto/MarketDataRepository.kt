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
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

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

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", "CNCryto-Android/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: throw IOException("Empty response")
        }
    }

    suspend fun quotes(category: String): List<MarketQuote> = withContext(Dispatchers.IO) {
        when (category) {
            "Crypto" -> listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT").map { symbol ->
                runCatching { binanceQuote(symbol) }.getOrElse { MarketQuote(symbol, symbol, category, null, null, "", "Binance public API", "Live", it.message) }
            }
            "Forex" -> listOf("EUR/USD", "GBP/USD", "USD/JPY", "AUD/USD", "USD/CAD", "NZD/USD").map { pair ->
                runCatching { frankfurterQuote(pair) }.getOrElse { MarketQuote(pair, pair, category, null, null, "", "ECB via Frankfurter", "Daily reference", it.message) }
            }
            "Equities" -> listOf("S&P 500" to "spy.us", "NASDAQ 100" to "qqq.us", "DOW 30" to "dia.us", "Russell 2000" to "iwm.us").map { (label, ticker) ->
                runCatching { stooqQuote(label, ticker) }.getOrElse { MarketQuote(label, ticker, category, null, null, "", "Stooq", "Daily close", it.message) }
            }
            else -> listOf("Gold" to "xauusd", "Silver" to "xagusd", "WTI Crude" to "cl.f", "Brent Crude" to "cb.f").map { (label, ticker) ->
                runCatching { stooqQuote(label, ticker) }.getOrElse { MarketQuote(label, ticker, category, null, null, "", "Stooq", "Daily close", it.message) }
            }
        }
    }

    private fun binanceQuote(symbol: String): MarketQuote {
        val o = JSONObject(get("https://api.binance.com/api/v3/ticker/24hr?symbol=$symbol"))
        return MarketQuote(symbol, symbol, "Crypto", o.optString("lastPrice").toDoubleOrNull(),
            o.optString("priceChangePercent").toDoubleOrNull(), o.optString("closeTime"), "Binance", "Live · 24h")
    }

    private fun frankfurterQuote(pair: String): MarketQuote {
        val parts = pair.split("/")
        val o = JSONObject(get("https://api.frankfurter.dev/v2/rates?base=${parts[0]}&quotes=${parts[1]}"))
        val first = o.optJSONObject("0") ?: throw IOException("No ECB reference rate")
        return MarketQuote(pair, pair, "Forex", first.optDouble("rate").takeIf { it > 0 },
            null, first.optString("date"), "Frankfurter / ECB", "Daily reference")
    }

    private fun stooqQuote(label: String, ticker: String): MarketQuote {
        val csv = get("https://stooq.com/q/d/l/?s=$ticker&i=d").trim()
        val rows = csv.lines().drop(1).filter { it.isNotBlank() }.takeLast(2)
        if (rows.isEmpty()) throw IOException("No Stooq data for $ticker")
        val last = rows.last().split(",")
        val previous = rows.getOrNull(rows.lastIndex - 1)?.split(",")
        val close = last.getOrNull(4)?.toDoubleOrNull() ?: throw IOException("Invalid close")
        val prior = previous?.getOrNull(4)?.toDoubleOrNull()
        val change = if (prior != null && prior != 0.0) (close - prior) / prior * 100.0 else null
        return MarketQuote(label, ticker, "Equities", close, change, last.firstOrNull().orEmpty(), "Stooq", "Daily close")
    }

    suspend fun candles(symbol: String, category: String): List<MarketCandle> = withContext(Dispatchers.IO) {
        when (category) {
            "Crypto" -> {
                val pair = symbol.replace("/", "").uppercase().let { if (it.endsWith("USD") && !it.endsWith("USDT")) it + "T" else it }
                val a = JSONArray(get("https://api.binance.com/api/v3/klines?symbol=$pair&interval=1h&limit=80"))
                (0 until a.length()).map { i ->
                    val c = a.getJSONArray(i)
                    MarketCandle(c.getLong(0), c.getString(1).toDouble(), c.getString(2).toDouble(), c.getString(3).toDouble(), c.getString(4).toDouble())
                }
            }
            "Forex" -> {
                val parts = symbol.replace("/", "").let { listOf(it.substring(0, 3), it.substring(3, 6)) }
                val end = LocalDate.now(ZoneOffset.UTC)
                val start = end.minusDays(90)
                val raw = get("https://api.frankfurter.dev/v2/rates?base=${parts[0]}&quotes=${parts[1]}&from=$start&to=$end")
                val a = JSONArray(raw)
                (0 until a.length()).map { i ->
                    val o = a.getJSONObject(i); val v = o.getDouble("rate")
                    MarketCandle(LocalDate.parse(o.getString("date")).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), v, v, v, v)
                }
            }
            else -> {
                val ticker = when (symbol.uppercase()) {
                    "S&P 500", "SPX", "^GSPC" -> "spy.us"
                    "NASDAQ 100", "NDX" -> "qqq.us"
                    "DOW 30", "DJI" -> "dia.us"
                    "RUSSELL 2000" -> "iwm.us"
                    "XAU/USD", "XAUUSD", "GOLD" -> "xauusd"
                    "XAG/USD", "XAGUSD", "SILVER" -> "xagusd"
                    "WTI", "WTI/USD" -> "cl.f"
                    "BRENT", "BRENT/USD" -> "cb.f"
                    else -> symbol.lowercase().replace("/", "").let { if (it.endsWith(".us") || category == "Metals") it else "${it}.us" }
                }
                val csv = get("https://stooq.com/q/d/l/?s=$ticker&i=d")
                csv.lines().drop(1).filter { it.isNotBlank() }.takeLast(80).mapNotNull { line ->
                    val p = line.split(",")
                    if (p.size < 5) null else runCatching {
                        MarketCandle(LocalDate.parse(p[0]).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                            p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble())
                    }.getOrNull()
                }
            }
        }
    }

    suspend fun treasuryYields(): List<BondYield> = withContext(Dispatchers.IO) {
        listOf("DGS1MO" to "1 mo", "DGS1" to "1 yr", "DGS2" to "2 yr", "DGS5" to "5 yr", "DGS10" to "10 yr", "DGS30" to "30 yr").map { (series, label) ->
            runCatching {
                val csv = get("https://fred.stlouisfed.org/graph/fredgraph.csv?id=$series").trim()
                val row = csv.lines().drop(1).lastOrNull { it.contains(",") && !it.endsWith(".") } ?: throw IOException("No recent value")
                val parts = row.split(",")
                BondYield(label, parts.getOrNull(1)?.toDoubleOrNull(), parts.firstOrNull(), "FRED / U.S. Treasury")
            }.getOrElse { BondYield(label, null, null, "FRED · ${it.message ?: "unavailable"}") }
        }
    }
}
