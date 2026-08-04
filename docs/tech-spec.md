# Shmemplaylist Phase 1 technical specification

Status: authoritative repository specification for Phase 1  
Product target: Android 16 with GoneMAD Music Player 4.1.11  
Application ID and namespace: `io.github.shmemcat.shmemplaylist`

## 1. Authority, scope, and classification

This document is the implementation authority for Phase 1 and the requirements
baseline for later phases. It consolidates:

1. the planning-session specification,
   `C:\Users\emily\.cursor\plans\gonemad_intake_spec_06bdfd19.plan.md`, which is
   authoritative when it refines or corrects the original; and
2. the 812-line original plan,
   `D:\My Documents\Code\shmembee\plans\shmembee-playlist-companion.md`, whose
   intent must remain traceable.

Requirement classifications are:

- **Retained** — same product or safety meaning.
- **Refined** — made implementation-specific without changing intent.
- **Corrected** — changed because repository or desktop-contract evidence
  contradicts the original wording; the correction and consequence are stated.
- **Device proof required** — behavior cannot be guaranteed until measured on
  the Android 16/GoneMAD 4.1.11 target.
- **Deferred** — retained behind a named later-phase prerequisite.
- **Rejected** — intentionally excluded with rationale.

Phase 1 initializes and documents the app. It does not receive production share
payloads, request media or tree permissions, parse real playlists, or mutate any
playlist. Later-phase requirements are specified here so implementation does not
silently diverge from the intended MVP.

## 2. Product purpose and success

Build a small native Android companion that gives GoneMAD a Musicolet-style
playlist-management workflow without replacing GoneMAD:

> While a song is playing, quickly see which M3U playlists contain it, then add
> it to or remove it from several playlists at once.

GoneMAD alone owns playback, queues, audio focus, MediaSession, Bluetooth and
Android Auto controls, equalization, library scanning, notifications, and
widgets. Shmemplaylist receives current-track evidence, resolves one safe track
identity, reads the M3U/M3U8 documents shared with GoneMAD and desktop Shmembee,
calculates membership and duplicate occurrences, and eventually performs
explicit, verified, recoverable add/remove operations.

Success means the end-to-end workflow is materially faster than opening and
editing playlists individually, remains interoperable with GoneMAD and
Shmembee, and never guesses an ambiguous identity.

## 3. Product boundary

### 3.1 MVP requirements

The eventual MVP shall:

- receive GoneMAD **Share → File** through `ACTION_SEND`;
- treat artist/title text only as supplemental, non-authoritative evidence;
- require the applicable audio-library permission;
- persist a user-selected SAF playlist-tree grant;
- prove provider read/create/write/rename/delete/reread/restore behavior with a
  disposable document;
- discover direct-child `.m3u` and `.m3u8` documents case-insensitively;
- resolve exactly one track or require deliberate user selection;
- remember approved aliases and revalidate them on every use;
- scan every recognized playlist and retain every matching occurrence;
- provide separate Add and Remove tabs, name search, relevant/all filtering,
  deterministic containing-first ordering, multi-selection, select-visible,
  clear-selection, and accessible membership and duplicate indicators;
- add one appended occurrence only where membership is absent;
- remove all matching occurrences by default;
- reread selected documents before mutation;
- back up exact original bytes, write, close, reread, parse, and semantically
  verify every changed document;
- compensate for partial failure with verified rollback;
- recover interrupted operations before allowing another write;
- offer concurrency-safe undo;
- report changed, skipped, failed, restored, and recovery-required outcomes;
- preserve GoneMAD and Shmembee interoperability.

### 3.2 Excluded from MVP

The following are **Rejected** from MVP because they expand the app beyond
focused external-M3U management: playback; playback-queue management or multiple
queues; equalizer, lyrics, tag editing, widgets, and Android Auto playback UI;
direct MusicBee communication; complete library or playlist browsing; arbitrary
editing/reordering; undocumented GoneMAD internals; individual duplicate
selection; arbitrary insertion positions; and automatic duplicate insertion.

### 3.3 Deferred convenience integrations

Pinned Direct Share, launcher shortcuts, a Quick Settings tile,
notification/MediaSession-assisted discovery, enhanced return-to-GoneMAD,
full editing/reordering, multi-track operations, add-at-beginning/position, and
explicit duplicate insertion are **Deferred** until the share, identity,
contract, write, rollback, recovery, and refresh gates pass. Metadata-only
shortcuts must reuse the resolver and cannot displace Share → File.

## 4. Phase 1 platform and dependency baseline

The repository files are the selected-version authority. Phase 1 uses:

| Selection | Phase 1 value | Repository evidence |
|---|---:|---|
| Application ID / namespace | `io.github.shmemcat.shmemplaylist` | `app/build.gradle.kts` |
| Minimum SDK | 26 | `app/build.gradle.kts` |
| Compile SDK | 36 | `app/build.gradle.kts` |
| Target SDK | 36 | `app/build.gradle.kts` |
| Java source/target toolchain | 17 | `app/build.gradle.kts` |
| Android Gradle Plugin | 9.0.0 | `gradle/libs.versions.toml` |
| Kotlin / Kotlin Compose plugin | 2.2.21 | `gradle/libs.versions.toml` |
| Compose BOM | 2026.06.01 | `gradle/libs.versions.toml` |
| Activity Compose | 1.12.3 | `gradle/libs.versions.toml` |
| AndroidX Core KTX | 1.18.0 | `gradle/libs.versions.toml` |
| Coroutines | 1.10.2 | selected catalog version |
| Navigation Compose | 2.9.7 | selected catalog version |
| Room | 2.8.4 | selected catalog version |
| JUnit 4 | 4.13.2 | selected catalog version |
| AndroidX JUnit | 1.3.0 | selected catalog version |
| AndroidX Test Runner | 1.7.0 | selected catalog version |
| Espresso | 3.7.0 | selected catalog version |
| Robolectric | 4.16.1 | selected catalog version |

