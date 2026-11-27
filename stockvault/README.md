# StockVault for Android

Look up any stock, ETF, index, currency or crypto, see its chart, key stats and analysis over time,
save interesting ones into folders, build model portfolios and backtest them: what an amount
invested on a date, plus optional monthly additions, would be worth today. Find similar stocks with
Google's AI Mode or with Claude.

## Where the data comes from

Google Finance has no API, and scraping its charts isn't practical (they're drawn by scripts), so
StockVault combines two free, keyless sources:

| | Used for | Notes |
| --- | --- | --- |
| **Yahoo Finance** (the public endpoints its own site uses) | Live quotes, intraday and daily charts, full daily history adjusted for splits and dividends, dividends and splits, key stats (valuation, margins, growth, ownership, analyst targets, next earnings), company profile, news, symbol search, "people also watch" | The same source most open-source finance tools use. History is complete and adjusted, which is what makes backtests correct. Key stats need a session token that Yahoo gives any visitor; the app gets it automatically. |
| **Google Finance** quote page | The stats table (previous close, day and year range, market cap, P/E, dividend yield, CEO, founded, …) and the About box, read from the page's HTML | Shown in a Google Finance section on each stock. **Open in app** shows the real page in a private browser view for anything else Google has. |

Both are unofficial. If one changes or rate-limits, the app keeps showing what still works and says
what failed. Daily histories are cached on the phone for 6 hours and used offline. Quotes can be
delayed depending on the exchange. Nothing here is investment advice.

## Features

- **Markets**: search by name or symbol, a row of market tiles (S&P 500, Dow, Nasdaq, VIX, 10-year yield,
  gold, oil, EUR/USD, Bitcoin; editable in Settings) and recently viewed stocks with sparklines.
- **Stock**: price and change, chart for 1D to Max with a touch-and-drag crosshair, 50/200-day averages,
  and a comparison line against any symbol (or the benchmark) in % terms.
  - *Overview*: Yahoo key stats, company profile and description, analyst view, and the Google Finance stats.
  - *Analysis*: returns over 1 week to 10 years and since listing (annualised for long periods), volatility,
    worst fall and recovery, Sharpe and Sortino, beta and correlation against the benchmark, RSI, trend from
    the moving averages, 52-week position, trailing dividend yield, best and worst days, and returns by year.
  - *What if*: "if I had put 10,000 in on this date and added 200 a month", with the result chart.
  - *Similar*: Yahoo's "people also watch" list, plus one tap to ask Google AI Mode or Claude for similar stocks.
  - *News* from Yahoo Finance.
- **Saved**: folders inside folders for stocks to look at later. Each saved stock keeps the price it had when
  saved and shows the change since, plus a note. Rename, move and delete folders; turn any folder into a
  portfolio to backtest it.
- **Portfolios**: holdings with weights, a starting amount, monthly additions, a start and end date,
  rebalancing (never, monthly, quarterly, yearly) and a benchmark (SPY by default). The backtest reinvests
  dividends (adjusted prices) and shows what you put in, what it's worth, the gain, CAGR, money-weighted
  return (IRR) when you add money monthly, volatility, Sharpe, Sortino, worst fall, best and worst year,
  where the money ended up per holding, and returns by year. **Compare** runs several portfolios over the
  same days with the same money and overlays them.
- **AI**: Google AI Mode and Claude, side by side. Stock symbols in any answer appear as buttons to open them,
  and **Save all** puts them in a folder.

## AI: Google AI Mode and Claude

**Google AI Mode** runs in the same private view as [SearchVault](../searchvault/): no Google account, cookies
and site data wiped before every search, sign-in blocked. The app reads the answer as Google writes it and
pulls out the tickers.

**Claude.** You asked whether the app can send a question to the installed Claude app and get the result back.
It can send it, but it can't read the answer back on its own: Android has no way for one app to read another
app's replies, and the Claude app doesn't offer an API or a "return the result" intent. So StockVault offers
the two closest things:

1. **Claude app round trip.** Questions (for example *Similar* → Claude) open the Claude app with the question
   filled in. When Claude has answered, tap **Share** on the answer and pick **StockVault**, or copy it and tap
   **Paste Claude's answer**. StockVault picks the stock symbols out of it so you can open or save them. The
   questions ask Claude to end with a `TICKERS:` line so this is reliable.
2. **Built in, with your own API key.** Add an Anthropic API key in Settings and Claude answers inside the app,
   searching the web for current figures, with sources and stock buttons, and follow-up questions. It uses the
   official Anthropic Java SDK with Claude Opus 5.5 by default (changeable in Settings). API use is billed to your
   Anthropic account. The key stays on the phone, encrypted with the Android Keystore.

## Sync and backup

Settings → **Sync with JSONBin.io** keeps folders, saved stocks and portfolios the same on several phones, with
the same keys as BookmarkVault and SearchVault, in its own private bin named `StockVault-<device>`. A sync pulls
and merges (newer edit wins, deletions carry over), then pushes. **Export/Import JSON** saves or merges a backup
file. Price data is never synced.

```json
{
  "stockvault": {
    "version": 1, "app": "StockVault", "device": "Pixel 8", "exportedAt": "…",
    "folders": [{ "id": "…", "name": "Chips", "parentId": "…", "createdAt": "…", "updatedAt": "…" }],
    "saved": [{ "id": "…", "symbol": "AMD", "name": "Advanced Micro Devices", "folderId": "…",
                "note": "", "priceAtSave": 141.2, "currency": "USD", "addedAt": "…", "updatedAt": "…" }],
    "portfolios": [{ "id": "…", "name": "Core", "note": "",
                     "holdings": [{ "symbol": "VTI", "name": "…", "weight": 60 }, { "symbol": "BND", "name": "…", "weight": 40 }],
                     "plan": { "amount": 10000, "monthly": 200, "start": "2015-01-01", "end": null,
                               "rebalance": "yearly", "benchmark": "SPY" },
                     "createdAt": "…", "updatedAt": "…" }],
    "deleted": [{ "id": "…", "at": "…" }]
  }
}
```

## Install

Grab the latest `StockVault-*.apk` from the repository's Releases page and open it on your phone.
Requires Android 8.0 or newer.

## Build

The workflow in `.github/workflows/stockvault.yml` runs the unit tests and builds on every push
that touches this folder, and attaches the APK to the run. Pushes to `main` and manual runs also
publish a GitHub release tagged `stockvault-v1.0.<run number>`.

Locally: `cd stockvault && ./gradlew test assembleDebug` (needs JDK 17 and the Android SDK).

Signing uses the same four repository secrets as the other apps; see the top-level README.
