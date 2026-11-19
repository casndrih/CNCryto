"""JSON bridge between CNCryto's Android UI and the forex signal engine."""
from __future__ import annotations

import io
import json
import os
import uuid
from dataclasses import replace
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from forex_signal_system import __version__ as ENGINE_VERSION
from forex_signal_system.config import SystemConfig, DataConfig, set_config
from forex_signal_system.types import AccountState, Direction, ForexSignal, OHLCV, PriceLevel, SignalStrength
from forex_signal_system.signal_parser import XTrendSpeedParser
from forex_signal_system.risk import RiskManager
from forex_signal_system.regime import RegimeDetector
from forex_signal_system.backtest import BacktestEngine
from forex_signal_system.tracker import SignalTracker


def _runtime():
    home = Path(os.environ.get("HOME", str(Path.home()))) / "cncryto"
    home.mkdir(parents=True, exist_ok=True)
    (home / "cache").mkdir(exist_ok=True)
    (home / "results").mkdir(exist_ok=True)
    config = replace(
        SystemConfig(),
        data=replace(DataConfig(), provider="csv", cache_dir=home / "cache"),
        log_file=None,
        results_dir=home / "results",
    )
    set_config(config)
    return config, home


def _price(value, symbol):
    if value is None or value == "":
        return None
    return PriceLevel(Decimal(str(value)), symbol)


def _signal(data: dict[str, Any]) -> ForexSignal:
    symbol = str(data.get("symbol") or data.get("pair") or "").upper().replace("/", "").replace("-", "")
    if not symbol:
        raise ValueError("Signal symbol is required")
    direction = Direction.from_string(str(data.get("direction") or data.get("side") or ""))
    strength = SignalStrength.from_string(str(data.get("strength") or "General"))
    raw_time = data.get("timestamp") or data.get("time")
    timestamp = datetime.fromisoformat(str(raw_time).replace("Z", "+00:00")) if raw_time else datetime.now(timezone.utc)
    return ForexSignal(
        symbol=symbol,
        direction=direction,
        strength=strength,
        timestamp=timestamp,
        source=str(data.get("source") or "CNCryto"),
        entry_price=_price(data.get("entry_price", data.get("entry")), symbol),
        stop_loss=_price(data.get("stop_loss", data.get("sl")), symbol),
        take_profit=_price(data.get("take_profit", data.get("tp")), symbol),
        signal_id=str(data.get("signal_id") or uuid.uuid4().hex[:8]),
        raw_data=data.get("raw_data") or {},
    )


def _signal_dict(signal):
    rr = signal.risk_reward_ratio()
    return {
        "signal_id": signal.signal_id,
        "symbol": signal.symbol,
        "direction": signal.direction.value,
        "strength": signal.strength.value,
        "timestamp": signal.timestamp.isoformat(),
        "source": signal.source,
        "entry_price": str(signal.entry_price.value) if signal.entry_price else None,
        "stop_loss": str(signal.stop_loss.value) if signal.stop_loss else None,
        "take_profit": str(signal.take_profit.value) if signal.take_profit else None,
        "risk_reward_ratio": str(rr) if rr is not None else None,
    }


def _bars(symbol, rows):
    result = []
    for row in rows:
        stamp = datetime.fromisoformat(str(row["timestamp"]).replace("Z", "+00:00"))
        result.append(OHLCV(
            timestamp=stamp,
            open=PriceLevel(Decimal(str(row["open"])), symbol),
            high=PriceLevel(Decimal(str(row["high"])), symbol),
            low=PriceLevel(Decimal(str(row["low"])), symbol),
            close=PriceLevel(Decimal(str(row["close"])), symbol),
            volume=Decimal(str(row.get("volume", 0))),
        ))
    return sorted(result, key=lambda bar: bar.timestamp)


def _csv_rows(csv_text):
    frame = pd.read_csv(io.StringIO(csv_text))
    frame.columns = [str(column).strip().lower() for column in frame.columns]
    required = {"timestamp", "open", "high", "low", "close"}
    missing = required.difference(frame.columns)
    if missing:
        raise ValueError("CSV is missing columns: " + ", ".join(sorted(missing)))
    if "volume" not in frame.columns:
        frame["volume"] = 0
    rows = []
    for record in frame.to_dict(orient="records"):
        rows.append({
            "timestamp": pd.to_datetime(record["timestamp"], utc=True).isoformat(),
            "open": record["open"], "high": record["high"], "low": record["low"],
            "close": record["close"], "volume": record.get("volume", 0) or 0,
        })
    return rows