AGP 9 supplies Android's built-in Kotlin integration; the explicitly versioned
Kotlin Compose compiler plugin is 2.2.21. Coroutines, Navigation, and Room are
selected in the catalog but intentionally
not yet wired into the Phase 1 launcher dependency graph. Their presence is a
later-phase compatibility selection, not a claim that those features exist.
Compose UI, Material 3, tooling, JUnit, Robolectric, AndroidX JUnit, Espresso,
and Compose UI test dependencies are wired by `app/build.gradle.kts`.

Primary development is Windows with Android Studio, SDK tools, Gradle, and
`adb`; Linux GitHub Actions exposes path, case, and line-ending assumptions.
Final GoneMAD and SAF claims require the physical Android 16 device. A Mac is
unnecessary for this Android-only product. React Native and Capacitor are
**Rejected** because intents, URI grants, MediaStore, SAF providers, process
lifecycle, and recovery are the critical implementation surface.

## 5. Phase 1 deliverables and exit gate

Phase 1 consists of:

- a native Kotlin/Compose/Material 3 launcher scaffold with no production
  playlist behavior;
- Gradle Kotlin DSL, committed wrapper, Java 17, and the versions in section 4;
- feature/domain package boundaries only when needed, without speculative
  abstractions;
- this exhaustive specification and documented fixture-category structure;
- README, ignore/attributes, and CI baseline as separately implemented Phase 1
  repository assets;
- one pure JVM smoke test and one practical Compose/instrumented scaffold test;
- Windows build, lint, and JVM-test verification and Linux CI configuration.

Repository and CI requirements remain part of the Phase 1 gate even though this
document-creation task performs no Git operation:

- use the existing repository and preserve its history and all unrelated user
  changes; never initialize a nested repository;
- confirm, before any release/push work, that the existing SSH origin is
  `git@github.com:shmemcat/shmemplaylist.git`;
- inspect status, history, branch, remotes, tracked and untracked files before
  scaffolding or committing, and use the current branch unless repository policy
  requires otherwise;
- commit both wrapper launchers and wrapper configuration;
- ignore `local.properties`, build outputs, machine-local IDE state, signing
  material, generated secrets, and all local SDK/device-sensitive artifacts;
- enforce UTF-8; use LF for source, Markdown, fixtures, and shell scripts while
  permitting CRLF for Windows command scripts where required;
- make fixtures/tests independent of drive letters, host separators, current
  culture, local time zone, case-insensitive filesystems, and Windows line
  endings, while comparing exact bytes where encoding or endings are contractual;
- run wrapper validation, debug compilation, lint, JVM tests, and selected
  static checks in GitHub Actions on Linux; keep emulator/device tests separate;
- cache dependencies only, never secrets or machine-local Android configuration;
- push only after equivalent Windows build/lint/test checks pass, report the
  commit and branch, never force-push, and create no release/tag/signing asset.

Phase 1 exits only when the debug APK builds, lint and JVM tests pass, CI is
configured, history/origin remain intact, no secret or local SDK/device data is
tracked, and this document has complete source traceability. The app must perform
no playlist mutation. The initial reviewed commit must ultimately be present on
the existing SSH origin, but committing/pushing is outside this document-only
task. Release artifacts, tags, signing keys, Play Console work, and force pushes
are **Rejected** from Phase 1.

## 6. Architecture baseline

Use one application module, organized by capability:

```text
app/src/main/java/io/github/shmemcat/shmemplaylist/
  intake/ tracks/ playlists/ operations/ storage/
  persistence/ recovery/ settings/ diagnostics/ ui/
contract-fixtures/
  parser/ normalization/ writer/ checksums/ operations/ gonemad/
docs/tech-spec.md
```

The app is layered. Compose and ViewModels do not mutate documents directly.
Platform-neutral domain behavior is isolated from Android URI/document access.
Both repositories share specifications and fixtures, never .NET assemblies,
source dependencies, MusicBee APIs, Windows/MTP transports, desktop SQLite, or
Android Room databases.

Later implementations shall provide interfaces equivalent to
`SharedTrackIntake.receive`, `UriEvidenceProbe.probe`,
`AudioLibraryRepository.findCandidates`, `TrackIdentityResolver.resolve`,
`PlaylistRepository.listPlaylists/readPlaylist`, `M3uParser.parse`,
`TrackMatcher.matches`, `MembershipService.scan`, `OperationPlanner.plan`,
`PlaylistOperationCoordinator.apply/undo/recover`,
`OperationJournalRepository`, and `BackupRepository`. Each contract must state
models, dispatcher, cancellation behavior, errors, and ownership/closure of
cursors, streams, and descriptors.

## 7. Setup and playlist-tree contract

First-run setup shall explain the product boundary and need for audio-library
and playlist access; request `READ_MEDIA_AUDIO` on Android 13+; remain limited
and block mutation if denied/revoked; launch `ACTION_OPEN_DOCUMENT_TREE`; suggest
but never assume `Internal storage/gmmp/playlists`; persist the granted flags;
verify the grant in `persistedUriPermissions`; and report ready, limited, or
blocked state.

