# SearchVault for Android

Ask Google's AI search from your phone, keep every question and answer, and sync them to
[JSONBin.io](https://jsonbin.io) with the same keys you use for BookmarkVault.

## Two ways to search

| | **AI Mode** (default, anonymous) | **Gemini** (optional) |
| --- | --- | --- |
| What it is | Google's own AI Mode page (`google.com/search?udm=50`) inside the app | The Gemini API with Google's built-in *Google Search* tool |
| Needs | Nothing: no account, no key | An API key from [Google AI Studio](https://aistudio.google.com/apikey), which is tied to your Google account |
| What gets saved | The question right away; then the answer text and its source links, read from the page and saved automatically once Google stops writing (about 3 s of no change). **Save now** saves on demand | Question, answer with numbered citations, sources, the Google queries it ran, model, time |
| Follow-ups | Inside Google's page; the newer page text is saved again | In the app; earlier questions are sent along as context |

### How private AI Mode is

With *Forget Google after every search* on (Settings, on by default):

- Google's cookies, cache, history and site storage in the app are wiped before every search and when the app starts, so searches can't be linked by cookie.
- Signing in is blocked; third-party cookies, location, Safe Browsing lookups and WebView usage reports are off.
- A consent cookie set to "reject all" is added after each wipe, so visitors in Europe don't get the cookie page every time.

What it can't hide: Google still sees your IP address and device/browser details, as with any private browser
window. Use a VPN or Tor-based proxy on the phone if you need to hide the IP too.

### Limits of reading the page

- There is no public AI Mode API, so the app reads the page text and drops Google's menus and buttons. If Google
  changes the page layout, saved answers may pick up extra lines or miss parts until the app is updated.
- Formatting is saved as plain text (no tables or images). Links in the answer area are saved as sources.
- Without cookies Google may sometimes show a captcha ("unusual traffic"), especially on VPNs; the app shows a
  notice and you can solve it in the page. If the cookie page still appears, choose *Reject all*.
- AI Mode may not be available in every country.

## Features

- **Ask**: a chat-style screen. Ask a question, then ask follow-ups in the same search. Tap a citation number or a source to open it.
- **Saved**: every search, newest first. Filter by text, star the ones you want to keep handy, rename, delete,
  share or copy as Markdown, re-open in Google AI Mode, or continue a saved search with a follow-up.
- **Sync**: JSONBin master key (and optional access key), **Sync now**, and *Sync after every search*.
  A sync reads the bin, merges it with the phone (newer edit wins, deletions carry over), then writes the result back,
  so two phones pointed at the same Sync ID share one history. Export and import a JSON backup file.
- Share text from any app, or select text and pick **Ask SearchVault**, to search it straight away.

API keys stay on the phone, encrypted with the Android Keystore, and app backup is off.

## JSONBin format

The first sync creates a private bin named `SearchVault-<device>`. Its content:

```json
{
  "searchvault": {
    "version": 1,
    "app": "SearchVault",
    "device": "Pixel 8",
    "exportedAt": "2026-11-23T02:30:00Z",
    "searches": [
      {
        "id": "…", "title": "…", "starred": false, "note": "",
        "createdAt": "…", "updatedAt": "…",
        "turns": [
          {
            "question": "…", "answer": "Markdown with [1] citations",
            "engine": "gemini", "model": "gemini-flash-latest", "at": "…",
            "queries": ["…"],
            "sources": [{ "title": "…", "url": "…" }]
          }
        ]
      }
    ],
    "deleted": [{ "id": "…", "at": "…" }]
  }
}
```

The backup file is the same document without the `searchvault` wrapper. JSONBin's free plan limits the size of a
bin, so a very long history may need the paid plan or an occasional clean-up.

## Install

Grab the latest `SearchVault-*.apk` from the repository's Releases page and open it on your phone.
Requires Android 8.0 or newer.

## Build

The workflow in `.github/workflows/searchvault.yml` runs the unit tests and builds on every push
that touches this folder, and attaches the APK to the run. Pushes to `main` and manual runs also
publish a GitHub release tagged `searchvault-v1.0.<run number>`.

Locally: `cd searchvault && ./gradlew test assembleDebug` (needs JDK 17 and the Android SDK).

Signing uses the same four repository secrets as the other apps; see the top-level README.
