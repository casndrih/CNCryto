#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
import zipfile


def patch(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Cannot apply {label}: source fragment missing in {path}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def lines(*items: str) -> str:
    return "\n".join(items) + "\n"


def prepare(source_zip: Path, destination: Path) -> Path:
    package_dir = destination / "forex_signal_system"
    package_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(source_zip) as archive:
        for item in archive.infolist():
            name = item.filename
            if not name.startswith("forex_signal_system/") or not name.endswith(".py"):
                continue
            if "__pycache__" in Path(name).parts:
                continue
            relative = Path(*Path(name).parts[1:])
            target = package_dir / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(item))

    config = package_dir / "config.py"
    patch(config,
        lines('    slippage_model: Literal["none", "fixed", "volume_weighted"] = "fixed"'),
        lines('    slippage_model: Literal["none", "fixed", "volume_weighted"] = "fixed"',
              '    slippage_pips: Decimal = Decimal("0.5")'),
        "backtest slippage setting")

    backtest = package_dir / "backtest.py"
    patch(backtest,
        lines('        equity_final = self.equity_curve.iloc[-1] if len(self.equity_curve) > 0 else Decimal("0")',
              '        equity_peak = self.equity_curve.cummax()',
              '        drawdown = (self.equity_curve - equity_peak) / equity_peak',
              '        max_dd = drawdown.min()'),
        lines('        if len(self.equity_curve) > 0:',
              '            equity_peak = self.equity_curve.cummax()',
              '            drawdown = (self.equity_curve - equity_peak) / equity_peak.replace(0, np.nan)',
              '            max_dd = drawdown.min()',
              '            if pd.isna(max_dd):',
              '                max_dd = 0.0',
              '        else:',
              '            max_dd = 0.0'),
        "empty equity-curve handling")
    patch(backtest,
        lines('    def _calc_sharpe(self) -> float:',
              '        if len(self.daily_returns) < 2:',
              '            return 0.0',
              '        return float(self.daily_returns.mean() / self.daily_returns.std() * np.sqrt(252))'),
        lines('    def _calc_sharpe(self) -> float:',
              '        if len(self.daily_returns) < 2:',
              '            return 0.0',
              '        deviation = self.daily_returns.std()',
              '        if not np.isfinite(deviation) or deviation <= 0:',
              '            return 0.0',
              '        return float(self.daily_returns.mean() / deviation * np.sqrt(252))'),
        "zero-variance Sharpe handling")
    patch(backtest,
        lines("        if len(downside) == 0:",
              "            return float('inf')",
              "        return float(self.daily_returns.mean() / downside.std() * np.sqrt(252))"),
        lines("        if len(downside) < 2:",
              "            return 0.0",
              "        deviation = downside.std()",
              "        if not np.isfinite(deviation) or deviation <= 0:",
              "            return 0.0",
              "        return float(self.daily_returns.mean() / deviation * np.sqrt(252))"),
        "zero-variance Sortino handling")
    patch(backtest,
        lines('    def _calc_calmar(self, max_dd: Decimal) -> float:',
              '        if max_dd == 0:',
              "            return float('inf')",
              '        annual_return = float(self.equity_curve.iloc[-1] / self.equity_curve.iloc[0] - 1) * 252 / len(self.daily_returns)',
              '        return annual_return / float(abs(max_dd))'),
        lines('    def _calc_calmar(self, max_dd: Decimal) -> float:',
              '        if len(self.equity_curve) < 2 or len(self.daily_returns) == 0 or max_dd == 0:',
              '            return 0.0',
              '        initial = float(self.equity_curve.iloc[0])',
              '        if initial <= 0:',
              '            return 0.0',
              '        annual_return = float(self.equity_curve.iloc[-1] / initial - 1) * 252 / len(self.daily_returns)',
              '        return annual_return / float(abs(max_dd))'),
        "empty Calmar handling")
    patch(backtest,
        lines('        # Prepare data',
              '        self._prepare_data(price_data)',
              '',
              '        # Detect regimes for each symbol',
              '        self.regime_states = {}',
              '        for symbol, bars in price_data.items():',
              '            if len(bars) > 50:',
              '                self.regime_states[symbol] = self.regime_detector.detect(bars)'),
        lines('        # Prepare data and seed the equity curve with starting capital.',
              '        self._prepare_data(price_data)',
              '        first_timestamp = min(',
              '            (df.index[0] for df in self.price_dfs.values() if len(df) > 0),',
              '            default=pd.Timestamp.now(tz=timezone.utc),',
              '        )',
              '        self.equity_curve = [(first_timestamp, self.equity)]',
              '',
              '        # Detect regimes only after the indicator warm-up period.',
              '        self.regime_states = {}',
              '        minimum_regime_bars = max(',
              '            self.regime_detector.config.adx_period,',
              '            self.regime_detector.config.ma_slow_period,',
              '            self.regime_detector.config.atr_period,',
              '        ) + 10',
              '        for symbol, bars in price_data.items():',
              '            if len(bars) >= minimum_regime_bars:',
              '                self.regime_states[symbol] = self.regime_detector.detect(bars)'),
        "equity initialization and regime warm-up")
    patch(backtest,
        "signal_with_entry, account, list(self.open_trades.values())",
        "signal_with_entry, account, [trade.signal for trade in self.open_trades.values()]",
        "correlated exposure signal mapping")
    patch(backtest,
        'slippage_pips = Decimal(str(self.config.backtest.__dict__.get("slippage_pips", 0.5)))',
        "slippage_pips = self.config.slippage_pips",
        "slippage setting lookup")
    patch(backtest,
        'swap_rate = spec.swap_long if trade.signal.is_buy else spec.swap_short if spec else Decimal("0")',
        'swap_rate = (spec.swap_long if trade.signal.is_buy else spec.swap_short) if spec else Decimal("0")',
        "missing symbol specification swap handling")
    patch(backtest,
        lines('        self.equity += net_pnl',
              '        self.balance += net_pnl',
              '        date_key = str(exit_time.date())'),
        lines('        self.equity += net_pnl',
              '        self.balance += net_pnl',
              '        self.equity_curve.append((exit_time, self.equity))',
              '        date_key = str(exit_time.date())'),
        "equity curve trade updates")

    parser = package_dir / "signal_parser.py"
    patch(parser,
        lines('        return ForexSignal(',
              '            symbol=symbol,',
              '            direction=direction,',
              '            strength=strength,',
              '            timestamp=timestamp,',
              '            source="XTrendSpeed",',
              '            raw_data=item',
              '        )'),
        lines('        def price_from(*keys):',
              '            for key in keys:',
              '                value = item.get(key)',
              '                if value not in (None, ""):',
              '                    try:',
              '                        parsed = Decimal(str(value).replace(",", "").strip())',
              '                        if parsed <= 0:',
              '                            raise ValueError(f"{key} must be positive")',
              '                        return PriceLevel(parsed, symbol)',
              '                    except Exception as exc:',
              '                        logger.warning("Ignoring invalid %s for %s: %s", key, symbol, exc)',
              '                        return None',
              '            return None',
              '',
              '        return ForexSignal(',
              '            symbol=symbol,',
              '            direction=direction,',
              '            strength=strength,',
              '            timestamp=timestamp,',
              '            source="XTrendSpeed",',
              '            entry_price=price_from("entry_price", "entryPrice", "entry", "price"),',
              '            stop_loss=price_from("stop_loss", "stopLoss", "sl"),',
              '            take_profit=price_from("take_profit", "takeProfit", "tp"),',
              '            raw_data=item',
              '        )'),
        "structured signal price parsing")

    risk = package_dir / "risk.py"
    patch(risk,
        lines('        # Clamp to min/max',
              '        lots = max(spec.min_lot, min(spec.max_lot, lots))'),
        lines('        # Never round a position up to broker minimum: this can exceed the risk budget.',
              '        if lots < spec.min_lot:',
              '            raise ValueError(',
              '                f"Calculated size {lots} lots is below broker minimum {spec.min_lot}"',
              '            )',
              '        lots = min(spec.max_lot, lots)'),
        "minimum-lot risk guard")
    patch(risk,
        lines('        kelly_lots = self._round_lots(kelly_lots, spec)',
              '        kelly_lots = max(spec.min_lot, min(spec.max_lot, kelly_lots))'),
        lines('        kelly_lots = self._round_lots(kelly_lots, spec)',
              '        if kelly_lots < spec.min_lot:',
              '            raise ValueError("Kelly-adjusted size is below broker minimum lot size")',
              '        kelly_lots = min(spec.max_lot, kelly_lots)'),
        "Kelly minimum-lot risk guard")
    patch(risk,
        lines('        # 2. Risk:Reward check',
              '        rr = signal.risk_reward_ratio()'),
        lines('        # Enforce directionally valid protection levels before the R:R check.',
              '        entry = signal.entry_price.value',
              '        stop = signal.stop_loss.value',
              '        target = signal.take_profit.value',
              '        if signal.is_buy and not (stop < entry < target):',
              '            return False, "BUY requires stop-loss < entry < take-profit", None',
              '        if signal.is_sell and not (target < entry < stop):',
              '            return False, "SELL requires take-profit < entry < stop-loss", None',
              '        rr = signal.risk_reward_ratio()'),
        "directional stop-loss and take-profit validation")

    types = package_dir / "types.py"
    patch(types,
        '        if self.daily_pnl <= -abs(self.equity * max_daily_loss_pct):',
        '        if self.equity <= 0:\n            return False, "Account equity must be positive"\n        if self.daily_pnl <= -abs(self.equity * max_daily_loss_pct):',
        "positive account equity guard")

    regime = package_dir / "regime.py"
    patch(regime,
        'vol_percentile = atr_pct.rolling(self.config.volatility_lookback).rank(pct=True)',
        'vol_percentile = atr_pct.rolling(\n            self.config.volatility_lookback,\n            min_periods=min(50, self.config.volatility_lookback),\n        ).rank(pct=True)',
        "regime volatility warm-up")

    provider = package_dir / "data_provider.py"
    patch(provider, 'return self.cache_dir / f"{key}.parquet"', 'return self.cache_dir / f"{key}.csv"', "CSV cache filename")
    patch(provider, 'df = pd.read_parquet(cache_file)',
          'df = pd.read_csv(cache_file, index_col=0, parse_dates=True)\n                df.index = pd.to_datetime(df.index, utc=True)',
          "CSV cache reader")
    patch(provider, 'df.to_parquet(cache_file, compression="snappy")', 'df.to_csv(cache_file)', "CSV cache writer")
    patch(provider, 'pd.date_range(df.index[0], df.index[-1], freq=expected_freq, tz=timezone.utc)',
          'pd.date_range(df.index[0], df.index[-1], freq=expected_freq)', "timezone-safe gap filling")
    patch(provider, 'pd.DatetimeIndex([c.timestamp for c in ohlcv_list], tz=timezone.utc)',
          'pd.DatetimeIndex(pd.to_datetime([c.timestamp for c in ohlcv_list], utc=True))',
          "timezone-safe dataframe conversion")
    return package_dir


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--dest", type=Path, required=True)
    args = parser.parse_args()
    print("Prepared Python engine source at " + str(prepare(args.source.resolve(), args.dest.resolve())))


if __name__ == "__main__":
    main()
