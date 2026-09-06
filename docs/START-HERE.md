# shmemplay development handoff

> **Current update, September 6, 2026:** A native player implementation now exists. Read [player-implementation.md](player-implementation.md) for implemented features, passing checks, installed debug build, provisional boundaries, and outstanding device acceptance. The repository-state and what-is-built sections below are the historical September 5 baseline, not the current implementation inventory.

Prepared September 5, 2026, after the repository/folder rename. This is the entry point for a new coding chat. The user approved the player design and wants to proceed to native implementation; do not restart the design interview or substitute another mock for building the app.

## Read these first

1. [music-player-plan.md](music-player-plan.md): the confirmed player behavior and development sequence.
2. [Approved prototype and screenshots](prototype/README.md): the exact interaction mock and the rounded outline icon style the user wants preserved.
3. [browser-2-plan.md](browser-2-plan.md): earlier browser/selection behavior to retain where the player plan does not supersede it. Its implementation-status paragraphs are historical, not a reliable inventory of today's code.
4. [tech-spec.md](tech-spec.md), [contract-fixtures](../contract-fixtures/README.md), and the actual operation/storage code: existing M3U compatibility, transactional writes, recovery, and cross-app contracts.

The latest user decisions in the player plan override older no-playback, incoming-share, one-action-menu, two-entry-point, system-theme, and snapshot-only restrictions. Screenshots are design references, not instructions to restore crossed-out features. The prototype demonstrates behavior; native code and the written requirements determine correctness. The no-scroll Now Playing requirement came after the mock and must be implemented even though its archived screen can scroll.

## Why the user is building this

Emily wants to replace GoneMAD with a local music player that combines Musicolet's independent rich queues with direct editing of the actual phone M3U files. MusicBee and Emily's Shmembee plugin synchronize the playlists; copying the synchronized files to the phone is currently manual. Importing/exporting roughly 88 playlists individually is not an acceptable workflow. Native queues and live recipes stay local; explicit M3U snapshots are the exchange format. Large libraries include Korean, Chinese, Thai, and other scripts; 30,000 songs is the performance target.

## Repository state and compatibility

- Local checkout: `D:\My Documents\Code\shmemplay`. GitHub: `git@github.com:shmemcat/shmemplay.git`.
- Branch at handoff: `main`; latest implementation commit: `120b9bc` (project/source rename). `984ba04` saved the player plan, references, branding, and icon. These commits were created locally; inspect remote status before assuming they were pushed.
- The folder rename is complete. Use the new directory for commands; references to the old checkout in past tool logs/build reports are historical.
- Native source namespace: `io.github.shmemcat.shmemplay`; both app and domain source/test directories were moved accordingly.
- Android `applicationId` deliberately remains `io.github.shmemcat.shmemplaylist`.
- Manifest launcher alias deliberately remains `io.github.shmemcat.shmemplaylist.MainActivity`, targeting `io.github.shmemcat.shmemplay.MainActivity`, so existing shortcuts retain their component identity.
- Keep `shmemplaylist.db`, existing shared-preference names/keys, backup/journal formats, and `Shmemplaylist Companion Test.m3u`. These are compatibility names, not missed branding edits. Use the same signing key for an in-place update.
- The purple/yellow record launcher asset is already in `app/src/main/res/drawable/ic_launcher.xml`; design source/preview are in `docs/design/`.
- The native manifest no longer registers the app as an incoming audio-share target. Legacy intake source and routing still exist and can be retired while preserving the reused M3U backend.
- Git authentication worked after configuring this repository's `core.sshCommand` to `C:/Windows/System32/OpenSSH/ssh.exe`, using the existing Windows SSH agent. The bundled Git SSH client did not authenticate. This setting is local, not committed; adjust it for other machines.

## What is actually built

The installed/native app is still a Kotlin/Jetpack Compose music browser and playlist editor. Native music playback is not implemented. The HTML prototype is not an Android app and must not be shipped as a WebView replacement.

Existing native functionality includes MediaStore music browsing, folder inclusion settings and refresh, playlist discovery, search normalization, album artwork, long-press/bulk selection, advanced filtered-list selection, M3U add/remove/create/rename/delete, flat ALL/ANY snapshot recipes with saved reruns, settings dialogs, parent-list scroll restoration, and script-aware fast scrolling. The native membership editor still uses a full-screen layout; converting it to an in-app modal is remaining player work.

Native player work still includes persisted independent queues, Media3 playback/service/session integration, revised navigation and icons, three-column album grid/list toggle, Now Playing, queue editing/shuffle/end policies, headset settings, nested/live recipes and refresh, expanded metadata/tag/file operations, and measured large-library performance. Some visual elements exist only in the mock.

## Source map

All app paths below are beneath `app/src/main/java/io/github/shmemcat/shmemplay/`.

