# AppShelf for Android

An app store for apps published on GitHub. It lists every app in this repository's
[Releases](../../../releases) by default, installs them with one tap, checks for new versions of
the ones you have installed and updates them. You can add any other GitHub repository that
publishes APKs as releases.

## How it works

- **Apps** lists the apps found in each repository, with the installed version next to the latest
  one. **Install**, **Update** or **Open** each one, or **Update all**. The filter chips show
  everything, only updates, or only installed apps. Pull down or tap refresh to check again.
- **App page**: latest version, size and date, release notes, every older version (install any of
  them), **Uninstall**, and a link to the release on GitHub.
- **Sources**: the repositories to read. Paste a link in any form (`https://github.com/owner/repo`,
  `…/releases`, `owner/repo`, `git@github.com:owner/repo.git`), or share a GitHub link from the
  browser to AppShelf. Reorder or remove them; `KKrampis/android-apps` can be added back with one tap.
- **Settings**: background update checks (every 6 hours, with a notification), whether to include
  pre-releases, an optional GitHub token, the install permission, and clearing downloaded APKs.

### Finding apps in releases

A repository can hold one app or many. In this repository each app publishes releases named like
`StockVault 1.0.4` with tags like `stockvault-v1.0.4`, so AppShelf groups releases into apps by the
name before the version: from the release name first, then the tag, then the APK file name. In a
repository with a single app and releases named `v2.0`, every release is a version of one app
named after the repository. When a release has APKs for several processors (`arm64-v8a`,
`armeabi-v7a`, …) it picks the one for your phone, then a universal one. Drafts are skipped, and
pre-releases unless turned on in Settings.

### Knowing what is installed

Releases don't say which Android package an APK contains, so AppShelf finds installed apps by the
package name it reads from each APK it downloads, then by the usual naming for GitHub projects
(`io.github.<owner>.<app>`, which every app here uses), then by an installed app with the same
name. It needs permission to see installed apps for that. It's installed from GitHub, not from
Google Play, so that's allowed.

Versions are compared number by number, so `1.0.10` is newer than `1.0.9`, and `1.2-beta` is older
than `1.2`.

### Installing

APKs are downloaded into the app's cache and handed to Android's package installer. The first time,
Android asks you to allow installs from AppShelf ("Install unknown apps"). Each install asks you to
confirm. On Android 12 and newer, updates to apps that AppShelf installed can go through without
asking again. Android checks that every update is signed with the same key as the installed app,
so an APK from someone else can't replace your app. If an app was first installed from a
debug-signed build and later releases use your release key, Android refuses the update. Uninstall
it once and install again.

### GitHub limits and tokens

Without a token GitHub allows 60 API requests an hour. AppShelf keeps every answer with its ETag
and asks with it next time: when nothing changed GitHub answers "not modified", which doesn't
count against the limit, and the saved copy is shown offline. A fine-grained token with read-only
**Contents** access raises the limit to 5,000 and lets AppShelf read and download from private
repositories. It is stored encrypted with a key in the Android Keystore, and backups are off.

## Install

Grab the latest `AppShelf-*.apk` from the repository's Releases page and open it on your phone.
Requires Android 8.0 or newer. After that, AppShelf lists itself too, so it can update itself.

## Build

The workflow in `.github/workflows/appshelf.yml` runs the unit tests and builds on every push
that touches this folder, and attaches the APK to the run. Pushes to `main` and manual runs also
publish a GitHub release tagged `appshelf-v1.0.<run number>`.

Locally: `cd appshelf && ./gradlew test assembleDebug` (needs JDK 17 and the Android SDK).

Signing uses the same four repository secrets as the other apps; see the top-level README.
