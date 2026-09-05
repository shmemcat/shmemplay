# Shmemplaylist

Shmemplaylist is a native Android music browser and M3U/M3U8 playlist editor
for a library played in GoneMAD Music Player. Launch it to browse songs,
albums, artists, genres, and playlists with artwork, resilient search, an
always-dark purple theme, and Musicolet-style bulk selection. Sharing a playing file from GoneMAD still opens
the focused membership editor. GoneMAD remains the music player.

> **Safety status:** Phase 7 enables real multi-playlist writes only after the
> user deliberately enables them and the app verifies the safety prerequisites
> for that operation. The unchecked Phase 6 physical-device matrix is retained
> as regression guidance, not as a blanket prerequisite that prevents a user
> from choosing to enable real writes. Unknown state or required recovery still
> blocks all mutation.

## Requirements

- Windows 10/11 for the primary development workflow (Linux is used in CI).
- Android Studio compatible with Android Gradle Plugin 9.0.
- JDK 17.
- Android SDK Platform 36 and current Platform Tools.
- A physical Android 16 device with GoneMAD 4.1.11 for final integration proof.

The supported application baseline is Android 8.0 (`minSdk 26`). The primary
and target contract is Android 16 (`compileSdk`/`targetSdk` 36).

## Windows setup

1. Install Android Studio and use SDK Manager to install Android SDK Platform
   36, Build Tools, and Platform Tools.
2. Configure Android Studio's Gradle JDK to JDK 17.
3. Let Android Studio create the untracked `local.properties`, or define
   `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
4. Connect a device with USB debugging for instrumented tests.

## Build and test

From PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :domain:test
.\gradlew.bat connectedDebugAndroidTest
```

The first three commands are the Phase 1 local gate. The connected test needs
an emulator or device and is not run by hosted CI.

## Repository guide

- `app/`: Kotlin, Compose, and Android tests.
- `domain/`: pure Kotlin/JVM Java 17 contracts, transforms, and fixture tests.
- `contract-fixtures/`: shared, versioned Kotlin/C# manifests and exact bytes.
- `docs/tech-spec.md`: authoritative implementation and product specification.
- `.github/workflows/android.yml`: Linux compile, lint, and JVM test checks.

## Current implementation

Version 2.0 adds a launcher library browser backed by MediaStore, album artwork,
folder filters and refresh controls, persistent normalized search, dense song
lists, an always-dark purple palette, and alphabetical song/album/artist/genre/playlist views. Long-press
starts global selection; Select all, Deselect all, Select in-between, and Invert
operate on the current filtered list. Song menus and bulk Options open the
Add/Remove membership editor.

The editor reads and writes the same playlist files selected through Android's
Storage Access Framework. A playlist is emphasized only when it contains every
selected song. Add appends only missing paths, Remove deletes every matching
occurrence, and each target is rewritten once through the existing backup,
reread, verification, rollback, and recovery coordinator. New playlists can be
created from the editor. Playlist browsing also supports rename, delete, and
static ALL/ANY membership recipes with positive or negative rules and explicit
reruns.

See [the 2.0 product and implementation plan](docs/shmemplaylist-2-plan.md) for
the current interaction contract. The [technical specification](docs/tech-spec.md)
and [Phase 7 device proof](docs/phase-7-device-proof.md) retain the original
share-flow safety gates and physical-device verification guidance.
