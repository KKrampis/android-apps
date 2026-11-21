# ClipHarbor

An Android app for watching YouTube without signing in.

- **Browse and search** anonymously. No Google account and no API key: pages are read with
  [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor).
- **Play** in the app, with a quality picker and full screen. Open a channel from any video.
- **Download** a video (up to 1080p, with picture and sound merged into one MP4 on the phone)
  or just its audio (M4A). Each download goes into a folder you choose, which defaults to the
  channel name:
  - video: `Movies/ClipHarbor/<folder>/`
  - audio: `Music/ClipHarbor/<folder>/`

  They show up in your gallery, music player and file manager, and play offline in the app.
- **Save** videos for later, and make **playlists**.
- **Sync** playlists and saved videos as one JSON file to:
  - a GitHub repository you choose (with a personal access token), and/or
  - [jsonbin.io](https://jsonbin.io) (with your master key).

  You can push by hand, import back, or turn on automatic push after each change.
  Keys are stored only on the phone, encrypted with the Android Keystore, and app backup is off.
- Share a YouTube link from any app to ClipHarbor to open it.

Requires Android 8.0 or newer.

## Install

Grab the latest `ClipHarbor-*.apk` from the repository's Releases page and open it on your phone.
You may need to allow installing apps from your browser or file manager.

## Sync setup

**GitHub:** create a [fine-grained token](https://github.com/settings/personal-access-tokens/new)
limited to the one repository you want to use, with *Contents: Read and write*. In the app open
Settings, fill in `owner/name`, branch and file path (default `clipharbor/playlists.json`), paste
the token, and press Push.

**jsonbin.io:** copy your master key from the jsonbin.io dashboard (API Keys), paste it in
Settings and press Push. The first push creates a private bin and fills in its id.

The file looks like this:

```json
{
  "app": "ClipHarbor",
  "formatVersion": 1,
  "exportedAt": 1763683200000,
  "playlists": [
    { "name": "Cooking", "createdAt": 0, "updatedAt": 0,
      "videos": [ { "url": "https://www.youtube.com/watch?v=…", "title": "…", "uploader": "…",
                    "uploaderUrl": "…", "thumbnail": "…", "durationSec": 612 } ] }
  ],
  "saved": [ ]
}
```

Import merges: playlists are matched by name and missing videos are added. Nothing is deleted.

## Build

The workflow in `.github/workflows/clipharbor.yml` builds on every push that touches this folder,
and attaches the APK to the run. Pushes to `main` and manual runs also publish a GitHub release
tagged `clipharbor-v1.0.<run number>`.

Locally: `cd clipharbor && ./gradlew assembleDebug` (needs JDK 17 and the Android SDK).

## Signing

Without a keystore the workflow signs with a debug key and says so on the run. That works for
installing, but if the cached debug key expires (7 days without a build) the next APK won't
install over the old one without uninstalling first. To sign with your own key, make one once
on your computer (keep the file and passwords safe, you need the same key for every update):

```sh
keytool -genkeypair -v -keystore release.jks -alias clipharbor \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 release.jks > release.jks.b64   # on macOS: base64 -i release.jks -o release.jks.b64
```

Then add these repository secrets (Settings → Secrets and variables → Actions → New repository secret):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Contents of `release.jks.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | The keystore password you chose |
| `ANDROID_KEY_ALIAS` | `clipharbor` (or the alias you used) |
| `ANDROID_KEY_PASSWORD` | The key password (same as the keystore password unless you set a different one) |

Switching from the debug key to your own key changes the signature, so uninstall the
debug-signed build once before installing the first release-signed one.

## Notes

- YouTube changes its site often. When browsing or playback stops working, the usual fix is
  bumping the NewPipeExtractor version in `app/build.gradle.kts` to the latest release.
- Age-restricted videos need a login and are not supported.
- Downloading may be against YouTube's terms for some content. Download only what you have the right to keep.
