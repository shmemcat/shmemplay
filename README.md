# Shmemplaylist

Shmemplaylist is a native Android companion for GoneMAD Music Player. Its MVP
will let a user share the playing file, inspect membership across M3U/M3U8
playlists, and safely add or remove the track from several playlists. GoneMAD
remains the music player.

> **Safety status:** Phase 1 is scaffold-only. This app does not request
> playlist access and cannot modify playlists.

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
.\gradlew.bat connectedDebugAndroidTest
```

The first three commands are the Phase 1 local gate. The connected test needs
an emulator or device and is not run by hosted CI.

## Repository guide

- `app/`: Kotlin, Compose, and Android tests.
- `contract-fixtures/`: shared Kotlin/C# contract categories; populated in
  Phase 2 after explicit contract decisions.
- `docs/tech-spec.md`: authoritative implementation and product specification.
- `.github/workflows/android.yml`: Linux compile, lint, and JVM test checks.

## Current phase

Phase 1 initializes the repository, permanent application identity
`io.github.shmemcat.shmemplaylist`, launcher scaffold, CI, fixture layout, and
the authoritative specification. No production intake, MediaStore, SAF, Room,
playlist parsing, or mutation behavior is implemented yet.

See [the technical specification](docs/tech-spec.md) for safety gates and the
complete delivery sequence.
