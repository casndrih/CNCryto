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


## Bundled Python engine bridge

The Engine destination calls the Python package through a JSON-only Kotlin boundary. Every request is a JSON object with an operation name; the bridge always returns either { "ok": true, "data": ... } or { "ok": false, "error": "..." }.

Supported operations:

| Operation | Purpose | Required input |
|---|---|---|
| health | Runtime and dependency versions | None |
| parse_signals | Parse XTrend Speed text or JSON | text |
| validate_signal | Validate direction, stop/target, reward-to-risk and position size | signal; optional account and open_positions |
| analyze_regime | Regime analysis for JSON candle arrays | price_data keyed by symbol |
| analyze_csv | Regime analysis for imported CSV | symbol, csv_text |
| backtest | Run event-driven backtest from JSON bars | signals, price_data |
| backtest_csv | Run backtest from imported CSV | symbol, csv_text, signals |
| track_signals | Store signals in local SQLite journal | signals |
| list_signals | Read journal entries | optional symbol, status, limit |
| performance_stats | Read journal performance summary | optional symbol |

### Structured signal

A signal object accepts symbol, direction, strength, timestamp, entry_price, stop_loss and take_profit. Price aliases entry, sl and tp are also accepted. Direction must be Buy or Sell. For BUY, stop-loss must be below entry and take-profit above entry. For SELL, take-profit must be below entry and stop-loss above entry.

### CSV market data

CSV must contain timestamp, open, high, low and close. Volume is optional. ISO-8601 timestamps are recommended and interpreted as UTC. At least 60 rows are needed for the current default regime configuration. For backtesting, the signal timestamp must be within the imported data range and must have a later candle for simulated entry.

### Persistence

The signal tracker uses SQLite in the app's private files area under HOME/cncryto/signals.db. The Android UI does not request broad storage permissions; it uses the system document picker to read a CSV file.

### Runtime limitations

The Android package uses Python 3.13, NumPy 1.26.2 and pandas 2.1.3 through Chaquopy. The Android build currently packages arm64-v8a only. CSV import is the supported market-data path; the package's Yahoo Finance adapter is not enabled because yfinance is not included in the Android dependency set. No broker API or order execution operation exists.