- `MainActivity.kt`: launcher entry, permissions, lifecycle, browser/legacy-intake wiring. Extract player responsibilities rather than growing all playback logic here.
- `ui/LibraryBrowserApp.kt`: current browser navigation, projection/filter/grouping, keyed LazyColumn rows, selection, fast scrollbar, settings, membership editor, and rule UI. Much of the list-wide projection work is still in composables.
- `playlists/LibraryBrowserViewModel.kt`: library/playlist state, MediaStore observer, mutations, snapshot recipe persistence/reruns, create/rename/delete paths.
- `tracks/MusicLibrary.kt`: MediaStore library metadata, search normalization, artwork/cache work. Profile full-image decoding and repeated expensive projection work.
- `tracks/AudioLibraryRepository.kt`: bounded shared-track identity lookup, not the full browser library repository.
- `tracks/LibrarySelection.kt` and `tracks/FastScrollIndex.kt`: existing selection and multilingual jump-index logic to preserve.
- `playlists/PlaylistLibraryScanner.kt` and `PlaylistMembershipScanner.kt`: file contents, canonical identity, membership.
- `operations/MultiTargetOperationCoordinator.kt`, related journals, `storage/`, `persistence/`: verified file writes, backups, rollback/recovery, Room database.
- `ui/ShmemplayApp.kt`, `playlists/PlaylistCoreViewModel.kt`, `intake/`: legacy share/editor flow; reuse backend behavior while retiring intake-specific UI.
- `domain/src/main/kotlin/io/github/shmemcat/shmemplay/domain/`: pure parser/writer/path/operation contracts. `ManyTrackPlaylist.kt` has the existing multi-track planner and flat recipe evaluator; add a nested/live model deliberately without breaking V1 contracts.
- Tests: `domain/src/test/`, `app/src/test/`, `app/src/androidTest/`. CI command is in `.github/workflows/android.yml`.

## Decisions that must not be lost

- Always dark purple. Keep the approved mock's rounded outline icons, rounded stroke ends, and substantial visible size. Use native vector/Compose assets with equivalent geometry; existing text-glyph icons are not the approved final icon system.
- Bottom tabs: Queues, Now Playing, All Songs, Albums, Artists, Genres, Playlists. Gear at top right only. No mini-player and no separate global Search tab.
- Now Playing never scrolls. Shrink the artwork to fit the available height; preserve all controls and navigation, system insets, and usable touch targets.
- Albums default to three columns, with a list/grid toggle. Artist, genre, and playlist lists use simple category icons, not album art.
- Keep category counts, songs' total hours, dense readable rows, a small gap above the first row, notification-bar clearance, and keyboard/search spacing.
- One library search persists across categories/details until X, suspended on Queues and Now Playing. Queue search is separate. Advanced selection always uses the currently filtered list, not the whole source list.
- Selection starts with long-press and survives navigation/search. Hide the selection bar and dismiss its popup when the final globally selected song is deselected. No explanatory range-action helper sentence.
- Playlist membership becomes a modal everywhere. Fully containing playlists are bold and first, partial matches follow unbolded, then nonmatches; alphabetical within each tier. Adding only fills missing songs and never duplicates existing M3U entries. Preserve Add/Remove, playlist search, and the bottom Create new playlist action.
- Every library song tap creates a new snapshot queue from the complete displayed list. Tapping a filtered queue result also creates a new queue from all filtered results. Unfiltered queue taps and Resume operate in the existing queue.
- Viewing a queue and playing a queue are different states. Each remembers its current song/position. Browsing or renaming an inactive queue must not change the active audio.
- No duplicate file in a queue. Adding existing songs moves them to the requested position; play-next anchors after the current entry without restarting it. M3U additions do not inherit this repositioning behavior.
- Shuffle moves the uninterrupted current entry to the top and all others below in shuffled order. Turning it off restores the prior order while preserving whatever song/position is now playing. Reconcile additions/removals so restoration neither loses nor resurrects entries.
- Each queue owns its settings. A new queue copies settings from the most recently created existing queue, not the most recently played or viewed queue.
- Removing the playing entry immediately advances to the next. Deleting the active queue goes to the next queue in picker order. Boundary behavior is detailed in the plan.
- Queues are always snapshots. Rule outputs are explicitly Snapshot (real M3U) or Live (local nested rule definition based on current M3Us). Live source updates never alter an existing queue.
- Keep press-and-drag fast scrolling, the inset thin rail/thumb of equal width at rest, and a bigger thumb during interaction. Alphabetical lists preview a dynamic bucket without moving until release; playlist and queue views scroll continuously. Keep broad script support from native `FastScrollIndex`; the mock's labels are simplified examples.
- Retain list scroll position on returning from a playlist/album/genre detail into its parent category. Changing categories resets the relevant navigation context rather than restoring an unrelated old scroll.
- Settings remains a scrollable popup with stable bounds and contained scrolling. Preserve the fix for the previous top-edge rubber-band/flicker problem.