With explicit confirmation it shall create a uniquely named disposable
document, write known bytes, close, reread/compare, rename/locate, update,
delete, verify deletion, and record unsupported capabilities. It shall then
discover recognized direct-child playlists. Diagnostics show redacted tree
shape/display name, grant state, count, capabilities, last scan, last verified
write, pending recovery, and contract versions.

Documents are identified by provider URI and document ID where available, not
display name. Record name, URI, document ID, MIME, size, and modified time when
available; size/time are cache hints only. Handle vanished, renamed, newly
created, duplicate-name, and inaccessible documents. An external rename is
delete/create unless stable identity is proved. Track existence independently
from semantic checksum.

## 8. GoneMAD share-intent and device-proof contract

Share → File is mutation-authoritative. Register `ACTION_SEND` with `audio/*`.
Add a broad MIME filter or `ACTION_SEND_MULTIPLE` only if measured GoneMAD
behavior requires it and validation prevents unrelated shares.

Handle cold and warm delivery, launcher starts, recreation duplicates, hostile
or malformed intents, `content://`, measured legacy `file://`, missing/wrong
MIME, `EXTRA_STREAM`, `ClipData`, supplemental `EXTRA_TEXT`, temporary grant
expiry, and initially readable URIs that fail after process death.

Capture, with default redaction: action/type/flags/categories; URI
shape/authority; ClipData count and consistency; text presence/shape;
reliable caller/referrer; resolver MIME; display name/size; safe provider
columns and errors; stream and descriptor availability, length, seekability;
stream-derived tags/duration; and direct MediaStore ID, volume, relative path,
name, and metadata.

Never use `MediaStore.DATA`, convert `content://` to `File`, infer a path from
URI text, assume a share grant is persistable, copy/hash complete audio by
default, probe on the main thread, or log unredacted paths/tags/tokens.

All payload and refresh statements are **Device proof required** on Android 16
and GoneMAD 4.1.11. Test used formats, Unicode and missing tags, duplicate names
and metadata, internal/removable storage as applicable, stopped/background/open
delivery, immediate/task-switched/recreated access, text share, and odd MIME.
Exit requires evidence that uniquely identifies a track or routes to explicit
resolution; stream access alone is insufficient.

## 9. Track evidence and deterministic identity

`SharedTrackEvidence` shall carry URI/authority/MIME/name/size, artist/title/
album/duration/track/disc, direct MediaStore ID and volume, provider relative
path, supplemental text, and typed provenance. `TrackCandidate` carries
MediaStore ID/volume, name/path, MIME, size, duration, tags, modified date, and
normalized M3U forms. `ResolvedTrackIdentity` carries the chosen candidate,
canonical volume-relative and generated paths, evidence, resolver version, and
approval source. `ApprovedAlias` stores only a stable evidence fingerprint,
target identity/path, material validation fields, timestamps, and invalidation
reason. Results are unique, ambiguous, not found, permission required, URI
expired, or invalid.

Resolution evaluates strongest evidence and succeeds only with one valid
candidate:

1. direct MediaStore ID plus volume;
2. provider volume/relative path mapping to one item;
3. exact normalized storage-relative path;
4. revalidated approved alias;
5. unique filename constrained by parent/path suffix;
6. unique display name constrained by duration and strong tags;
7. strong artist/title/album/duration agreement;
8. explicit user choice.

The implementation contract must specify NFC normalization; slash/dot handling;
volume identity; separate case comparison; justified duration tolerance;
unknown metadata; size confidence; narrowing versus proof; display-only stable
ordering; zero/multiple/stale behavior; and alias invalidation after deletion,
rename/path, volume, size/duration/material metadata, or provider identity
changes. Manual resolution shows details, candidates, evidence/discrepancies,
supports cancel, requires deliberate selection, separately records approval,
and never chooses the first candidate. Complete-file hashing is **Deferred** as
an explicit privacy/performance decision.

## 10. Versioned M3U and path contracts

### 10.1 `m3u-parser-v1`

The parser accepts a caller-owned stream and leaves it open; discovery handles
extensions. `displayName` is
nonblank and `backingName` may be null. Desktop v1 uses throwing UTF-8 with BOM
detection, so recognized UTF-16/32 BOM input may be accepted. This **Corrects**
the original claim that established behavior is strictly UTF-8 plus optional
UTF-8 BOM. Android must fixture exact v1 compatibility or name a new
strict-UTF-8 contract.

Line parsing accepts CR, LF, and CRLF plus an unterminated final line, numbers physical lines from 1, trims before
classification and retained source value, ignores blank and ordinary comments,
recognizes `#EXTINF:` case-insensitively, parses signed decimal duration before
the first comma (invalid/overflow becomes null), retains a nonempty post-comma
title without another trim (`null` for no comma or terminal comma), carries
EXTINF across blanks/comments, applies last
EXTINF, attaches it to the next path, and discards dangling metadata. It
preserves path order, duplicates, physical line, trimmed source path, normalized
path, and optional metadata. With `preserveExtendedInfo=false`, EXTINF is
consumed but omitted from records. A `#`-starting path is not representable in v1.
Any production path that normalizes empty is a typed parser error.
The retained path is not byte-raw. Malformed encoding, provider failure,
required-name failure, limits, and cancellation are structured per-playlist
errors and never abort unrelated scans.

### 10.2 `phone-path-v1`

