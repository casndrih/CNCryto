# CNCryto implementation notes

## Multi-provider design
Provider requests execute concurrently with a bounded 15-second HTTP call timeout. Failed or malformed provider responses are isolated. Successful forecasts are combined using reliability x model-confidence weights. The current reliability ledger is deliberately a neutral prior; it is not yet learned from historical outcomes. Do not call the consensus score a win probability.

## Accuracy calibration roadmap
Persist forecast snapshots and matured outcomes. Evaluate rolling Brier score, log loss, directional accuracy, coverage and calibration by symbol, timeframe and market regime. Shrink estimates toward a neutral prior until adequate sample size; only update weights from out-of-sample results after fees/slippage. Confidence reported by an LLM must not be treated as calibrated probability.

## Scope and limitations
This is a buildable Android foundation, not the complete trading platform in the supplied specification. It currently has one provider profile in the UI and an ensemble engine capable of multiple configured forecasts; provider profile management, market feeds, broker adapters, charts, Room persistence, risk engine, historical calibration, Python bridge and automated tests are not yet complete. No trades are placed.

The referenced forex_signal_system workspace directory was not included in the repository or uploaded source. The heavy PyTorch/Transformers/TA-Lib Chaquopy bundle was intentionally not added because Android wheel/ABI support and package size require validating the actual source and target devices first.
