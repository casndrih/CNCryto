# Trading workspace and public market data

The Android app includes dedicated Markets and Trades destinations. Market data is retrieved from public endpoints; the app does not scrape rendered finance webpages.

## Public data sources

| Asset class | Source | Data exposed in the app | Update characteristics |
|---|---|---|---|
| Crypto | Binance public market-data-only REST API | 24-hour ticker and hourly candlesticks | Exchange market data; polling on screen load/manual refresh |
| Forex | Frankfurter API backed by European Central Bank reference rates | Major-pair reference quotes and daily rate history | Daily reference rates, not intraday/live broker prices |
| Equity index proxies | Stooq public CSV | SPY, QQQ, DIA and IWM ETF daily close/history | Daily data; ETF proxies, not the official index calculation |
| Metals/energy | Stooq public CSV | Gold, silver and selected energy symbols where available | Daily data; symbol coverage may vary |
| U.S. Treasury curve | FRED public CSV graph endpoint | 1-month, 1-year, 2-year, 5-year, 10-year and 30-year series | Daily constant-maturity observations; latest date is shown |

No API key is required for these default sources. Public endpoints can change, throttle requests, or be unavailable in some regions. The UI displays unavailable states rather than inventing prices. Binance market data is exchange-specific. Frankfurter/ECB FX values are reference rates. Stooq symbols are public daily CSV feeds and coverage is not guaranteed. Treasury series are U.S.-specific.

## Markets
- Forex, crypto, metals, and equity watchlist categories.
- Searchable instruments, source labels, timestamps, refresh action, and percent changes when the source provides them.
- Treasury yield curve values from FRED.
- Values are indicative reference data, not broker bid/ask quotes or guaranteed executable prices.

## Trades
- Create and persist local trade-journal positions.
- Record instrument, BUY/SELL side, position units, entry, current/mark price, stop loss, and take profit.
- Validate directional price levels before saving (BUY: SL < Entry < TP; SELL: TP < Entry < SL).
- Open-position list, estimated P/L, risk/reward, position size, and closed-trade history.
- Trade detail view with public historical candles where available and Entry/SL/TP overlays.
- The displayed mark price is refreshed from a matching public feed where supported.
- Close changes the local journal status only. It does not place, modify, or close a broker order.

## AI analysis context
The multi-provider analysis request retrieves public historical bars for the requested symbol and supplies recent OHLC values plus the data-source and frequency note to configured AI providers. If a source is unavailable, the prompt explicitly says no market features were supplied and instructs models not to invent market facts.

## Important limitations
- No broker account, order placement, execution API, or private account balance.
- No websocket streaming; market data refreshes when the screen loads or the user taps refresh.
- FX history is daily ECB reference-rate history, not intraday OHLC. The chart should be interpreted as daily reference-rate points.
- Equity index cards use ETF proxies (SPY/QQQ/DIA/IWM), not the official indices.
- Stooq symbol coverage for metals and energy can vary.
- The trade journal is stored locally and is not synced across devices.
- P/L is a simple price-difference estimate and does not yet account for pip value, contract specifications, account-currency conversion, spread, swap, commission, leverage, or margin.
- Public feed access, licensing, display permissions, and rate limits are governed by each source's terms. Verify the relevant terms before commercial redistribution.