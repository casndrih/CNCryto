# CNCryto architecture

## Current application boundary

The current repository is an Android application module. Compose renders the screens, the provider configuration is held in encrypted preferences, and the AI package contains the provider contract, HTTP adapter and ensemble calculation.

```text
MainActivity / Compose
      |
      +---- ProviderVault (Android Keystore encrypted preferences)
      |
      +---- PredictionRequest
                  |
             EnsembleEngine
                  |
           bounded concurrent calls
                  |
          AIProvider adapter registry
                  |
           validated ProviderForecasts
                  |
        reliability-weighted consensus
```

## Intended clean architecture

The original specification describes feature modules for markets, signals, strategies, portfolio, charting and AI predictions, plus shared core and UI modules. These feature modules are not yet present. The recommended migration path is to extract domain contracts first, then add data and feature modules without moving Android UI concerns into the domain layer.

## Threading and cancellation

Network calls execute on Dispatchers.IO. The ensemble caps concurrent provider calls and applies a per-profile timeout. Coroutine cancellation is propagated rather than being swallowed as an ordinary provider failure. Individual network/parse failures are isolated so healthy providers can still contribute.

## Security boundaries

Provider credentials are stored with EncryptedSharedPreferences backed by Android Keystore. The app calls configured provider endpoints directly from the device; there is no CNCryto proxy. Remote endpoints should use HTTPS. This implementation is an early MVP and has not undergone a formal security audit.

## Future Python integration

The original brief describes Chaquopy and a Python bridge. The source package itself was not supplied in this repository, so neither Chaquopy nor a fake substitute is included. When the actual package is provided, add a bridge behind Kotlin domain interfaces and validate Python modules on every target ABI.
