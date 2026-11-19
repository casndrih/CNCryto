# Changelog

## 1.0.0 - Initial Android foundation

- Added Kotlin/Jetpack Compose Android app.
- Added lime-accented dark Material 3 styling.
- Added configurable multiple OpenAI-compatible provider profiles.
- Added concurrent weighted-consensus prediction engine.
- Added encrypted local provider credential storage.
- Added GitHub Actions debug build, JVM test, artifact upload and repository-root APK publication.
- Documented current MVP limitations and external prerequisites.

This is an initial development version, not a production trading release.

## Unreleased

- Discover available models from OpenAI-compatible provider `/models` endpoints and select them from a dropdown.
- Refresh saved model catalogs automatically when opening Providers.
- Add fully rounded form controls and System/Light/Dark theme selection with a black dark-mode canvas and dark bottom navigation.
- Enable R8 code shrinking, resource shrinking and core-library desugaring for release builds.


## Unreleased — Python forex engine integration

- Bundled the supplied forex_signal_system source archive through Chaquopy 17.2 and Python 3.13.
- Added NumPy and pandas Android wheels and restricted the embedded native runtime to arm64-v8a.
- Added Kotlin/Python JSON bridge and an Engine screen for signal parsing, risk validation, CSV regime analysis, historical backtesting and SQLite signal history.
- Added repeatable source extraction and Android compatibility fixes for backtesting, risk sizing, signal price parsing and timezone-safe CSV handling.
- Added Python regression tests to GitHub Actions and expanded CI to build debug and minified release variants.
- Documented the CSV data contract, runtime dependencies and current market-data/broker limitations.
