# AI provider and prediction API

## Provider profile

A provider profile consists of:

- `id`: stable profile identifier.
- `baseUrl`: API root.
- `model`: provider model identifier.
- `apiKey`: credential stored locally in encrypted preferences.
- `enabled`: whether the profile participates in a run.

The current UI sends enabled profiles to the ensemble engine. The only implemented transport adapter currently expects an OpenAI-compatible chat-completions endpoint.

## Request safety

The current prompt explicitly states that no candle series is attached and instructs the model not to invent market facts. Until a market-data repository supplies a verified snapshot, output is not an actionable market forecast.

## Forecast response

The adapter expects a JSON object inside the model response:

```json
{
  "direction": "bullish | bearish | neutral",
  "score": 0,
  "confidence": 0.0,
  "rationale": "Concise explanation"
}
```

- `score` is a bounded model score from 0 to 100.
- `confidence` is a model-reported scalar from 0 to 1; it is not empirically calibrated.
- `direction` is normalized to bullish, bearish or neutral.
- Invalid or failed provider responses are excluded.

## Ensemble semantics

The engine uses a reliability ledger to weight votes and score aggregation. The current ledger returns a neutral constant for every provider. It is therefore an equal-prior baseline rather than learned historical weighting. A production ledger must be backed by matured, out-of-sample prediction outcomes and should shrink estimates toward a neutral prior until there is adequate sample size.

The aggregate score is not a probability of profit or a calibrated win probability.
