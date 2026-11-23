# android-apps

Each app lives in its own top-level folder and has its own GitHub Actions workflow
that builds the APK and publishes it to [Releases](../../releases).

| App | Folder | What it does |
| --- | --- | --- |
| ClipHarbor | [`clipharbor/`](clipharbor/) | Browse and search YouTube without an account, play, download into folders, save videos, build playlists and sync them to GitHub or jsonbin.io |
| BookmarkVault | [`bookmarkvault/`](bookmarkvault/) | Android version of the BookmarkVault Chrome extension: a bookmark library on the phone that syncs with Chrome through jsonbin.io, with the same JSON format, import and export |
| SearchVault | [`searchvault/`](searchvault/) | Ask Google AI Mode anonymously (no account or key; cookies wiped per search) or Gemini with Google Search, keep every question and answer on the phone and sync them to jsonbin.io |

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