## Prototype caveats and unconfirmed details

The archive is an approved design reference, not production implementation. It contains 30,000 generated sample tracks and simulated audio/file operations. Do not copy its in-memory-only state, JavaScript substring search, artwork monograms, or missing-source shortcuts into native data logic. Native search must keep its multi-term/apostrophe/Unicode handling and stable identity rules.

The latest approved prototype has three-column albums and fully containing playlists pinned bold at the top. Its Now Playing layout predates the explicit no-scroll requirement. Its membership dialog lacks some classic native controls such as playlist-name search and filtering Remove to containing playlists; retain those working controls when converting the editor. Some mock dialogs close after applying, while native creation requirements call for refreshing and remaining in the membership editor. The written plan governs.

All ten original design questions were answered. These smaller details were not fully specified and should not block independent foundation work:

- Rewind/fast-forward intervals and tap/hold semantics (mock uses 10-second steps).
- Complete sort choices (mock demonstrates title, artist, album, duration, reverse).
- Exact unwanted-autoplay settings and device-specific headset click timing.
- Final-queue removal/no-successor boundaries, stop-after one-shot details, and unsupported metadata/tag-write formats.
- The unlabeled circular-arrow control and contents of Musicolet's Simple repeat mode are not established by the screenshots; do not invent functionality for them.

Where needed, retain explicit, documented provisional defaults from the plan/mock and ask a focused question only when a real implementation decision depends on it. Do not relabel these defaults as confirmed user choices.

## Build and verification

On this Windows machine the Android SDK is at `C:\Users\emily\AppData\Local\Android\Sdk`. Use JDK 17 and the committed Gradle wrapper/version catalog. The project uses compile/target SDK 36 and min SDK 26. Verify current Media3 APIs/dependencies against official Android documentation when adding them.

```powershell
Set-Location 'D:\My Documents\Code\shmemplay'
$env:ANDROID_HOME = 'C:\Users\emily\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

Last full verification was after the source rename, before the folder itself moved: all 55 JVM tests passed (24 domain, 31 app), lint passed, and both app/test APKs built. The manifest was checked for the retained application ID and launcher alias. Room version remains 3 with identity hash `540416fce38464853ba678b9fda3281e` (legacy hash `0def19864b01756c4ae74df1011f3d8c`), unchanged by the rename. The folder move alone was not followed by another full build. Future persistence changes need their own real migrations and upgrade tests.

Mock browser checks passed for virtualization with fewer than 20 rendered song rows, deep/fast scrolling, selection/filter behavior, queue move/dedup/shuffle/restore, independent viewed/active queues, live versus snapshot behavior, membership ordering, and 320/360px layouts. Those checks do not prove native playback or 30k-song performance.

The user's device was a Samsung SM-S928U on Android 16. Rediscover connected devices at runtime. Install updates over the existing app with matching signing identity; do not uninstall or clear its data as part of a routine test. In the previous setup, `connectedDebugAndroidTest` removed the target app during its lifecycle, so do not run that task against the user's real installation without arranging an isolated test setup. `assembleDebugAndroidTest` only builds the test APK and was part of the passing checks. If the phone is locked, let the user unlock it; do not try to dismiss/bypass the keyguard.

Use fixture/test files for write tests. The player feature request is not permission to delete or retag actual user music during development. The application's existing explicit write enablement, verified M3U operations, and recovery handling remain relevant. The old unchecked device-proof checklist is regression guidance; it is not a reason to abandon otherwise authorized implementation.

## Suggested first implementation pass

Follow the six phases in the player plan. Start by inspecting current persistence and splitting reusable library/playlist state from browser-only navigation, then implement a pure queue model/repository with persisted stable identities, source snapshots, viewed versus active state, per-queue policy inheritance, and focused tests of the confirmed queue invariants. Add Media3-backed service/session playback next, followed by the approved screens and remaining functionality. Build in reviewable increments and update the plan with completed work, outstanding device verification, and newly resolved boundary decisions. Do not stop at another design proposal.

## Paste into the new chat

> Build shmemplay as a native Android music player from the approved plan. Read `docs/START-HERE.md`, `docs/music-player-plan.md`, and `docs/prototype/README.md` first, then inspect the current Kotlin/Compose repository. Use the archived mock and screenshots for the approved appearance and interactions. Preserve existing M3U behavior and installed-app data compatibility. Keep the rounded outline icons and ensure Now Playing never scrolls. The design questions are already answered; proceed with implementation in the plan's phases, test the changes, and only ask about unresolved details when they actually block progress. This is a request to build the app, not another mock.
