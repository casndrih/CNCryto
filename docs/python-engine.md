# Python forex engine on Android

CNCryto bundles the supplied forex_signal_system Python package using Chaquopy. The original archive remains at the repository root as forex_signal_system.zip.

## Build integration

- Android Gradle Plugin: 8.9.2
Gradle: 8.11.1
Chaquopy Gradle plugin: 17.2.0
- Embedded Python: 3.13
- NumPy: 1.26.2
- pandas: 2.1.3
- Android ABI: arm64-v8a
- Minimum Android API: 24

The app module's preBuild task invokes scripts/prepare_python_engine.py. It extracts Python source files from the archive into app/src/main/python/forex_signal_system and applies small Android/runtime and correctness patches. That generated directory is ignored by Git; do not edit it directly. Make source-level changes in the preparation script or the original package archive, then rerun the preparation script.

For a local build, install Python 3.13 and ensure the python command resolves to it, then run:

    python scripts/prepare_python_engine.py --source forex_signal_system.zip --dest app/src/main/python
    python -m unittest discover -s tests/python -v
    gradle --no-daemon assembleDebug assembleRelease testDebugUnitTest

## App workflows

The Engine destination exposes these capabilities:

1. Runtime check: reports the embedded Python, NumPy and pandas versions.
2. Signal parser: accepts copied XTrend Speed text or structured JSON.
3. Signal journal: saves parsed signals to SQLite and reloads them.
4. Risk validation: checks signal direction, protective levels, reward-to-risk and account limits, then calculates a position size.
5. Market regime: imports OHLCV CSV and calculates the current regime and indicator summary.
6. Historical backtest: runs the supplied signal against imported OHLCV candles and returns trade and performance metrics.

## CSV format

Required header columns:

    timestamp,open,high,low,close

Optional column:

    volume

Use chronological data and ISO-8601 timestamps. The bridge interprets timestamps as UTC. The default regime configuration needs at least 60 candles. For backtesting, the signal timestamp must be inside the CSV time range and must have a subsequent candle for entry simulation.

## Bridge operations

The Kotlin class PythonForexEngine calls cncryto_bridge.handle with a JSON string. The Python bridge always returns a JSON envelope with ok and data, or ok and error. Supported operations are:

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

Python calls run on Dispatchers.IO. The interpreter is initialized once per process. The tracker database is stored in the app-private HOME/cncryto/signals.db path.

## Safety and known boundaries

- CSV import is the current Android market-data path. The app does not silently fetch live prices.
- The original package's Yahoo Finance adapter is not enabled in the Android dependency set because yfinance and its transitive dependencies are not bundled.
- The app does not connect to a broker and does not place orders.
- Backtests are simulations, not evidence of future profitability.
- Position sizing uses the package's symbol specifications and configured risk defaults. Confirm broker contract specifications before relying on a calculated size.
- The current app only packages arm64-v8a. 32-bit ARM and x86 emulator builds are not produced.
- The Python regression suite runs on the host in CI. Physical-device runtime, performance and market-data validation remain separate release gates.

## Integration fixes

The preparation script applies fixes to the extracted package for slippage configuration, equity-curve accounting and empty metrics, correct correlated-exposure input, safe swap handling, structured price-level parsing, directional stop/target validation, minimum-lot risk enforcement, positive account equity, rolling volatility warm-up and CSV/timezone-safe cache operations. The original ZIP is retained unchanged as the supplied source archive.
