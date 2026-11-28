# Radio Recorder for Android

Records internet radio stations on your phone, around the clock or on a schedule. It cuts the
recordings into one file per program, uploads them to the [Internet Archive](https://archive.org),
and clears old local copies so storage never fills up. It's the Android version of the
[Radio Recorder desktop app](https://github.com/KKrampis/radio-recorder-archive) and works the
same way.

## What it does

- **Several stations at once.** Each station records on its own, in the background, with the
  screen off. A notification shows what's being recorded and has a **Stop recording** button.
- **Program-change splitting.** A new file starts when the stream's title (ICY `StreamTitle`)
  changes. A minimum file length (15 minutes by default) keeps music stations that send song
  titles from making tiny files: those titles are noted in the recording's info instead.
- **Hourly fallback.** Files are always cut at a maximum length (60 minutes by default), on the
  hour if you like. This also covers stations that send no titles.
- **Scheduling.**
  - Weekly program slots: days, start and end time, and a program name. Slots can run past
    midnight.
  - One-off recordings on a date.
  - A time zone per station, so a station abroad follows its own clock.
  - Stations can record **always** (slot names then only label and split programs) or **only during
    their slots**.
  - An exact alarm starts each scheduled recording on time, even when the phone is idle, and
    recording resumes after a reboot.
- **Quality.**
  - **Original** saves the broadcast exactly as sent: best quality and no extra battery use.
  - **AAC** at 48–192 kbps, mono or stereo, re-encoded on the phone with Android's own codecs.
    Re-encoding works for MP3 and AAC streams; for other formats the original is kept.
- **Internet Archive uploads.**
  - Each station can use its own collection.
  - Each recording becomes an item tagged with the station, program name, date, hour and weekday,
    and every stream title heard during it.
  - Uploads can be limited to Wi-Fi only. Failed uploads retry with back-off.
  - The secret key is stored encrypted with the Android Keystore.
- **Automatic cleanup** (every 24 hours by default).
  - It **uploads everything pending first**, then deletes local copies older than the retention
    time that are already on archive.org.
  - A free-space guard deletes the oldest uploaded files early if storage runs low.
  - Recordings that haven't been uploaded are kept, unless you set a time limit for them.
- **Recordings list** with playback, sharing, a link to each item on archive.org, and **Retry**
  for failed uploads.
- **Adding stations by sharing.** Share a stream link from the browser to Radio Recorder to add it
  as a station.

## Using it

1. **Stations → Add station.** Paste a stream link: a direct MP3/AAC Icecast or Shoutcast URL, or a
   `.pls` / `.m3u` playlist link. Choose the quality, splitting and schedule, then tap **Save**.
2. When asked, allow notifications. On the Stations screen, tap **Allow** under *Allow background
   recording* so Android doesn't pause long recordings to save battery.
3. **Schedule** lists what's coming up across all stations, in your phone's time.
4. **Recordings**: play, share, open on archive.org, or retry an upload.
5. **Settings**: archive.org keys and default collection, upload options, and cleanup timing.

### archive.org keys

1. Log in (or sign up for free) at archive.org.
2. Open <https://archive.org/account/s3.php> (Settings has a button for it).
3. Tick the box and tap **Generate New Keys**.
4. Paste the access key and the secret key into **Settings**, tap **Test keys**, then **Save settings**.

You can't create archive.org collections yourself: ask archive.org for one per station (via
info@archive.org) and enter its identifier in the station editor. Until then, recordings go to the
default collection, `opensource_audio` (Community Audio). **Test mode** uploads to
`test_collection`, which archive.org removes after about 30 days.

### Where recordings are kept

In the app's own folder: `Android/data/io.github.kkrampis.radiorecorder/files/Music/RadioRecorder/<station>/<date>/`,
named like `harbor-city-radio_20261127-090000_morning-show.aac`. Use **Share** on a recording to
save it elsewhere. Uninstalling the app deletes this folder.

## Limitations

- **HLS streams (`.m3u8`) aren't supported.** Android has no simple way to save them frame by frame
  without a large media library. Most stations also publish a direct Icecast/Shoutcast MP3 or AAC
  link: use that.
- **MP3, Opus and FLAC encoding aren't available.** Android only includes an AAC encoder, so the
  choices are Original or AAC.
- Recording all day keeps the phone's network and CPU awake. Plug the phone in for 24/7
  recording.

## How it's built

| Part | What it does |
| --- | --- |
| `core/` | Plain Kotlin, unit tested: settings, schedule windows, ICY metadata, MP3/ADTS frame parsing, archive.org metadata and file naming |
| `engine/StreamSource.kt` | Reads the stream with OkHttp (which accepts old `ICY 200 OK` servers) and separates out the titles |
| `engine/Writers.kt` | Original pass-through, or MediaCodec decode → AAC encode to ADTS `.aac` files |
| `engine/StationRecorder.kt` | One thread per station: connect, write, split, reconnect with back-off |
| `engine/Uploader.kt`, `Cleaner.kt` | IA-S3 uploads and the upload-first cleanup |
| `service/` | Foreground service (wake lock and Wi-Fi lock while recording), exact alarms, restart on boot |
| `ui/` | Jetpack Compose screens |

`core/` follows the desktop app's logic, and the settings use the same JSON field names.

## Building

GitHub Actions builds the APK on every push that touches `radiorecorder/`, runs the unit tests,
and publishes a release named `Radio Recorder 1.0.N` from `main`. AppShelf picks these up
automatically.

Locally: Android Studio, or `./gradlew assembleRelease` with JDK 17 and the Android SDK.

Signing works the same as the other apps in this repository: see
[`clipharbor/README.md`](../clipharbor/README.md#signing).