In order: reject null/blank; trim; replace `\` with `/`; NFC-normalize; repeatedly
remove exact leading `./`; remove one `/storage/emulated/0/`
case-insensitively; split/drop empty segments; drop `.`; resolve `..` by popping
or discard above root; join; remove a remaining leading slash.

V1 preserves case; comparison is separate; does not alias `/sdcard/`; does not
decode percent escapes or interpret URI schemes; does not identify removable
roots; collapses separators; silently drops above-root traversal; strips
arbitrary absolute roots; and can normalize nonblank input to empty.
Volume-aware matching, `/sdcard/`, removable roots, URI encoding, and safer
traversal require a fixture-backed `phone-path-v2`; they cannot silently change
desktop v1 checksums.

### 10.3 Comparison and checksum

Comparison policy is independent from normalization and explicitly named at
each boundary. Membership never conflates removable volumes. The semantic
pipeline is ordered parsed path records → selected normalization → reject empty
normalized production entries → literal LF join with no trailing LF → UTF-8 →
SHA-256 → lowercase hex. Empty input hashes empty bytes; order and multiplicity
matter; the checksum utility itself does not normalize; existence is separate.

## 11. Writer/editor decision gate

Existing deterministic writer behavior rejects null sequences and null/blank/
CR/LF entries; preserves order and duplicates; trims and slash-normalizes;
does not NFC- or dot-normalize; joins with LF; emits UTF-8 without BOM and a
default trailing LF for nonempty content; emits zero bytes when empty; applies
one normalized optional prefix using case-insensitive existing-prefix
detection; and emits only paths, dropping comments, blanks, `#EXTM3U`, and
EXTINF.

**Resolved in Phase 2 — canonical deterministic editing.** Mutation is eligible
only for `canonical-gonemad-profile-v1`: valid UTF-8 without BOM, LF-only,
trailing LF when nonempty, and path-only nonblank records. Every path must
already be a canonical absolute primary path below `/storage/emulated/0/`.
Deterministic `m3u-writer-v1` output for the parsed path sequence must equal the
input bytes exactly. A zero-byte empty playlist is canonical and writable.

Parseable noncanonical playlists remain readable for membership inspection, but
mutation eligibility returns typed reasons (encoding/BOM, line endings,
trailing LF, blank/non-path records, noncanonical path, or writer mismatch).
This avoids silently canonicalizing unrelated records. The selected output plan
always emits canonical absolute primary paths. Removable-volume output is not
representable by this profile and remains mutation-ineligible pending a
separately versioned, fixture-backed profile.

Regardless of the choice: never write unaffected documents; preserve unrelated
entry order and duplicate multiplicity; reject line-break injection; choose an
explicit output style; and prove GoneMAD/Shmembee parsing. Style detection must
define absolute primary, volume-relative, music-root-relative, mixed/tie/
single/empty behavior, ignore comments/EXTINF, require a configured ambiguous
default, record the generated path before confirmation, and verify internal and
removable storage separately.

### 11.1 Phase 2 implementation boundary

The Java 17 `:domain` module contains only Kotlin/JDK code. It implements the
versioned parser, path normalization and explicit volume-aware comparison,
checksum, writer, profile validator, canonical output plan, occurrence indexes,
add-one-if-absent, and remove-all transforms. Root `contract-fixtures` manifests
and exact bytes are loaded as `:domain` test resources. The Android app has a
compile dependency on this module but does not invoke storage or mutation.
`PhaseOneSafety` remains mutation-disabled.

## 12. Membership, add, and remove

For each playlist, open/parse off-main, preserve order/duplicates, normalize by
the declared contract, compare to volume-aware identity, record every matching
index, compute checksum plus existence, and return warnings. Cache by document
identity and optional metadata/checksum only for display; selected documents are
always reread.

Add appends one generated occurrence to each selected eligible absent
membership. Existing or concurrently added membership is an explicit skip,
never a duplicate. Unsafe output representation blocks that playlist.
Intentional duplicate insertion is **Deferred**.

Remove deletes every matching occurrence by default, preserves every remaining
entry’s order and multiplicity, reports count, and cleanly skips already-removed
state. Individual occurrence choice is **Deferred**. Companion remove-all is an
intentional policy even if GoneMAD removes one selected occurrence itself.

## 13. Safe write, journal, recovery, and undo

SAF providers do not guarantee atomic sibling replacement. Use a compensating
transaction. Before writing: reread every selected document; compare existence
and semantic checksum; recompute; reconfirm only if targets, counts, paths, or
outcomes materially changed; store exact original bytes privately; record prior
existence; durably persist operation/target order/checksums/backups and pending
state; check backup capacity where possible; and serialize app operations.

During writing use one operation ID, touch only selected documents, journal
before/after irreversible transitions, close/flush before reread, stop at first
unrecoverable failure, retain backups through undo policy, and make cancellation
restore-or-recover rather than abandon. After every write, reopen, parse, and
verify expected ordered semantic checksum and existence.

On failure restore every possibly changed document in reverse order from exact
bytes, verify at the level selected by the Phase 2 editor contract, retain
backups, block writes if uncertain, and report each outcome.

Room eventually stores operation timestamps/action/contract versions, redacted
track identity and approval source, ordered document identities, existence,
checksums, backup integrity/location, generated path, occurrence positions,
counts, per-step results/errors, and states from pending through recovery,
rollback, undo, and failure. It also stores tree/capabilities, settings, aliases,
schema/contracts, and non-authoritative scan cache. Migrations, retention,
cleanup, history clearing, and tree-change deletion semantics must be explicit.

