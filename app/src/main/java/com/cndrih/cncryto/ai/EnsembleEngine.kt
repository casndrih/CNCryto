package com.cndrih.cncryto.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.TimeUnit

enum class ProviderProtocol { OPENAI_COMPATIBLE }

data class ProviderConfig(
    val id: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String,
    val enabled: Boolean = true,
    val protocol: ProviderProtocol = ProviderProtocol.OPENAI_COMPATIBLE,
    val timeoutMillis: Long = 12_000L
)

data class MarketContext(val qualityNote: String, val features: List<Double>)
data class PredictionRequest(
    val symbol: String,
    val timeframe: String,
    val horizon: String,
    val providers: List<ProviderConfig>,
    val market: MarketContext
)
data class ProviderForecast(
    val providerId: String,
    val model: String,
    val direction: String,
    val score: Double,
    val confidence: Double,
    val rationale: String
)
data class EnsembleResult(
    val direction: String,
    val score: Double,
    val agreement: Double,
    val contributors: Int,
    val summary: String,
    val generatedAt: String
)

interface AIProvider {
    val protocol: ProviderProtocol
    suspend fun predict(config: ProviderConfig, request: PredictionRequest): ProviderForecast
}

class OpenAiCompatibleProvider : AIProvider {
    override val protocol = ProviderProtocol.OPENAI_COMPATIBLE
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun predict(config: ProviderConfig, request: PredictionRequest) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(config.baseUrl.startsWith("https://") ||
                config.baseUrl.startsWith("http://10.") ||
                config.baseUrl.startsWith("http://192.168.") ||
                config.baseUrl.startsWith("http://127.0.0.1")) {
                "Use HTTPS or a private/local endpoint"
            }
            require(config.apiKey.isNotBlank()) { "Provider API key is empty" }
            val prompt = """Analyse ${request.symbol}, timeframe ${request.timeframe}, horizon ${request.horizon}.
                |Data note: ${request.market.qualityNote}
                |Market features supplied: ${request.market.features.size}.
                |Recent numeric market features in chronological OHLC order: ${request.market.features.takeLast(120).joinToString(", ") { "%.8f".format(java.util.Locale.US, it) }}.
                |Treat the data-source and frequency note as authoritative. Do not reinterpret daily reference points as intraday candles.
                |Do not invent prices, candles, news, events or probabilities. If evidence is insufficient,
                |return neutral with low confidence. Return JSON only with direction (bullish/bearish/neutral),
                |score (0-100), confidence (0-1), and rationale.""".trimMargin()
            val body = JSONObject()
                .put("model", config.model)
                .put("temperature", 0)
                .put("max_tokens", 220)
                .put("messages", org.json.JSONArray()
                    .put(JSONObject().put("role", "system").put("content",
                        "You are a cautious market research assistant. Never fabricate live data or claim certainty."))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
                .toString().toRequestBody("application/json".toMediaType())
            val url = config.baseUrl.trimEnd('/') + "/chat/completions"
            val requestHttp = Request.Builder().url(url)
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(body).build()
            http.newCall(requestHttp).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                check(response.isSuccessful) {
                    "Provider ${config.id} returned HTTP ${response.code}: ${raw.take(180)}"
                }
                val content = JSONObject(raw).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
                val start = content.indexOf('{')
                val end = content.lastIndexOf('}')
                require(start >= 0 && end > start) { "Provider ${config.id} returned no JSON forecast" }
                val json = JSONObject(content.substring(start, end + 1))
                val direction = json.optString("direction", "neutral").lowercase()
                    .takeIf { it in setOf("bullish", "bearish", "neutral") } ?: "neutral"
                ProviderForecast(
                    providerId = config.id,
                    model = config.model,
                    direction = direction,
                    score = json.optDouble("score", 50.0).coerceIn(0.0, 100.0),
                    confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
                    rationale = json.optString("rationale", "No rationale supplied").take(600)
                )
            }
        }
}

interface AccuracyLedger {
    /** Empirical provider reliability in [0,1]. Must be based on matured out-of-sample outcomes. */
    fun reliability(providerId: String): Double
}

/** Neutral prior only. It is not historical calibration or a claim of provider accuracy. */
class NeutralAccuracyLedger : AccuracyLedger {
    override fun reliability(providerId: String) = 0.5
}

class EnsembleEngine(
    private val providers: List<AIProvider>,
    private val ledger: AccuracyLedger,
    private val maxConcurrency: Int = 4
) {
    init { require(maxConcurrency > 0) }

    suspend fun predict(request: PredictionRequest): EnsembleResult = coroutineScope {
        val configs = request.providers.filter { it.enabled && it.apiKey.isNotBlank() }
        require(configs.isNotEmpty()) { "Enable and configure at least one provider" }
        require(configs.map { it.id }.distinct().size == configs.size) { "Provider profile IDs must be unique" }

        val semaphore = Semaphore(maxConcurrency)
        val outcomes = configs.map { config ->
            async {
                val adapter = providers.firstOrNull { it.protocol == config.protocol }
                    ?: return@async null
                try {
                    semaphore.withPermit {
                        withTimeout(config.timeoutMillis.coerceIn(1_000L, 60_000L)) {
                            adapter.predict(config, request).also {
                                require(it.providerId == config.id) { "Provider returned a mismatched profile ID" }
                                require(it.direction in setOf("bullish", "bearish", "neutral")) { "Invalid forecast direction" }
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null // One provider's error must not discard successful peers.
                }
            }
        }.awaitAll().filterNotNull()

        require(outcomes.isNotEmpty()) { "All configured providers failed, timed out or lack an adapter" }

        // Never weight by a model's own confidence: self-reported confidence is not calibrated.
        // Reliability is neutral until a matured, out-of-sample ledger is integrated.
        val weighted = outcomes.map { forecast ->
            val weight = ledger.reliability(forecast.providerId).coerceIn(0.05, 1.0)
            val vote = when (forecast.direction) {
                "bullish" -> 1.0
                "bearish" -> -1.0
                else -> 0.0
            }
            Triple(forecast, weight, vote)
        }
        val totalWeight = weighted.sumOf { it.second }.coerceAtLeast(1e-9)
        val signedVote = weighted.sumOf { it.second * it.third } / totalWeight
        val direction = when {
            signedVote > 0.15 -> "bullish"
            signedVote < -0.15 -> "bearish"
            else -> "neutral"
        }
        val agreement = weighted.filter { (_, _, vote) ->
            when (direction) {
                "bullish" -> vote > 0
                "bearish" -> vote < 0
                else -> vote == 0.0
            }
        }.sumOf { it.second } / totalWeight
        val score = weighted.sumOf { it.second * it.first.score } / totalWeight
        val reasons = outcomes.joinToString(" | ") {
            "${it.providerId}/${it.model}: ${it.rationale}"
        }.take(900)

        EnsembleResult(
            direction = direction,
            score = score.coerceIn(0.0, 100.0),
            agreement = agreement.coerceIn(0.0, 1.0),
            contributors = outcomes.size,
            summary = "Reliability-weighted consensus from ${outcomes.size} successful model(s). $reasons",
            generatedAt = Instant.now().toString()
        )
    }
}
