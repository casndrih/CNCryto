import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "python"))
import cncryto_bridge


class ForexEngineBridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.env = patch.dict(os.environ, {"HOME": self.temp_dir.name})
        self.env.start()

    def tearDown(self):
        self.env.stop()
        self.temp_dir.cleanup()

    def call(self, operation, **kwargs):
        response = json.loads(cncryto_bridge.handle(json.dumps({"operation": operation, **kwargs})))
        self.assertTrue(response["ok"], response.get("error"))
        return response["data"]

    @staticmethod
    def sample_bars(count=100):
        start = datetime(2026, 1, 1, tzinfo=timezone.utc)
        price = 1.0800
        rows = []
        for index in range(count):
            open_price = price
            close = price + 0.00015
            rows.append({
                "timestamp": (start + timedelta(minutes=15 * index)).isoformat(),
                "open": open_price, "high": close + 0.0001,
                "low": open_price - 0.0001, "close": close, "volume": 1000,
            })
            price = close
        return rows

    @staticmethod
    def as_csv(rows):
        lines = ["timestamp,open,high,low,close,volume"]
        lines.extend(
            f'{row["timestamp"]},{row["open"]},{row["high"]},{row["low"]},{row["close"]},{row["volume"]}'
            for row in rows
        )
        return "\n".join(lines)

    def test_health_reports_bundled_runtime(self):
        result = self.call("health")
        self.assertEqual(result["engine_version"], "1.0.0")
        self.assertIn("pandas", result)
        self.assertIn("numpy", result)

    def test_parse_xtrend_speed_text(self):
        result = self.call("parse_signals", text="EURUSD\nSell\n01:08\nStrong\nGBPUSD\nBuy\n02:00\nGeneral")
        self.assertEqual(result["count"], 2)
        self.assertEqual(result["signals"][0]["symbol"], "EURUSD")
        self.assertEqual(result["signals"][0]["direction"], "Sell")

    def test_parse_structured_signal_price_levels(self):
        source = [{
            "symbol": "EUR/USD", "direction": "Buy", "strength": "Strong",
            "timestamp": "2026-01-01T00:00:00Z",
            "entry_price": "1.0850", "stop_loss": "1.0800", "take_profit": "1.0950",
        }]
        result = self.call("parse_signals", text=json.dumps(source))
        signal = result["signals"][0]
        self.assertEqual(signal["symbol"], "EURUSD")
        self.assertEqual(signal["risk_reward_ratio"], "2.00")

    def test_risk_validator_approves_valid_buy_and_rejects_wrong_stop_side(self):
        valid = {
            "symbol": "EURUSD", "direction": "Buy", "entry_price": "1.0850",
            "stop_loss": "1.0800", "take_profit": "1.0950",
        }
        accepted = self.call("validate_signal", signal=valid)
        self.assertTrue(accepted["approved"])
        self.assertGreater(float(accepted["position_size"]["lots"]), 0)
        rejected = self.call("validate_signal", signal={**valid, "stop_loss": "1.0900"})
        self.assertFalse(rejected["approved"])
        self.assertIn("stop-loss < entry", rejected["reason"])

    def test_risk_validator_never_rounds_up_below_minimum_lot(self):
        signal = {
            "symbol": "EURUSD", "direction": "Buy", "strength": "Strong",
            "entry_price": "1.0850", "stop_loss": "1.0350", "take_profit": "1.1850",
        }
        result = self.call("validate_signal", signal=signal, account={"equity": "100"})
        self.assertFalse(result["approved"])
        self.assertIn("below broker minimum", result["reason"])

    def test_regime_analysis_from_csv(self):
        result = self.call("analyze_csv", symbol="EURUSD", csv_text=self.as_csv(self.sample_bars()))
        self.assertEqual(result["bars"], 100)
        self.assertEqual(result["regime"]["regime"], "Trending_Up")

    def test_backtest_from_csv_returns_trade_metrics(self):
        rows = self.sample_bars()
        signal = {
            "symbol": "EURUSD", "direction": "Buy", "strength": "Strong",
            "timestamp": rows[60]["timestamp"], "entry_price": "1.0890",
            "stop_loss": "1.0880", "take_profit": "1.0920", "signal_id": "test123",
        }
        result = self.call(
            "backtest_csv", symbol="EURUSD", csv_text=self.as_csv(rows),
            signals=[signal], initial_equity="10000", timeframe="M15",
        )
        self.assertEqual(result["trade_count"], 1)
        self.assertEqual(result["summary"]["total_trades"], 1)
        self.assertGreater(float(result["summary"]["net_profit"]), 0)

    def test_signal_journal_persists(self):
        signal = {
            "symbol": "EURUSD", "direction": "Buy", "strength": "General",
            "timestamp": "2026-01-01T00:00:00Z",
        }
        saved = self.call("track_signals", signals=[signal])
        self.assertEqual(saved["tracked"], 1)
        history = self.call("list_signals", limit=10)
        self.assertEqual(history["count"], 1)
        self.assertEqual(history["signals"][0]["symbol"], "EURUSD")


if __name__ == "__main__":
    unittest.main()
