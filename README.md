# CNCryto

**AI-assisted multi-market analysis for Android**  
Kotlin · Jetpack Compose · Material 3 · Coroutines · OpenAI-compatible model endpoints

[![Android CI](https://github.com/casndrih/CNCryto/actions/workflows/android-build.yml/badge.svg)](https://github.com/casndrih/CNCryto/actions/workflows/android-build.yml)
[![License: Apache-2.0](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

CNCryto is an Android application foundation for market research, model-assisted forecasts, signal workflows, risk analysis and (planned) backtesting and paper trading. The project is designed around a provider-neutral prediction interface so several AI models can be queried concurrently and their outputs combined.

> **Development status: early MVP, not a live trading system.** The checked-in app currently provides a basic Compose analysis screen, configurable OpenAI-compatible provider profiles, encrypted local credential storage, and a concurrent weighted-consensus engine. It does not yet contain the original `forex_signal_system` Python package, live multi-asset market-data adapters, persistent prediction calibration, a broker connection, or order execution. See [implementation status](docs/IMPLEMENTATION.md).

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
- Concurrent requests to enabled OpenAI-compatible chat-completions endpoints.
- Per-provider failure isolation: one failed provider does not discard successful forecasts.
- Reliability-weighted directional consensus engine with neutral prior.
- EncryptedSharedPreferences backed by Android Keystore for provider credentials.
- HTTP call timeouts and HTTPS-by-default endpoint validation (private/local HTTP endpoints are permitted for local models).
- GitHub Actions build, unit-test, APK artifact upload and root APK publication.

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
- Gradle 8.9 (the CI workflow provisions it automatically).
- Android device or emulator running Android 7.0 / API 24 or later.
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
gradle --no-daemon assembleDebug
gradle --no-daemon testDebugUnitTest
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug application ID has the `.debug` suffix, so it can be installed alongside a release build.

### CI build

Pushes and pull requests to `main` run the Android build workflow. On a push to `main`, CI uploads the APK artifact and commits the generated `CNCryto-debug.apk` to the repository root. Pull-request builds do not publish an APK into the branch.

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

The current adapter expects an OpenAI-compatible `POST /chat/completions` endpoint and a response containing `choices[0].message.content`. This is suitable for OpenAI and many compatible gateways (including common Groq, Together AI and Ollama-compatible endpoints). Azure deployments and native Anthropic/Gemini APIs may require dedicated adapters and are not claimed to work through the current adapter.

Example endpoint (OpenAI):

```text
https://api.openai.com/v1
```

Example local Ollama endpoint (only when reachable from the Android device):

```text
http://192.168.1.10:11434/v1
```

Use HTTPS for remote services. Do not paste real API keys into source files, issues, screenshots or commits. Keys are stored locally using Android encrypted preferences. The app does not proxy requests through a CNCryto backend.

## Tests

Run JVM unit tests:

```bash
gradle --no-daemon testDebugUnitTest
```

Build and test together:

```bash
gradle --no-daemon assembleDebug testDebugUnitTest
```

The CI workflow runs these commands on every push and pull request. Current tests cover only the existing JVM-level code; they do not establish market prediction accuracy, Python bridge compatibility, broker correctness or live trading safety.

## Security

- Never commit API keys, passwords, signing keys or account tokens.
- Keep remote API traffic on HTTPS.
- Use a separate restricted API key for development.
- Do not enable withdrawal permissions on exchange API keys.
- Use read-only API permissions until a separately reviewed execution system exists.
- Report suspected vulnerabilities privately; see [SECURITY.md](SECURITY.md).

## Roadmap and known limitations

The specification in the original project brief is broader than the current checked-in code. The following are not yet delivered:

- The referenced `forex_signal_system` Python source package was not available in this repository or the uploaded project files. Chaquopy is therefore not configured and no Python integration is claimed.
- No live market-data source is wired into the analysis prompt. Without verified candles, forecasts must not be treated as market signals.
- Provider reliability is a neutral placeholder, not historical model accuracy calibration.
- Only OpenAI-compatible chat-completions protocol is implemented.
- Room persistence, Hilt dependency injection, full MVVM/use-case layers, signal journal and prediction history are not yet implemented.
- No strategy editor, charting, economic calendar, news/RAG, backtest engine, portfolio sync or paper-trading engine is currently shipped.
- No broker integration or automatic order execution is included.
- No signed release APK or Play Store distribution configuration is supplied.

See [implementation status](docs/IMPLEMENTATION.md) for the delivery checklist and prerequisites.

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request. Keep changes small, add tests for domain logic, and update the documentation when behaviour or configuration changes.

## License

CNCryto is distributed under the Apache License 2.0. See [LICENSE](LICENSE).

## Risk disclosure

Trading leveraged financial products, foreign exchange, securities and crypto-assets involves substantial risk of loss and may not be suitable for all users. AI outputs can be inaccurate, stale, biased or fabricated. They are probabilistic research aids, not guarantees, personalized financial advice or instructions to trade. Past performance does not guarantee future results. Verify all market data independently and consult a qualified professional where appropriate. CNCryto's current MVP does not execute orders.
