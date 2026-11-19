# Trading workspace and public market data

The Android app includes Markets and Trades destinations. Data comes from public endpoints and free-key APIs; it does not scrape rendered finance webpages.

## Data sources

| Asset class | Provider | Data | Access/freshness |
|---|---|---|---|
| Crypto | Binance market-data-only REST API | 24-hour ticker and hourly candles | No key; exchange market data |
| Forex | Frankfurter / central-bank reference rates | Major-pair quotes and daily history | No key; daily reference rates, not intraday broker quotes |
| Equity index proxies | Alpha Vantage | SPY, QQQ, DIA, IWM daily OHLC | Free API key; 25 requests/day free tier |
| Gold and silver | Alpha Vantage | Spot snapshots and daily history | Free API key; subject to quota |
| WTI and Brent | Alpha Vantage | Daily commodity observations | Free API key; subject to quota |
| U.S. Treasury curve | FRED public graph CSV endpoint | 1-month, 1-year, 2-year, 5-year, 10-year and 30-year yields | Daily observations |

Binance and Frankfurter work without credentials. The optional Alpha Vantage key is configured under Settings > Market data provider and stored in encrypted app preferences. The app caches quotes and candles in memory for five minutes. Alpha Vantage's free tier is limited to 25 requests/day; real-time and 15-minute delayed U.S. equity entitlements are premium-only. Check current provider terms and display rights before commercial redistribution.

## Markets
- Forex, crypto, metals and equities categories.
- Searchable watchlists, source labels, timestamps, refresh, and percent changes where provided.
- U.S. Treasury yield curve from FRED's public graph CSV output.
- Equity index cards use ETF proxies (SPY/QQQ/DIA/IWM), not official index calculations.
- Prices are indicative reference data, not guaranteed executable bid/ask prices.

## Trades
- Create and persist local trade-journal positions.
- Record instrument, BUY/SELL side, units, entry, current/mark price, stop loss and take profit.
- Validate directional levels (BUY: SL < Entry < TP; SELL: TP < Entry < SL).
- Open-position list, estimated P/L, risk/reward, position size and closed-trade history.
- Trade detail view uses public historical candles where available and overlays Entry/SL/TP.
- Mark price refreshes from a matching public feed where supported.
- Close and Modify change the local journal only; they do not send broker orders.

## AI analysis context
The multi-provider analysis request retrieves public historical bars and supplies recent OHLC values plus source/frequency notes to configured AI providers. If a source is unavailable, the prompt says no market features were supplied and instructs models not to invent market facts. Crypto analysis uses hourly bars; FX, equities and commodity analysis use daily/reference data.

## Important limitations
- No broker account, order placement, execution API or private account balance.
- No WebSocket streaming; market data refreshes on screen load or manual refresh.
- FX history is daily central-bank reference-rate history, not intraday OHLC.
- Free Alpha Vantage quota can be exhausted; five-minute in-memory caching reduces but does not eliminate calls.
- No claim that free market-data plans permit commercial redistribution; verify current provider terms and exchange display rights.
- The trade journal is local and not synced across devices.
- P/L is a simple price-difference estimate and does not yet account for pip value, contract specifications, account-currency conversion, spread, swap, commission, leverage or margin.