At startup and before writes, recover every nonterminal operation by revalidating
tree/backups/targets, rereading, classifying original/expected/unknown/missing,
completing verification or safe restoration, verifying, and marking terminal or
recovery-required. Recovery is idempotent for process death at every transition;
required recovery blocks mutation.

Undo performs fresh reads. It precisely inverts when current state equals the
result; otherwise it preserves unrelated later edits and inverts only when
unambiguous. It never blindly restores an old whole-file backup. Ambiguous
identical additions or unsafe reinsertion requires review. Undo uses the same
journal/backup/verify/rollback/recovery protocol and reports partial eligibility.

## 14. GoneMAD refresh contract

Existing evidence (not a new device guarantee) says GoneMAD discovers external
M3U/M3U8 after rescan, accepts real phone-relative paths, preserves order and
duplicates, may rewrite paths as `/storage/emulated/0/...`, and persists in-app
edits to the backing file.

Immediate detection, reopen/reload, required rescan, open-screen cache, active
playlist behavior, stale overwrite, foreground/background/stopped behavior,
add/remove/rollback/undo, resolvable `.m3u8`, and documented refresh intents are
all **Device proof required**. Until proved, say:

> Updated N playlists. Reopen or rescan the playlist in GoneMAD if its contents
> do not refresh immediately.

Record phone/build/app, volume, URI/path shape, sequence, latency, and outcome.
Never promise automatic refresh.

## 15. UI and state requirements

Setup explains scope/permissions, requests media permission, selects and tests
the tree, reports count/readiness, and links diagnostics. Membership shows
known track fields and evidence, Add/Remove with last-tab restoration, scan
progress/retry, search/filter, deterministic containing-first sort, accessible
indicators, duplicate counts, multi-selection/select-visible/clear, warnings,
and disabled-action reasons. Hidden selections remain selected, and UI always
shows total selected count.

Resolution appears only when exact resolution fails or an alias invalidates,
shows evidence/candidates/discrepancies, allows remembering, and cancels without
mutation. Changed-state confirmation shows target/count/path differences and
appears only when meaning changed. Applying prevents duplicate submission,
distinguishes writing/verifying/restoring, and never lets navigation abandon
durable responsibility. Results list changed/skipped/counts/rollback, eligible
Undo, Return to GoneMAD, and redacted diagnostics. Settings expose tree,
permission, output style, MVP-preserving duplicate/removal settings,
auto-return, history, aliases, recovery, exports, versions, capabilities, scan,
and verified-write state.

States include setup/permission required, ready, receiving, probing, resolving,
resolution required, scanning, membership ready, changed confirmation,
applying, verifying, rolling back, succeeded, failed, and recovery required.
Mutation requires ready setup, permission, writable tree, resolved identity,
current snapshot, eligible selection, no active operation, and no recovery.
Rotation/recreation restore safe state; expired share access requests a new
share. Auto-return is opt-in and forbidden after attention-worthy skips,
warnings, failure, rollback, or recovery.

## 16. Errors, limits, security, and privacy

Typed user/diagnostic errors cover malformed share, URI expiry/denial,
permission denial/revocation, MediaStore failure, zero/ambiguous candidate,
invalid alias, revoked tree, missing provider capability/document, malformed
encoding/M3U, unsupported path style, stale precondition, backup space,
write/close, reread/parse/verification, restoration, and recovery.

Implementation must set testable safe-fail limits for probe duration, metadata
reads, file/line/entry/playlist counts, diagnostics, backup retention, and
operation timeouts. Never truncate content that could later be written.

Treat every intent, URI, MIME, name, metadata field, M3U line, and provider as
untrusted. Bound resources, validate schemes, prevent CR/LF injection, never
execute M3U content, use private backups, avoid broad storage permission, redact
URI tokens/full paths/tags/names by default, require explicit unredacted export,
retain no audio bytes, close resources, and support history deletion/alias
forgetting.

## 17. Fixtures and verification strategy

Every fixture manifest names contract version, exact input encoding/bytes,
parsed values/line numbers, normalized values, comparison, checksum, output
bytes or error, and rationale.

- Parser: UTF-8/BOM/malformed and UTF-16/32 decision cases; LF/CRLF/unterminated;
  whitespace/comments; all EXTINF forms and state transitions; order,
  duplicates, Unicode, source lines, and `#` paths.
- Normalization/matching: relative/absolute/`./`, slash forms, emulated root and
  `/sdcard/`, NFC, dot/traversal/empty, removable volumes, percent literals,
  rejected URI schemes, case policy, and same text on different volumes.
- Writer/editor: empty/one/many/duplicates/Unicode, whitespace/slashes,
  invalids/injection, trailing newline, prefix, exact bytes, canonical loss,
  and source-preserving cases if selected.
- Checksum: known empty, order/duplicates/Unicode/no-trailing-LF, normalized
  equivalence, NFC, and absent-versus-empty.
- Operations: add/skip, remove duplicates, preservation, stale no-op/review,
  failures at every position, mismatch/restore, process death at every journal
  state, and all undo concurrency/document cases.
- GoneMAD: before/after rewrite equivalence, real M3U/M3U8, path styles,
  duplicates/Unicode, replacement/display/backing rename, and companion
  add/remove/rollback/undo followed by rescan/autosave.

Pure JVM tests cover all contracts plus intent extraction, evidence provenance,
candidate ambiguity, alias lifecycle, membership, transformations, planning,
journal/recovery, undo, and redaction. Matching C# and Kotlin fixtures cover
GoneMAD-, companion-, and Shmembee-generated/rewritten playlists, duplicates,
Unicode, empty, separators, and equivalent relative/absolute paths. Divergence
must be named/versioned.

