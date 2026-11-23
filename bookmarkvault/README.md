# BookmarkVault for Android

The Android version of the [BookmarkVault Chrome extension](https://github.com/KKrampis/bookmarkvault):
keep a bookmark library on your phone and sync it with Chrome through [JSONBin.io](https://jsonbin.io).
It uses the same JSON format and the same bins, so the two work together.

| Chrome extension | Android app |
| --- | --- |
| Reads Chrome's bookmarks | Keeps its own library on the phone (Android has no shared bookmark store), with the same *Bookmarks bar / Other bookmarks / Mobile bookmarks* folders |
| Push to JSONBin (`POST` the first time, then `PUT`, body `{"bookmarkvault": …}`) | Same, with the same master key. The first push creates a private bin named `BookmarkVault-<device>` |
| Pull by Sync ID with master key, access key, or none for a public bin | Same |
| Folder picker, import into *Other Bookmarks › BookmarkVault — device · date* | Same picker and folder, plus **Replace my library** to mirror one browser exactly |
| Export JSON and Netscape HTML | Same files, saved anywhere through the Android file picker, or shared as text |
| Import a `.json` file or pasted JSON | Same, and `.json` files open straight from a file manager |
| Push history (last 10), device name, clear sync data | Same |
| | Browse folders, search, open in your browser, add, edit, move and delete bookmarks and folders |
| | **Share** a page from any browser to BookmarkVault to bookmark it |

Keys are stored only on the phone, encrypted with the Android Keystore, and app backup is off.
Bookmarks that aren't web links (bookmarklets, `chrome://` pages) are skipped on import, as in the extension.

Requires Android 8.0 or newer.

## Typical use

1. In Chrome, open BookmarkVault → Sync, enter your master key and press **Push**. Copy the Sync ID.
2. On the phone, open **Sync**, enter the same master key, paste the Sync ID and press **Pull**.
3. Choose **Replace my library with this** to get an exact copy, or tick folders and import them.
4. Add bookmarks on the phone (or share pages to the app), then **Push** from the phone.
   The phone pushes to its own bin, shown under *Your Sync ID*; pull that ID in Chrome to bring them over.

## Install

Grab the latest `BookmarkVault-*.apk` from the repository's Releases page and open it on your phone.

## Build

The workflow in `.github/workflows/bookmarkvault.yml` runs the unit tests and builds on every push
that touches this folder, and attaches the APK to the run. Pushes to `main` and manual runs also
publish a GitHub release tagged `bookmarkvault-v1.0.<run number>`.

Locally: `cd bookmarkvault && ./gradlew test assembleDebug` (needs JDK 17 and the Android SDK).

Signing uses the same four repository secrets as the other apps; see the top-level README.
