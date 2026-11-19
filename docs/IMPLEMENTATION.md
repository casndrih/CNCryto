# Implementation status

This file distinguishes delivered code from planned capabilities. A feature is not complete merely because it appears in the original project brief.

## Implemented

| Area | Current implementation |
|---|---|
| Android UI | Compose destinations: Analysis, Providers and Engine |
| Appearance | Device/system theme plus manual Light and Dark; black dark canvas and dark bottom navigation |
| Provider profiles | Multiple enabled/disabled profiles; encrypted local save/load |
| Provider discovery | Authenticated OpenAI-compatible GET /models and selectable model dropdown |
| AI inference | Concurrent OpenAI-compatible POST /chat/completions calls with failure isolation |
| Consensus | Reliability-weighted direction/score with neutral reliability prior |
| Python runtime | Chaquopy 17.2, AGP 8.9.2, Gradle 8.11.1, Python 3.13, NumPy 1.26.2 and pandas 2.1.3 |
| Python source | Original forex_signal_system.zip is versioned; Gradle extracts it and applies Android compatibility fixes |
| Signal parsing | XTrend Speed copied text and structured JSON; structured JSON price levels are retained |
| Risk validation | Directional stop/target validation, minimum reward-to-risk, account checks and position sizing |
| Risk safeguards | Position sizing refuses to round a risk budget upward to the broker minimum lot |
| Regime analysis | Market regime and indicator summary from imported OHLCV CSV |
| Backtesting | Event-driven backtest using imported OHLCV CSV and supplied signal levels |
| Signal journal | SQLite persistence through the package tracker |
| Automated tests | Python regression suite plus existing JVM unit tests |
| Build pipeline | Debug and R8-minified release builds; debug APK published at repository root |

## Python integration design

The source archive remains the source of truth. scripts/prepare_python_engine.py extracts only package .py files into app/src/main/python/forex_signal_system/ and applies a small, repeatable set of compatibility and correctness fixes. The generated package directory is ignored by Git so the archive is not duplicated in source control.

The Gradle preBuild task invokes the preparation script. A local Android build therefore requires Python 3.13 on PATH, JDK 17 and the Android SDK. CI installs Python 3.13 and runs the same preparation script before tests and Android builds.

Android dependencies are pinned in app/build.gradle.kts:

- Chaquopy Gradle plugin 17.2.0
- Python 3.13
- NumPy 1.26.2
- pandas 2.1.3
- arm64-v8a Android ABI

The ABI choice keeps the native scientific stack smaller and targets current 64-bit ARM phones. x86 emulator and 32-bit ARM APKs are not produced by this configuration.

### Kotlin/Python boundary

PythonForexEngine initializes Chaquopy once and runs every Python call on Dispatchers.IO. cncryto_bridge.py exposes a JSON-in/JSON-out API so Python exceptions are returned as explicit errors instead of escaping into Compose.

Supported bridge operations:

- health
- parse_signals
- validate_signal
- analyze_regime
- analyze_csv
- backtest
- backtest_csv
- track_signals
- list_signals
- performance_stats

### CSV contract

The Engine screen uses Android's document picker. CSV columns are:

- Required: timestamp, open, high, low, close
- Optional: volume (defaults to zero)

Timestamps are parsed as UTC. Use ISO-8601 timestamps and chronological candles. The regime engine requires at least 60 bars with the current default indicator configuration. The backtest signal timestamp must fall inside the CSV range and have a subsequent candle available for simulated entry.

The app does not silently fetch market prices. The Yahoo Finance adapter in the original package is not bundled as an Android data source because yfinance and its transitive dependencies are not part of the Android pip requirements. A user-imported CSV is the current supported data path.

## Correctness fixes applied during integration

The preparation script applies these fixes to the extracted source without modifying the original archive:

1. Adds an explicit fixed slippage setting to the backtest configuration.
2. Initializes and updates the backtest equity curve, and guards empty/zero-variance Sharpe, Sortino and Calmar calculations.
3. Passes the underlying signals, not internal backtest trade wrappers, to correlated-exposure validation.
4. Uses the configured slippage value and makes swap-rate handling safe when a symbol specification is absent.
5. Extracts structured entry, stop-loss and take-profit values from JSON signal payloads.
6. Rejects directionally invalid protection levels (BUY: stop < entry < target; SELL: target < entry < stop).
7. Prevents position sizing from increasing a calculated size to the broker minimum when that would exceed the requested risk budget.
8. Adds a positive-equity guard to account risk checks.
9. Allows the rolling volatility percentile to warm up before the full 200-bar lookback is available.
10. Uses CSV caching and timezone-safe pandas index construction rather than requiring Parquet/pyarrow in the Android runtime.

These are code-level safety and compatibility corrections, not a claim that the trading strategy is profitable or validated against live execution.

## Still not implemented

- [ ] Live or streaming forex, crypto, equity, index and commodity market-data adapters.
- [ ] Automatic refresh, source provenance, stale-data policy and candle integrity validation for a live feed.
- [ ] Native Anthropic, Gemini and Azure OpenAI model adapters; current provider protocol is OpenAI-compatible only.
- [ ] Empirical provider calibration based on matured out-of-sample outcomes.
- [ ] Strategy editor, optimization, walk-forward testing and Monte Carlo analysis.
- [ ] Charting, economic calendar, news/RAG and portfolio analytics.
- [ ] Broker account connection, paper-trading simulator UI and order execution.
- [ ] Room-based app-wide persistence, migrations and full MVVM/use-case architecture.
- [ ] Signed release keystore, Play Store bundle and distribution pipeline.
- [ ] Android instrumentation tests and physical-device runtime/performance verification.

## Accuracy and latency

The AI ensemble is an orchestration feature, not an accuracy guarantee. The neutral ledger is not trained on historical outcomes. Model-reported confidence is not a calibrated probability.

A production calibration system must store each forecast with its exact market snapshot, provider/model version, symbol, timeframe, horizon and timestamp; evaluate only after the forecast horizon matures; measure directional accuracy, Brier score, log loss, calibration, coverage, latency and failure rate; and shrink small-sample estimates toward a neutral prior. Correlated models should not be counted as independent evidence.

For speed, the app reuses the same supplied data snapshot, runs enabled providers concurrently with bounded timeouts, isolates provider failures and keeps Python analytics off the UI thread. More providers do not automatically improve accuracy.

## Trading and compliance boundary

CNCryto does not connect to a broker or place orders. Backtest and risk outputs are analytical estimates based on supplied prices and configured assumptions. They are not financial advice, guaranteed results or execution instructions. Any future broker integration requires a separate security and safety review, explicit user authorization, sandbox testing, idempotency, audit logs and hard risk limits.