Instrumented tests cover cold/warm intake, stream/ClipData and grants,
permissions, MediaStore, SAF persistence/capabilities, transaction and Room
durability, process death, selection/filter/reconfirmation, recovery UI, and
accessibility.

The manual matrix records phone/Android/GoneMAD/app builds, volumes/tree,
payload/format/metadata, identity, refresh, duplicates, provider capability,
and failure/recovery. It exercises at least 40 playlists, bounded large cases,
Unicode/punctuation, internal/removable storage, concurrent edits,
Shmembee synchronization, revocation, low space, every write-stage death,
malformed playlists, missing/renamed tracks, duplicate names/occurrences,
rotation/recreation, and every supported Android version.

## 18. Delivery phases and gates

1. **Phase 1 — initialization/spec:** scaffold and documentation only; no
   mutation.
2. **Phase 2 — shared contracts/domain:** resolve the editor gate; version and
   fixture parser/editor/normalization/comparison/checksum/operations; implement
   pure Kotlin and cross-repository tests.
3. **Phase 3 — intake diagnostics:** minimum measured filters, redacted
   cold/warm probing, full device payload matrix; no tree mutation.
4. **Phase 4 — identity:** required MediaStore permission, deterministic
   candidates, explicit resolution, Room aliases/invalidation; no mutation.
5. **Phase 5 — SAF/read-only membership:** grant/capability proof, discovery,
   parsing/matching and complete read-only UI; only disposable test document.
6. **Phase 6 — disposable transaction:** writes restricted by identity and test
   mode to `Shmemplaylist Companion Test.m3u`; journal, backup, verification,
   rollback, recovery, undo, failure injection, and refresh proof.
   The local implementation uses an app-created and persisted document identity,
   not display-name matching, and keeps all other discovered playlists read-only.
   Physical Android 16 provider, process-death, failure-injection, undo, and
   GoneMAD refresh evidence remains required before this gate is complete.
7. **Phase 7 — multi-playlist MVP:** enable real writes only after reviewed
   gate; batch semantics, reconfirmation, outcomes, settings, diagnostics, and
   Shmembee reconciliation.
8. **Phase 8 — hardening/release:** scale/platform/lifecycle/security,
   retention/migrations/accessibility/performance, supported-device matrix,
   reproducible release instructions with external secrets.
9. **Phase 9 — convenience:** evaluate deferred shortcuts, assisted discovery,
   richer editing, and multi-track operations using all existing safety gates.

No later mutation phase starts while an earlier safety gate is unresolved.

## 19. MVP acceptance criteria

The MVP is accepted only when Share → File works on the target; URI access does
not require a path; permission is enforced; identity is unique or deliberate;
text alone cannot authorize mutation; aliases revalidate; tree and capabilities
persist; discovery, membership, and duplicates are correct; accessible Add/
Remove/search/filter/selection work; add avoids duplicates; remove-all works;
unselected documents remain byte-identical; selected content meets the chosen
editor contract; every write has backup and semantic verification; stale state
recomputes/reconfirms only on meaning change; rollback/restoration/recovery are
verified; undo preserves concurrent edits; GoneMAD and Shmembee consume and
reconcile output; shared fixtures pass; and the workflow is materially faster.

## 20. First implementation proofs

After Phase 1 and Phase 2 contracts:

1. capture the exact Android 16/GoneMAD 4.1.11 Share File payload and prove
   deterministic identity or explicit resolution; and
2. prove read, exact-byte backup, write, close, reread, parse, verify, restore,
   and verify-restore on one disposable M3U through the selected SAF provider.

Read-only membership follows. Real writes remain compile-time or runtime gated
until intake, identity, contracts, refresh, verification, rollback, and recovery
phase exits all pass.

## Appendix A. Decision register

- **Corrected:** desktop parser v1 is not accurately described as strict UTF-8
  plus optional UTF-8 BOM; BOM detection can accept recognized UTF-16/32.
- **Corrected:** source “raw source paths” means trimmed retained source values,
  not byte-raw records.
- **Corrected:** original identity ordering overvalues a persisted shared
  document URI; temporary share URIs are not durable identity. Direct MediaStore
  ID+volume and provider path mapping precede path/alias/manual tiers.
- **Refined:** audio permission is required for the selected resolver on Android
  13+, not merely an optional fallback.
- **Refined:** app name/ID is Shmemplaylist /
  `io.github.shmemcat.shmemplaylist`, not the conceptual repository/app names.
- **Refined:** discovery is direct-child only until recursion is explicitly
  designed.
- **Refined:** visual emphasis must be accessible and cannot rely on bold.
- **Refined:** rollback includes durable journaling, exact-byte backup,
  per-document transitions, next-launch recovery, and write blocking on
  uncertainty.
- **Refined:** auto-return is opt-in and suppressed for any attention state.
- **Device proof required:** exact GoneMAD payload, URI lifetime, broad MIME
  need, file URI behavior, storage formats, and all refresh/cache behavior.
- **Deferred:** convenience integrations and advanced editing listed in 3.3.
- **Rejected:** playback/general-player scope, undocumented internals, hybrid
  frameworks, broad storage permission, unsafe path inference, and real writes
  before gates.
- **Unresolved Phase 2 gate:** canonical rewrite versus source-record-preserving
  editing. Phase 1 makes no selection.

## Appendix B. Source-plan traceability

