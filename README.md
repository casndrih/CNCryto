# CNCryto

**AI-assisted multi-market analysis for Android**  
Kotlin · Jetpack Compose · Material 3 · Chaquopy Python 3.13 · NumPy · pandas · OpenAI-compatible model endpoints

[![Android CI](https://github.com/casndrih/CNCryto/actions/workflows/android-build.yml/badge.svg)](https://github.com/casndrih/CNCryto/actions/workflows/android-build.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

CNCryto is an Android application foundation for market research, model-assisted forecasts, signal workflows, risk analysis and (planned) backtesting and paper trading. The project is designed around a provider-neutral prediction interface so several AI models can be queried concurrently and their outputs combined.

> **Development status: functional analytics MVP, not a live trading system.** The app now bundles the supplied `forex_signal_system` package through Chaquopy, with signal parsing, risk validation, OHLCV CSV regime analysis, historical backtesting and a local SQLite signal journal. Market data is imported by the user; no live feed or broker execution is connected. See [implementation status](docs/IMPLEMENTATION.md).

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [AI ensemble](#ai-ensemble)
- [Requirements](#requirements)
- [Build and run](#build-and-run)
- [Configure model providers](#configure-model-providers)
- [Tests](#tests)
- [Security](#security)
- [Roadmap and known limitations](#roadmap-and-known-limitations)
- [Contributing](#contributing)
- [License](#license)
- [Risk disclosure](#risk-disclosure)

## Features

### Implemented in the current MVP

- Native Android application: Kotlin and Jetpack Compose with Material 3.
- Dark interface with lime accent palette.
- Android min SDK 24; compile/target SDK 35.
- Multiple configurable model profiles in the UI.
- Provider model discovery through authenticated OpenAI-compatible `GET /models`; returned model IDs are selectable rather than manually typed.
- Saved provider profiles automatically refresh their model catalog when the Providers screen is opened, with a manual refresh action available.
- Fully rounded text fields, buttons and model selectors; System, Light and Dark appearance modes with a true-black dark-mode canvas and dark bottom navigation.
- Release builds use R8 code shrinking, resource shrinking and core-library desugaring.
- Concurrent requests to enabled OpenAI-compatible chat-completions endpoints.
- Per-provider failure isolation: one failed provider does not discard successful forecasts.
- Reliability-weighted directional consensus engine with neutral prior.
- EncryptedSharedPreferences backed by Android Keystore for provider credentials.
- HTTP call timeouts and HTTPS-by-default endpoint validation (private/local HTTP endpoints are permitted for local models).
- Chaquopy Python 3.13 runtime with NumPy and pandas for the bundled analytics engine.
- XTrend Speed signal parsing from copied text or structured JSON.
- Direction-aware stop/target checks, reward-to-risk validation and fixed-fractional position sizing.
- Historical OHLCV CSV import, market-regime detection and event-driven backtesting.
- Local SQLite signal journal with history lookup.
- GitHub Actions Python regression tests, Android build, APK artifact upload and root APK publication.

### Planned capabilities

The original development specification also describes markets and watchlists, XTrend Speed signal parsing, market-regime detection, risk-based position sizing, a strategy builder, historical backtesting, paper trading, portfolio analytics, charts, news and economic-calendar context, prediction history and model calibration. These must be implemented and tested before they should be considered available.

## Architecture

The current codebase is intentionally small and modular. The intended production boundary is:

```text
Compose UI
   │
   ▼
ViewModel / UI state
   │
   ▼
Use cases
   │
   ├── Market-data repository ── Exchange / broker / licensed data APIs
   ├── Signal repository ─────── Python signal engine (when source is supplied)
   ├── Prediction use case
   │       ├── Provider adapter A ──┐
   │       ├── Provider adapter B ──┼─ Concurrent forecasts
   │       └── Local model adapter ─┘
   │                    │
   │                    ▼
   │             Ensemble / calibration
   │
   └── Persistence ───── Room / encrypted preferences
```

### Source layout

```text
CNCryto/
├── .github/workflows/       # Android CI and APK publication
├── app/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   └── java/com/cndrih/cncryto/
│       │       ├── MainActivity.kt
│       │       ├── ProviderVault.kt
│       │       └── ai/EnsembleEngine.kt
│       └── test/            # JVM unit tests
├── docs/
│   ├── IMPLEMENTATION.md
│   └── ai-ensemble.md
├── CNCryto-debug.apk        # Latest CI-published debug build
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## AI ensemble

Each configured profile supplies a provider label, base URL, model identifier, API key and enabled flag. Enabled profiles are submitted concurrently. The engine isolates individual provider errors and combines successful directional forecasts using provider reliability weights.

The current ledger uses a neutral prior. It is **not** a learned accuracy model. A model's self-reported confidence is not a calibrated probability of a profitable trade. The ensemble score is a consensus score, not a win probability.

A production calibration service must record each forecast with its input snapshot, provider/model version, symbol, timeframe, horizon and timestamp; wait until the forecast horizon has matured; calculate out-of-sample metrics (for example Brier score, log loss, directional accuracy and calibration); and shrink estimates toward a neutral prior when sample sizes are small. Never train or calibrate on future information.

See [AI ensemble design](docs/ai-ensemble.md).

## Requirements

- Android Studio with Android SDK Platform 35 installed.
- JDK 17.
- Python 3.13 on PATH (Chaquopy build and source preparation).
- Gradle 8.9 (the CI workflow provisions it automatically).
- 64-bit ARM Android device running Android 7.0 / API 24 or later (the packaged Python ABI is `arm64-v8a`).
- An API key for at least one compatible model endpoint to generate model forecasts.

## Build and run

### Android Studio

1. Clone this repository.
2. Open the repository root in Android Studio.
3. Allow Gradle sync to complete.
4. Select the `app` run configuration.
5. Run on an emulator or connected Android device.

### Command line

With Gradle 8.9 and JDK 17 installed:

```bash
gradle --no-daemon assembleDebug assembleRelease testDebugUnitTest
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug application ID has the `.debug` suffix, so it can be installed alongside a release build.

### CI build

Pushes and pull requests to `main` run debug and R8-minified release builds plus JVM unit tests. CI uploads both APK artifacts; on a push to `main`, it also commits the generated `CNCryto-debug.apk` to the repository root. The release artifact is unsigned and intended for shrinker/build verification, not direct distribution. Pull-request builds do not publish an APK into the branch.

- [Latest workflow runs](https://github.com/casndrih/CNCryto/actions)
- [Download latest debug APK](https://github.com/casndrih/CNCryto/blob/main/CNCryto-debug.apk)

The root APK is a **debug** build, not a signed Play Store release.

## Configure model providers

1. Install and open CNCryto.
2. Open the **Providers** tab.
3. Add one profile per model endpoint.
4. Enter the endpoint base URL, model ID and API key.
5. Enable the profiles you want included.
6. Save the profiles to encrypted local storage.
7. Return to Analysis and run an ensemble.

Model discovery and prediction both expect an OpenAI-compatible API: `GET /models` for listing models and `POST /chat/completions` for forecasts and a response containing `choices[0].message.content`. This is suitable for OpenAI and many compatible gateways (including common Groq, Together AI and Ollama-compatible endpoints). Azure deployments and native Anthropic/Gemini APIs may require dedicated adapters and are not claimed to work through the current adapter.

Example endpoint (OpenAI):

```text
https://api.openai.com/v1
```

Example local Ollama endpoint (only when reachable from the Android device):

```text
http://192.168.1.10:11434/v1
```

Use HTTPS for remote services. Do not paste real API keys into source files, issues, screenshots or commits. Keys are stored locally using Android encrypted preferences. The app does not proxy requests through a CNCryto backend.

## Python forex engine

Open the **Engine** tab to check the bundled Python runtime, paste XTrend Speed signals, validate a setup and calculate a risk-based position size, import OHLCV CSV data, inspect the current market regime, run a historical backtest and save/load the local signal journal. CSV files must contain `timestamp,open,high,low,close`; `volume` is optional. Use ISO-8601 timestamps, preferably UTC. The regime detector requires at least 60 candles.

The original package is retained as `forex_signal_system.zip`. The Gradle `preBuild` task extracts its Python modules and applies documented Android compatibility and risk-safety fixes into an ignored generated source directory. Chaquopy bundles Python 3.13, NumPy 1.26.2 and pandas 2.1.3. See [Python engine integration](docs/python-engine.md).

The app currently uses user-imported CSV data only. The Python package's Yahoo adapter is not enabled in the Android build because `yfinance` and its dependency chain are not bundled. No live price feed is implied by the analysis UI.

## Tests

Run JVM unit tests:

```bash
gradle --no-daemon testDebugUnitTest
```

Build and test together:

```bash
gradle --no-daemon assembleDebug testDebugUnitTest
```

The CI workflow runs eight Python engine regression tests plus JVM tests and debug/release Android builds on every push and pull request. These tests cover the bridge, parser, risk gates, CSV regime analysis, backtesting and local journal on the host Python runtime; they do not establish live-market prediction accuracy, Android-device runtime compatibility, broker correctness or trading safety.

## Security

- Never commit API keys, passwords, signing keys or account tokens.
- Keep remote API traffic on HTTPS.
- Use a separate restricted API key for development.
- Do not enable withdrawal permissions on exchange API keys.
- Use read-only API permissions until a separately reviewed execution system exists.
- Report suspected vulnerabilities privately; see [SECURITY.md](SECURITY.md).

## Roadmap and known limitations

The specification in the original project brief is broader than the current checked-in code. The following are not yet delivered:

- The supplied Python package is integrated through Chaquopy. Its Yahoo Finance adapter is not enabled in Android; current market data enters through user-imported OHLCV CSV files.
- No automatic live market-data source is wired into the AI analysis prompt. Without verified candles, forecasts must not be treated as market signals.
- Provider reliability is a neutral placeholder, not historical model accuracy calibration.
- Only OpenAI-compatible chat-completions protocol is implemented.
- Room persistence, Hilt dependency injection, full MVVM/use-case layers, signal journal and prediction history are not yet implemented.
- No strategy editor, charting, economic calendar, news/RAG, portfolio sync or paper-trading engine is currently shipped. The historical backtest engine is available for imported CSV data.
- No broker integration or automatic order execution is included.
- No signed release APK or Play Store distribution configuration is supplied.

See [implementation status](docs/IMPLEMENTATION.md) for the delivery checklist and prerequisites.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Keep changes small, add tests for domain logic, and update the documentation when behaviour or configuration changes.

## License

CNCryto is distributed under the Apache License 2.0. See [LICENSE](LICENSE).

## Risk disclosure

Trading leveraged financial products, foreign exchange, securities and crypto-assets involves substantial risk of loss and may not be suitable for all users. AI outputs can be inaccurate, stale, biased or fabricated. They are probabilistic research aids, not guarantees, personalized financial advice or instructions to trade. Past performance does not guarantee future results. Verify all market data independently and consult a qualified professional where appropriate. CNCryto's current MVP does not execute orders.
