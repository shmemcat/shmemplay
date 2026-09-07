<p align="center">
  <img src="docs/design/shmemplay-icon.png" width="132" alt="shmemplay record icon">
</p>

<h1 align="center">shmemplay</h1>

<p align="center">
  A queue-first Android music player and safety-minded M3U playlist editor for large, local libraries.
</p>

<p align="center">
  <a href="https://github.com/shmemcat/shmemplay/actions/workflows/android.yml"><img src="https://github.com/shmemcat/shmemplay/actions/workflows/android.yml/badge.svg" alt="Android CI"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white" alt="Android 8.0 and newer">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose">
</p>

shmemplay is a native Kotlin/Compose app that browses audio exposed by Android's
MediaStore, plays it with Media3, and works directly with playlist files in a
folder you choose through Android's Storage Access Framework. It was built
around a GoneMAD Music Player and MusicBee playlist workflow, but GoneMAD is not
required for playback.

Everything runs on the device: the app has no account system and its manifest
does not request network access.

## Highlights

- **Native playback:** background playback, Android media controls, audio-focus
  handling, route-disconnect behavior, and configurable headset button actions.
- **Android Auto and widgets:** browse and resume saved queues in the car,
  search queued songs, and resume through Android media buttons. Standard player
  discovery supports widgets such as KWGT. See [setup and device verification](docs/android-auto.md).
- **Persistent queues:** keep multiple independent queues, each with its own
  track, position, order, shuffle state, repeat/end policy, and stop-after marker.
  Search and reorder a queue, switch without losing your place, or export one as
  an M3U snapshot.
- **Large-library browsing:** browse songs, albums, artists, genres, and
  playlists with artwork, normalized search, folder filters, compact lists,
  fast scrolling, and bulk selection.
- **Playlist editing:** add or remove one or many songs across selected
  `.m3u`/`.m3u8` files; create, rename, and delete file playlists; or edit
  membership without leaving the browser.
- **Rule-built playlists:** combine nested **Match all**/**Match any** groups
  using playlist membership, artist, genre, and title rules. Save a fixed M3U
  snapshot or a live playlist that reevaluates inside shmemplay.
- **Song tools:** inspect metadata and embedded MP3 lyrics, share an audio file,
  edit supported MP3 ID3v2.3/v2.4 text tags, or request permanent deletion
  through Android's confirmation flow. Tag writes and deletion require Android
  11 or newer; browsing and playback remain available on Android 8–10.

## Development status

> [!CAUTION]
> shmemplay is under active development. The current build is version `2.0.0`.
> The player, queue system, library browser, playlist editor, and rules builder
> are implemented—not mockups—but device validation is still in progress. Keep
> backups and use disposable playlist/audio copies when testing write features.

The current build has been exercised on a Samsung SM-S928U running Android 16
against a 23,042-song library. Core JVM tests, Android lint, debug builds, and
targeted on-device Compose tests pass. Longer background-playback sessions,
more device and accessibility combinations, provider change notifications, and
real-file tag-edit recovery still need broader validation. See the
[player implementation and verification notes](docs/player-implementation.md)
and [playlist builder notes](docs/playlist-builder.md) for the exact tested and
untested boundaries.

## Playlist compatibility and write safety

shmemplay discovers `.m3u` and `.m3u8` files directly inside the selected
playlist folder. Its add/remove rewrite path is intentionally stricter than its
parser: a writable playlist must use the canonical GoneMAD profile—UTF-8 without
a BOM, LF line endings, a trailing newline, path-only records, and absolute
primary-storage paths beginning with `/storage/emulated/0/`. Files containing
`#EXTM3U`/`#EXTINF`, relative paths, blank records, or other encodings are not
silently normalized and overwritten.

For eligible add/remove operations, shmemplay rereads the source, plans the
whole batch, asks for reconfirmation if material state changed, keeps exact-byte
backups, writes each target once, rereads and verifies the result, and rolls
back on failure. An operation journal supports recovery and blocks further
writes if recovery is still required.

Live rule playlists are stored inside shmemplay. They do not automatically sync
their definitions to MusicBee or Shmembee; create a snapshot when another app
needs a physical M3U file.

## Build from source

The primary development workflow uses Windows 10/11 and PowerShell; CI runs on
Linux. You will need:

- JDK 17
- Android SDK Platform 36 and Build Tools 36.0.0
- Android Studio compatible with Android Gradle Plugin 9.0, or an equivalent
  command-line Android SDK setup
- An emulator or test device only for connected tests

```powershell
git clone https://github.com/shmemcat/shmemplay.git
cd shmemplay

# Android Studio can create local.properties for you.
# Alternatively, set ANDROID_HOME or ANDROID_SDK_ROOT.
.\gradlew.bat :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On Linux, use
`./gradlew` in place of `.\gradlew.bat`.

Run the same build, lint, and JVM-test gate used by CI:

```powershell
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Run instrumented tests separately on an emulator or dedicated test device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Project map

- [`app/`](app/) — Android app, Compose UI, Media3 playback service, Room
  persistence, storage adapters, and Android/JVM tests.
- [`domain/`](domain/) — pure Kotlin queue, playlist, path, parser, writer, and
  rules contracts with JVM tests.
- [`contract-fixtures/`](contract-fixtures/) — versioned cross-language fixtures
  shared with Shmembee.
- [`docs/music-player-plan.md`](docs/music-player-plan.md) — player interaction
  contract and queue invariants.
- [`docs/player-implementation.md`](docs/player-implementation.md) — current
  implementation details, device evidence, and known validation gaps.
- [`docs/playlist-builder.md`](docs/playlist-builder.md) — rules-builder behavior
  and verification notes.
- [`docs/tech-spec.md`](docs/tech-spec.md) — original phased safety specification
  for playlist parsing, identity, writes, rollback, and recovery.

## Compatibility note

The project was formerly named **Shmemplaylist**. Its source namespace is now
`io.github.shmemcat.shmemplay`, while the Android application ID intentionally
remains `io.github.shmemcat.shmemplaylist`. Builds signed with the same key can
therefore update existing installations without changing the app's persisted
identity.
