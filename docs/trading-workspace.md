# Trading workspace

The Android app now has dedicated **Markets** and **Trades** destinations in the bottom navigation.

## Markets
- Forex, crypto, metals, and equity watchlist categories.
- Searchable instrument list and a bond-yield section.
- Market values remain blank until a verified market-data provider is configured. The UI does not present fabricated prices, percentage moves, charts, or yields as live data.

## Trades
- Create and persist local trade-journal positions.
- Record instrument, BUY/SELL side, position units, entry, current/mark price, stop loss, and take profit.
- Validate directional price levels before saving (BUY: SL < Entry < TP; SELL: TP < Entry < SL).
- Open-position list, estimated P/L, risk/reward, position size, and closed-trade history.
- Trade detail view with entry/SL/TP overlays, Modify, and Close actions.
- Close changes the local journal status only. It does not place, modify, or close a broker order.

## Chart and P/L limitations
The trade-detail chart currently uses a clearly labelled illustrative candle pattern to demonstrate the chart layout and overlays. It is not historical or live OHLC data. P/L is a simple price-difference estimate using the entered units; accurate account-currency P/L for cross-currency FX pairs, contract specifications, spread, swap, commission, leverage, and margin requires instrument metadata and a broker/data integration.

## Not yet connected
- No live market-data or streaming quote provider.
- No broker account, order placement, or execution API.
- No verified market movers, earnings calendar, or real bond-yield feed.
- The trade journal is stored locally on the device and is not synced across devices.
