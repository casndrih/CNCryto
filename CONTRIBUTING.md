# Contributing

Thanks for helping improve CNCryto.

## Development setup

- JDK 17
- Android Studio with Android SDK Platform 35
- Gradle 8.9
- Python 3.13 on PATH for the Chaquopy source-preparation task
- Android API 24+ emulator/device

Open the repository in Android Studio or run:

```bash
python scripts/prepare_python_engine.py --source forex_signal_system.zip --dest app/src/main/python
python -m unittest discover -s tests/python -v
gradle --no-daemon assembleDebug assembleRelease testDebugUnitTest
```

## Pull request expectations

1. Describe the problem and the behaviour change.
2. Keep pull requests focused and reasonably small.
3. Add or update tests for domain logic and failure cases.
4. Do not commit API keys, credentials, signing keys, local.properties or generated build folders.
5. Update README/docs when build steps, configuration or feature status changes.
6. Include build/test results and note any manual device testing.
7. Do not describe planned or mocked functionality as complete.

## Python engine conventions

- Treat forex_signal_system.zip as the original source archive; do not edit the ignored generated extraction directory.
- Make compatibility changes in scripts/prepare_python_engine.py and add regression tests under tests/python.
- Keep the Android Python dependency set minimal and verify native wheels for the supported arm64-v8a ABI.
- Keep Python calls behind PythonForexEngine and execute them off the Compose main thread.
- Use UTC timestamps and Decimal for prices, money and risk calculations.

## Kotlin conventions

- Keep Android UI code separate from domain logic.
- Use suspend functions for asynchronous I/O and structured concurrency.
- Bound network timeouts and concurrency.
- Validate external API responses before they enter domain code.
- Use BigDecimal/Decimal for monetary calculations; avoid binary floating point for money.
- Keep prediction probabilities distinct from model-reported confidence and empirical calibration.
- Fail closed when market data is missing or stale.

## Financial safety

Do not introduce automatic live order execution without a separate design review, explicit user confirmation, audit trail, risk controls and sandbox tests. Never hardcode credentials or claim a strategy is profitable without reproducible, out-of-sample evidence.
