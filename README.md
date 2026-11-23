# android-apps

A collection of Android apps I've built, each from a one-shot prompt in Claude Opus 5.5.
Each one covers an everyday activity with open data or without the official app:
biking around NYC and New Jersey with open city data, browsing YouTube privately without
the YouTube app or an account, keeping bookmarks in sync with Chrome, and saving AI search
answers without signing in to Google.

Every app lives in its own top-level folder, has its own README, and has its own GitHub
Actions workflow that builds the APK and publishes it to [Releases](../../releases).

## The apps

### ClipHarbor: private YouTube browsing · [`clipharbor/`](clipharbor/)

Browse and search YouTube without the app, an account or an API key. Watch with
quality selection and full screen, download videos or audio into folders you choose,
save videos, build playlists, and sync them to GitHub or JSONBin.io. Sharing a YouTube
link to ClipHarbor opens it there.

### BookmarkVault: bookmarks synced with Chrome · [`bookmarkvault/`](bookmarkvault/)

The Android version of the BookmarkVault Chrome extension. It keeps a bookmark library
on the phone and syncs it with Chrome through JSONBin.io, using the same JSON format, so
a library pushed from Chrome pulls on the phone and the other way round. Exports JSON and
Netscape HTML, and you can share any page from a browser to bookmark it.

### SearchVault: private AI search with saved answers · `searchvault/` (in review, [#3](../../pull/3))

Search with Google's AI Mode in a private browser view, with no Google account. Cookies
and history are wiped before every search and sign-in is blocked. Every answer is saved
with its source links, so you can search, star, share or continue it later, and the
archive syncs to JSONBin.io. An optional Gemini mode with your own API key gives answers
with numbered citations.

### SpokeMap: NYC and NJ biking with open data · `spokemap/` (in review, [#5](../../pull/5))

A bike map for New York City and northern New Jersey built entirely on open data, with
no keys or accounts. Its layers are OpenStreetMap bike lanes and paths, live Citi Bike
stations with dock and bike availability (including Jersey City and Hoboken), the NYC DOT
bike network, and NJTPA bike level of traffic stress for northern New Jersey roads.

## Signing

All apps read the same four repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Your keystore file, base64 encoded |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password |
| `ANDROID_KEY_ALIAS` | The key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | The key password |

If they are missing, the workflow still builds and signs the APK with a debug key and
shows a warning on the run. See [`clipharbor/README.md`](clipharbor/README.md#signing) for how to make the keystore.
