# Implementation status

This file distinguishes code that exists from requirements that remain planned. A feature is not complete merely because it appears in the original specification or in the architecture diagram.

## Verified in the repository

| Area | Current state |
|---|---|
| Android build | Kotlin Android application builds through GitHub Actions |
| UI | Small Compose screen with Analysis and Providers destinations |
| Provider profiles | Multiple profiles can be added/edited/enabled in the current UI |
| Credentials | EncryptedSharedPreferences save operation exists |
| AI protocol | OpenAI-compatible `/chat/completions` only |
| Parallelism | Configured requests are launched concurrently |
| Failure handling | Individual provider failures are dropped; all-failed returns an error |
| Consensus | Direction and score use reliability-weighted aggregation |
| CI | Debug assemble, JVM tests, artifact upload and root APK publication |

## Not yet implemented

- [ ] Full five-tab app navigation (Markets, Signals, Strategies, Portfolio, Settings).
- [ ] Navigation drawer, deep links, notifications and accessibility review.
- [ ] ViewModel/use-case/repository architecture and dependency injection.
- [ ] Room entities, migrations, prediction history and signal/trade journal.
- [ ] Loading persisted provider profiles back into the UI (current vault only saves).
- [ ] Provider-specific API adapters for native Anthropic, Gemini and Azure OpenAI protocols.
- [ ] Market data feeds for forex, equities, indices, commodities and crypto.
- [ ] Staleness checks, market-session metadata, data provenance and input snapshot hashing.
- [ ] Forex signal parser and XTrend Speed integration.
- [ ] Market regime detection, indicators and feature attribution.
- [ ] Position sizing and portfolio exposure/correlation limits.
- [ ] Strategy builder, walk-forward/Monte Carlo backtesting and optimization.
- [ ] Paper trading, trade journal and portfolio/account integrations.
- [ ] Prediction calibration using matured out-of-sample results.
- [ ] Python bridge and Chaquopy integration.
- [ ] Instrumented UI tests, device tests, security audit and release signing.

## External source prerequisite: forex_signal_system

The project brief identifies a Python package at a workspace path belonging to another app's private storage. That directory is not present in this Git repository or in the supplied files. It must be exported and added to `python/forex_signal_system/` before it can be inspected, ported or called from Android. The listed module names and function signatures in the brief are not enough to prove the package's actual implementation or compatibility.

Do not add a placeholder package and describe it as the original source. Once supplied, inspect its imports, tests, data contracts, license and Python version requirements. Prefer pure-Python/Decimal-compatible modules for the Android bridge. Native wheels such as TA-Lib, SciPy, pandas, NumPy, scikit-learn and PyTorch require per-ABI Chaquopy validation and may make the APK too large or impossible to build. Add dependencies incrementally and test on every supported ABI.

## Prediction accuracy and speed

The present ensemble is an orchestration prototype. It does not establish an accuracy edge. A valid implementation should:

1. Fetch one timestamped market snapshot and share the same immutable snapshot with all providers.
2. Validate symbol, timeframe, horizon, data age, missing candles and price precision before calling models.
3. Use bounded parallel requests, a global deadline and per-provider timeouts.
4. Parse a strict response schema; reject malformed outputs, unsupported symbols and out-of-range values.
5. Separate model-reported confidence from empirically measured calibration.
6. Weight providers using only matured, out-of-sample results, with sample-size shrinkage and a neutral fallback.
7. Track directional accuracy, Brier score, log loss, calibration, coverage, latency and failure rate by market/timeframe/regime.
8. Avoid double-counting correlated models from the same provider family.
9. Return neutral/abstain when data quality, agreement or calibration is insufficient.
10. Never describe a consensus score as a probability of winning.

Latency optimization should reuse a single data snapshot, run independent providers concurrently, cap concurrency, cancel late requests and cache only with explicit freshness rules. More providers do not automatically produce more accurate predictions.

## Trading and compliance boundary

The current app does not connect to brokers and does not place orders. Broker adapters, account synchronization and order execution require separate permission handling, strong authentication, explicit per-order user confirmation, idempotency, audit records, risk limits, sandbox testing and jurisdiction-specific legal review. Never infer an order should be placed from an AI response alone.