This appendix maps every heading, numbered requirement, and bullet in the
original 812-line source. Wrapped continuation lines belong to the preceding
entry. Narrative and diagrams are mapped by their enclosing heading.

### B.1 Purpose and product boundary

- L1 heading “Shmembee Playlist Companion” → §§1–2; **Refined** name.
- L3 heading and L5–20 purpose statements → §§2, 6; **Retained**.
- L22/L24 headings → §3; **Retained**.
- L26–39, each Initial release bullet → §3.1; **Retained/Refined** by explicit
  permission, identity, recovery, and outcome gates.
- L41 heading and L43 playback → §3.2; **Rejected**.
- L44 multiple queues → §3.2; **Rejected**.
- L45 EQ/lyrics/tags/widgets/Android Auto → §3.2; **Rejected**.
- L46 direct MusicBee → §§3.2, 6; **Rejected**.
- L47 full library browsing → §3.2; **Rejected**.
- L48 full reordering → §§3.2–3.3; **Deferred**.
- L49 undocumented internals and L51–52 rationale → §3.2; **Rejected**.

### B.2 Primary experience

- L54/L56 headings and L58–67 steps 1–10 → §§2, 15; **Retained/Refined** with
  resolution, stale-state, verification, and rollback steps.
- L69–89 conceptual screen → §15; **Retained** as nonbinding concept.
- L91 heading; L93 containing-first → §15; **Corrected** typo only.
- L94 indicator/bold → §15; **Refined** to accessible explicit indicator.
- L95 multi-select; L96 duplicate prevention; L99 append → §§12, 15;
  **Retained**.
- L97–98 explicit duplicate insertion → §§3.3, 12; **Deferred**.
- L101 heading; L103–109 each Remove bullet → §§12, 15;
  **Retained/Refined** accessible styling.
- L111 heading; L113 last tab; L114 search; L115 select/clear; L116 filter;
  L118 failed result; L119 undo → §15; **Retained**.
- L117 auto-return → §15; **Refined** opt-in and safety suppression.

### B.3 Architecture and GoneMAD integration

- L121 heading and L123–132 each technology bullet → §§4, 6;
  **Retained/Refined** to selected versions.
- L134–136 hybrid rationale → §4; **Rejected** frameworks.
- L138–166 layout heading/tree → §6; **Refined** repository/package names and
  additional safety boundaries.
- L168–186 data-flow heading/diagram → §6; **Retained/Refined** with probe,
  backup, and recovery.
- L188–214 conceptual interfaces → §6; **Refined** complete contracts.
- L216 heading and L218–221 share authority → §8; **Retained**.
- L223–231 manifest example → §8; **Retained** minimum filter, subject to proof.
- L233 heading; L235 content URI; L237 MIME; L238 grants; L239 no path → §8;
  **Retained**.
- L236 file URI → §8; **Device proof required**.
- L241–242 first payload proof → §§8, 20; **Device proof required**.

### B.4 Playlist access and M3U compatibility

- L244 heading and L246–255 setup steps 1–8 → §7; **Retained/Refined** with
  permission/grant verification and exact capability sequence.
- L257–263 likely folder/do-not-hardcode → §7; **Retained**.
- L265–266 diagnostics → §7; **Refined** redaction/recovery/contracts.
- L268 heading and L270–280 desktop references/share fixtures → §§6, 10, 17;
  **Retained**.
- L282 heading; L284 extension acceptance → §§7, 10.1; **Corrected** filtering
  belongs to discovery, parser accepts stream.
- L285 strict UTF-8 and L286 BOM → §10.1/Appendix A; **Corrected** by desktop
  BOM-detection evidence.
- L287 comments; L288 EXTINF; L289 order/duplicates; L291 nondestructive read →
  §10.1; **Retained/Refined**.
- L290 raw paths/line numbers → §10.1; **Corrected** to trimmed source value,
  not byte-raw.
- L293 heading; L295–302 writer bullets → §11; **Retained** as deterministic
  writer behavior.
- L303 no canonicalization → §11; **Unresolved Phase 2 gate** because it
  conflicts with deterministic canonical rewrite.
- L305 heading and L307–320 checksum definition → §10.3; **Retained/Refined**
  with existence and normalized-entry validation.

### B.5 Identity, membership, and operations

- L322 heading and L324–333 evidence tiers → §9; **Corrected/Refined** to direct
  MediaStore volume identity, temporary-URI treatment, and deterministic tiers.
- L335 no silent selection → §9; **Retained**.
- L337–350 intake model → §9; **Refined** to typed evidence/candidate/alias
  models.
- L352 heading; L354 emulated root; L356 relative; L357 `./`; L358 dot;
  L359 slash → §10.2; **Retained** in v1.
- L355 `/sdcard/`; L360 URI encoding; L361 volumes; L362 music roots →
  §§10.2, 11; **Refined/Deferred** to fixture-backed v2/style policy.
- L364–366 case/versioning → §§10.2–10.3; **Retained**.
- L368–370 alias → §9; **Refined** revalidation/invalidation rules.
- L372 heading and L374–381 membership steps 1–6 → §12; **Retained/Refined**
  volume and existence.
- L383–392 membership model → §12; **Refined** with existence state.
- L394–396 threading/cache → §12; **Retained**.
- L398/L400 headings; L402–405 Add bullets → §12; **Retained**.
- L406 dominant style and L407 ambiguous default → §11; **Refined** full style
  contract.
- L409–410 insertion/duplicates → §3.3; **Deferred**.
- L412 heading; L414–417 Remove bullets → §12; **Retained**.
- L419 individual occurrences → §§3.3, 12; **Deferred**.