def _json_safe(value):
    if isinstance(value, Decimal):
        return str(value)
    if isinstance(value, (datetime, pd.Timestamp)):
        return value.isoformat()
    if isinstance(value, dict):
        return {str(key): _json_safe(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_safe(item) for item in value]
    if isinstance(value, np.integer):
        return int(value)
    if isinstance(value, np.floating):
        return float(value) if np.isfinite(value) else None
    if isinstance(value, float) and not np.isfinite(value):
        return None
    return value


def _run_backtest(config, signals, price_data, initial_equity, timeframe):
    backtest_config = replace(
        config.backtest,
        initial_equity=Decimal(str(initial_equity)),
        data_timeframe=str(timeframe),
    )
    engine = BacktestEngine(
        config=backtest_config,
        symbol_specs=config.symbols,
        risk_manager=RiskManager(config.risk, config.symbols),
        regime_detector=RegimeDetector(config.regime),
    )
    result = engine.run(signals, price_data)
    trades = [{
        "trade_id": trade.trade_id,
        "symbol": trade.signal.symbol,
        "direction": trade.signal.direction.value,
        "entry_price": str(trade.entry_price.value),
        "exit_price": str(trade.exit_price.value),
        "exit_time": trade.exit_time.isoformat(),
        "exit_reason": trade.exit_reason.value,
        "net_pnl": str(trade.net_pnl_currency),
        "r_multiple": str(trade.r_multiple),
    } for trade in result.trades]
    return {"summary": _json_safe(result.summary()), "trade_count": len(trades), "trades": trades}


def handle(request_json: str) -> str:
    """Handle one JSON request and always return a JSON response."""
    try:
        request = json.loads(request_json or "{}")
        operation = request.get("operation", "health")
        config, home = _runtime()

        if operation == "health":
            data = {
                "engine_version": ENGINE_VERSION,
                "python": __import__("sys").version.split()[0],
                "numpy": np.__version__,
                "pandas": pd.__version__,
                "features": ["signal_parser", "risk_manager", "regime_detector", "backtest", "sqlite_tracker"],
            }

        elif operation == "parse_signals":
            signals = XTrendSpeedParser().parse(request.get("text", ""))
            data = {"count": len(signals), "signals": [_signal_dict(signal) for signal in signals]}

        elif operation == "validate_signal":
            signal = _signal(request.get("signal") or {})
            account_data = request.get("account") or {}
            equity = Decimal(str(account_data.get("equity", "10000")))
            account = AccountState(
                equity=equity,
                balance=Decimal(str(account_data.get("balance", equity))),
                free_margin=Decimal(str(account_data.get("free_margin", equity))),
                used_margin=Decimal(str(account_data.get("used_margin", "0"))),
                margin_level=Decimal(str(account_data.get("margin_level", "1000"))),
                open_positions=int(account_data.get("open_positions", 0)),
                daily_pnl=Decimal(str(account_data.get("daily_pnl", "0"))),
                max_drawdown_pct=Decimal(str(account_data.get("max_drawdown_pct", "0"))),
                currency=str(account_data.get("currency", "USD")),
            )
            open_positions = [_signal(item) for item in request.get("open_positions", [])]
            approved, reason, size = RiskManager(config.risk, config.symbols).validate_signal(
                signal, account, open_positions
            )
            data = {
                "approved": approved,
                "reason": reason,
                "position_size": None if size is None else {
                    "lots": str(size.lots), "units": str(size.units),
                    "risk_amount": str(size.risk_amount), "risk_pct": str(size.risk_pct),
                    "margin_required": str(size.margin_required), "pip_value": str(size.pip_value),
                },
            }

        elif operation == "analyze_regime":
            results = {}
            for symbol, rows in (request.get("price_data") or {}).items():
                bars = _bars(symbol, rows)
                minimum = max(config.regime.adx_period, config.regime.ma_slow_period, config.regime.atr_period) + 10
                if len(bars) < minimum:
                    results[symbol] = {"error": "At least " + str(minimum) + " OHLCV bars are required", "bars": len(bars)}
                else:
                    results[symbol] = RegimeDetector(config.regime).current_regime(bars).to_dict()
            data = {"regimes": results}

        elif operation in ("analyze_csv", "backtest_csv"):
            symbol = str(request.get("symbol", "")).upper().replace("/", "").replace("-", "")
            if not symbol:
                raise ValueError("CSV symbol is required")
            bars = _bars(symbol, _csv_rows(str(request.get("csv_text", ""))))
            minimum = max(config.regime.adx_period, config.regime.ma_slow_period, config.regime.atr_period) + 10
            if operation == "analyze_csv":
                if len(bars) < minimum:
                    raise ValueError("At least " + str(minimum) + " OHLCV rows are required")
                data = {
                    "symbol": symbol, "bars": len(bars),
                    "regime": RegimeDetector(config.regime).current_regime(bars).to_dict(),
                }
            else:
                signals = [_signal(item) for item in request.get("signals", [])]
                if not signals:
                    raise ValueError("Backtest requires at least one signal")
                data = {"symbol": symbol, "bars": len(bars), **_run_backtest(
                    config, signals, {symbol: bars},
                    request.get("initial_equity", "10000"), request.get("timeframe", "M15")
                )}

        elif operation == "backtest":
            signals = [_signal(item) for item in request.get("signals", [])]
            price_data = {symbol: _bars(symbol, rows) for symbol, rows in (request.get("price_data") or {}).items()}
            if not signals or not price_data:
                raise ValueError("Backtest requires signals and OHLCV price data")
            data = _run_backtest(
                config, signals, price_data,
                request.get("initial_equity", "10000"), request.get("timeframe", "M15")
            )

        elif operation == "track_signals":
            signals = [_signal(item) for item in request.get("signals", [])]
            tracker = SignalTracker(home / "signals.db")
            ids = [tracker.track_signal(signal, str(request.get("notes", ""))) for signal in signals]
            data = {"tracked": len(ids), "signal_ids": ids}

        elif operation == "list_signals":
            tracker = SignalTracker(home / "signals.db")
            signals = tracker.get_signals(
                symbol=request.get("symbol"), status=request.get("status"),
                limit=max(1, min(int(request.get("limit", 100)), 1000)),
            )
            data = {"count": len(signals), "signals": [
                {**_signal_dict(item.signal), "status": item.status, "notes": item.notes,
                 "tracked_at": item.tracked_at.isoformat()} for item in signals
            ]}

        elif operation == "performance_stats":
            data = SignalTracker(home / "signals.db").get_performance_stats(symbol=request.get("symbol"))

        else:
            raise ValueError("Unsupported operation: " + str(operation))

        return json.dumps({"ok": True, "data": _json_safe(data)}, allow_nan=False)
    except Exception as exc:
        return json.dumps({"ok": False, "error": str(exc)}, allow_nan=False)
