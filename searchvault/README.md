# SearchVault for Android

Ask Google's AI search from your phone, keep every question and answer, and sync them to
[JSONBin.io](https://jsonbin.io) with the same keys you use for BookmarkVault.

## Two ways to search

| | **Gemini** (default) | **AI Mode** |
| --- | --- | --- |
| What it is | The Gemini API with Google's built-in *Google Search* tool: Gemini runs real Google searches and answers from the results | Google's own AI Mode page (`google.com/search?udm=50`) inside the app |
| Needs | A free Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey) | Nothing (sign in to Google in the page if AI Mode asks) |
| Answer | Formatted answer with numbered citations, the list of source pages, and the Google queries it ran | Google's page as is |
| Follow-ups | In the app; earlier questions are sent along as context | Inside Google's page |
| What gets saved | Question, answer, sources, queries, model, time | The question as soon as you search; tap **Save answer** to also keep the page's answer text and links |

Gemini is the main route because it gives a clean, structured answer with sources that can be stored and synced
reliably. Google has no public API for AI Mode itself, so the AI Mode tab is a web page, and what it saves is
the page's text, which depends on how Google lays the page out.

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
