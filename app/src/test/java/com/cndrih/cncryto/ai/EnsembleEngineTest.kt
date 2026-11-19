package com.cndrih.cncryto.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnsembleEngineTest {
    private class FakeProvider(
        private val forecasts: Map<String, ProviderForecast>,
        private val failures: Set<String> = emptySet()
    ) : AIProvider {
        override val protocol = ProviderProtocol.OPENAI_COMPATIBLE
        override suspend fun predict(config: ProviderConfig, request: PredictionRequest): ProviderForecast {
            if (config.id in failures) error("simulated provider outage")
            return forecasts.getValue(config.id)
        }
    }

    private class FixedLedger(private val values: Map<String, Double>) : AccuracyLedger {
        override fun reliability(providerId: String) = values[providerId] ?: 0.5
    }

    private fun config(id: String) =
        ProviderConfig(id, "https://example.test/v1", "test-model", "not-a-real-key")

    private fun forecast(id: String, direction: String, score: Double = 70.0) =
        ProviderForecast(id, "test-model", direction, score, 0.99, "test rationale")

    @Test fun reliabilityWeightCanResolveConflictingVotes() {
        runBlocking {
            val engine = EnsembleEngine(
                listOf(FakeProvider(mapOf(
                    "strong" to forecast("strong", "bullish", 80.0),
                    "weak" to forecast("weak", "bearish", 20.0)
                ))),
                FixedLedger(mapOf("strong" to 0.9, "weak" to 0.1))
            )
            val result = engine.predict(PredictionRequest(
                "EURUSD", "1h", "4h", listOf(config("strong"), config("weak")),
                MarketContext("test fixture", emptyList())
            ))
            assertEquals("bullish", result.direction)
            assertEquals(2, result.contributors)
            assertEquals(0.9, result.agreement, 0.0001)
        }
    }

    @Test fun oneProviderFailureDoesNotDiscardSuccessfulForecasts() {
        runBlocking {
            val engine = EnsembleEngine(
                listOf(FakeProvider(mapOf("healthy" to forecast("healthy", "bullish")), setOf("offline"))),
                NeutralAccuracyLedger()
            )
            val result = engine.predict(PredictionRequest(
                "BTCUSDT", "15m", "1h", listOf(config("healthy"), config("offline")),
                MarketContext("test fixture", emptyList())
            ))
            assertEquals(1, result.contributors)
            assertEquals("bullish", result.direction)
        }
    }

    @Test fun allProviderFailuresFailClosed() {
        val engine = EnsembleEngine(listOf(FakeProvider(emptyMap(), setOf("offline"))), NeutralAccuracyLedger())
        val request = PredictionRequest(
            "BTCUSDT", "15m", "1h", listOf(config("offline")),
            MarketContext("test fixture", emptyList())
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { engine.predict(request) }
        }
    }

    @Test fun disabledProvidersAreNotCalled() {
        val engine = EnsembleEngine(listOf(FakeProvider(emptyMap())), NeutralAccuracyLedger())
        val request = PredictionRequest(
            "BTCUSDT", "15m", "1h",
            listOf(config("disabled").copy(enabled = false)),
            MarketContext("test fixture", emptyList())
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { engine.predict(request) }
        }
    }

    @Test fun consensusScoreIsBounded() {
        runBlocking {
            val engine = EnsembleEngine(
                listOf(FakeProvider(mapOf("p1" to forecast("p1", "neutral", 140.0)))),
                NeutralAccuracyLedger()
            )
            val result = engine.predict(PredictionRequest(
                "XAUUSD", "1h", "4h", listOf(config("p1")), MarketContext("test", emptyList())
            ))
            assertEquals(100.0, result.score, 0.0)
            assertEquals("neutral", result.direction)
        }
    }
}
