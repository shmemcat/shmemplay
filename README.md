# Shmemplaylist

Shmemplaylist is a native Android companion for GoneMAD Music Player. Its MVP
will let a user share the playing file, inspect membership across M3U/M3U8
playlists, and safely add or remove the track from several playlists. GoneMAD
remains the music player.

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

## Current phase

Phase 7 moves the implemented transaction machinery toward user-enabled real
multi-playlist Add and Remove operations. Before enabling writes, the app must
have a persisted writable SAF grant, a successful disposable provider
capability test, exact track resolution, eligible canonical targets, sufficient
backup capacity, no active operation, and no pending recovery. Enabling real
writes is an explicit user choice with a clear warning; every operation still
requires confirmation, and material changes found by the mandatory pre-write
reread require reconfirmation.

Phase 5 continues to provide persisted SAF access and automatic read-only
membership scanning. Phase 6 established the disposable-playlist transaction
model; its device-proof checklist remains historical and useful as a regression
and fault-injection matrix.
The user can select the GoneMAD playlist directory, persist and revalidate its
SAF grant, discover direct-child M3U/M3U8 documents, and explicitly authorize a
disposable create/write/reread/rename/update/delete/cleanup capability test.
After exact track resolution, the app scans playlists and shows Add and Remove
views, membership and duplicate counts, search, relevant/all filtering,
containing-first ordering, selection controls, warnings, and progress. Real
actions remain disabled until the user enables them and all operation-time
safety gates pass.

Android 16 device testing should continue to exercise provider capabilities,
persisted access after process recreation, membership, duplicate counts,
rotation, journaling, exact-byte backups, reread/parse/semantic verification,
rollback, startup recovery, concurrency-safe undo, and GoneMAD refresh behavior.
For Phase 7, results are reported per target as changed, skipped, failed,
restored, or recovery-required. Rollback/recovery status and eligible Undo stay
visible; optional auto-return to GoneMAD occurs only after an uncomplicated
success. The primary screen keeps only essential actions visible and places
infrequent settings, diagnostics, and history in a minimal overflow menu.

See [the Phase 7 device proof](docs/phase-7-device-proof.md) for rollout,
reconciliation, and evidence requirements. The
[Phase 6 device proof](docs/phase-6-device-proof.md) is preserved unchanged as
historical/regression guidance.

See [the technical specification](docs/tech-spec.md) for safety gates and the
complete delivery sequence.
