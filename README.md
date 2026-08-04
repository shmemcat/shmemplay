# Shmemplaylist

Shmemplaylist is a native Android companion for GoneMAD Music Player. Its MVP
will let a user share the playing file, inspect membership across M3U/M3U8
playlists, and safely add or remove the track from several playlists. GoneMAD
remains the music player.

> **Safety status:** Phase 5 adds user-selected, persisted playlist-tree access,
> a confirmed disposable provider test, and read-only membership scanning.
> Phase 6 work permits verified transactions only against the app-created,
> identity-persisted `Shmemplaylist Companion Test.m3u`. Real playlists cannot
> be modified.

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

## Current phase

Phase 6 is implemented as a disposable-playlist transaction proof and awaits
physical-device fault-injection and GoneMAD refresh validation. Phase 5
continues to provide persisted SAF access and automatic read-only membership
scanning.
The user can select the GoneMAD playlist directory, persist and revalidate its
SAF grant, discover direct-child M3U/M3U8 documents, and explicitly authorize a
disposable create/write/reread/rename/update/delete/cleanup capability test.
After exact track resolution, the app scans playlists read-only and shows Add
and Remove views, membership and duplicate counts, search, relevant/all
filtering, containing-first ordering, selection controls, warnings, and
progress. Add/Remove actions remain unequivocally disabled.

Android 16 device testing must confirm provider capabilities, persisted access
after process recreation, membership against real GoneMAD playlists, duplicate
counts, and rotation behavior. Phase 6 device testing must additionally prove
journaling, exact-byte backups, reread/parse/semantic verification, rollback,
startup recovery, concurrency-safe undo, and GoneMAD refresh behavior. No
playlist may be changed except disposable capability-test documents and the
identity-persisted companion test playlist.

See [the technical specification](docs/tech-spec.md) for safety gates and the
complete delivery sequence.