### B.6 Write safety and undo

- L421 heading and L423–425 provider/strategy → §13; **Retained/Refined**.
- L427–443 transaction diagram → §13; **Refined** durable journal/recovery.
- L445 heading; L447–452 each pre-write bullet → §13; **Retained/Refined**.
- L454 heading; L456–460 each during-write bullet → §13;
  **Retained/Refined** transition journaling and cancellation.
- L462 heading; L464–468 each post-write bullet → §13;
  **Retained/Refined** exact restoration and uncertainty blocking.
- L470 compensating transaction statement → §13; **Retained**.
- L472 heading and L474–484 each operation-record bullet → §13;
  **Retained/Refined** complete Room journal.
- L486–489 stale-safe undo → §13; **Retained/Refined** precise ambiguity rules.

### B.7 Refresh and screens

- L491 heading and L493–501 every refresh-test bullet → §14;
  **Device proof required**.
- L503–506 conservative guidance → §14; **Refined** plural-neutral `N` and
  rescan wording.
- L508/L510 headings; L512–516 each Setup bullet → §§7, 15;
  **Retained/Refined** required permission/capability detail.
- L518 heading; L520–528 each membership-screen bullet → §15;
  **Retained/Refined** accessibility and malformed-versus-identity separation.
- L530–538 resolution heading and each bullet → §§9, 15; **Retained/Refined**.
- L540 heading; L542–546 each Result bullet → §15; **Retained/Refined**.
- L548 heading; L550 tree; L551 path style; L552 duplicates; L553 removal;
  L555 history; L556 aliases; L557 export; L558 version → §15; **Retained**.
- L554 auto-close → §15; **Refined** as safe opt-in auto-return.

### B.8 Milestones

- L560/L562 headings; L564–568 every Milestone 0 bullet and L570 exit →
  §§17–18 Phase 2; **Retained/Refined**.
- L572 heading; L574–578 every Milestone 1 bullet and L580–581 exit →
  §§8, 18 Phase 3; **Retained/Refined**, **Device proof required**.
- L583 heading; L585–589 every Milestone 2 bullet and L591 exit →
  §§7, 12, 15, 18 Phase 5; **Retained/Refined**.
- L593 heading; L595–599 disposable-name restriction; L601–602 bullets; L604
  exit → §§13, 18 Phase 6; **Retained/Refined** identity plus test-mode gate.
- L606 heading; L608–613 every Milestone 4 bullet and L615–616 exit →
  §§3, 12–15, 18 Phase 7; **Retained/Refined**.
- L618 heading; L620–634 each hardening test bullet and L636 recovery →
  §§17–18 Phase 8; **Retained/Refined**, recovery moved earlier as safety
  prerequisite.
- L638 heading; L640 prerequisite; L642 direct share; L643 launcher; L644
  Quick Settings; L645 assisted detection; L646 return; L647 full edit; L648
  multi-track; L650–651 metadata rule → §§3.3, 18 Phase 9; **Deferred**.

### B.9 Testing

- L653/L655 headings; L657 UTF/BOM; L658 comments/EXTINF; L659 path forms;
  L660 normalization; L661 Unicode; L662 duplicates; L663 checksum; L664
  transforms; L665 style; L666 ambiguity; L667 undo → §17; **Retained/Refined**.
- L669 heading and L671–682 each cross-repository fixture bullet → §17;
  **Retained**.
- L684 heading; L686 share; L687 tree grant; L688 provider; L689 transaction;
  L690 recreation; L691 URI metadata; L692 multi-select; L693 reconfirm; L694
  failures → §17; **Retained/Refined** expanded matrix.
- L696 heading and L698–708 every manual-log bullet → §§14, 17;
  **Retained/Refined** with exact target builds and additional evidence.

### B.10 Desktop relationship and risks

- L710 heading and L712–720 relationship/diagram → §§2, 6; **Retained**.
- L722 heading; L724 ordered; L725 duplicates; L726 normalization; L727
  checksum; L728 concurrency; L729 safety; L730 ambiguity → §§6, 9–13;
  **Retained**.
- L732 heading; L734 .NET; L735 MusicBee; L736 transports; L737 desktop
  SQLite; L738 Room → §6; **Retained** as “do not share.”
- L740–742 M3U authority/optional sidecar → §6; **Retained**.
- L744/L746 headings and L748–751 payload risk/mitigation → §§8–9;
  **Device proof required/Refined**.
- L753 heading and L755–759 stale-cache risk/mitigation → §14;
  **Device proof required**.
- L761 heading and L763–766 provider risk/mitigation → §§7, 13;
  **Retained/Refined**.
- L768 heading and L770–773 path risk/mitigation → §§9–11;
  **Retained/Refined** volume-aware contracts.
- L775 heading and L777–780 scope risk/mitigation → §§2–3; **Retained**.

### B.11 Acceptance and first action

- L782 heading; L784 share; L785 identity; L786 scan; L787 membership; L788
  visual; L789 tabs; L790 selection; L791 remove; L792 add; L793 untouched;
  L794 preservation; L795 backup/verify; L796 rollback; L797 undo; L798
  GoneMAD; L799 Shmembee; L800 speed → §19; **Retained/Refined** accessible
  indicators, explicit contracts, and recovery gates.
- L802 heading and L804–808 two proof requirements → §20;
  **Retained/Refined** identity and exact verification sequence.
- L810–812 read-only-next/write gate → §§18, 20; **Retained/Refined** with
  contracts, identity, and recovery prerequisites.
