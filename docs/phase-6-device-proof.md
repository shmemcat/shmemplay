# Phase 6 disposable transaction device proof

Execute this matrix on Android 16 with GoneMAD 4.1.11. Phase 6 may mutate only
the app-created, identity-persisted `Shmemplaylist Companion Test.m3u`.
Back up the playlist directory before testing. Never record raw provider URIs,
unredacted paths, or track metadata in committed artifacts.

## Build and setup

- [ ] Record app commit/build, Android build, GoneMAD version, and provider
- [ ] Revalidate the persisted writable tree grant
- [ ] Complete the disposable provider capability test, including cleanup
- [ ] Explicitly enable Phase 6 test mode
- [ ] Create the test playlist through shmemplay
- [ ] Confirm another document with the same display name cannot be adopted
- [ ] Confirm every non-test playlist remains mutation-disabled

## Verified add and remove

- [ ] Add an absent resolved primary-storage track
- [ ] Reread and parse the document after close
- [ ] Verify exact expected ordered semantic checksum and one occurrence
- [ ] Repeat Add and verify it skips instead of duplicating
- [ ] Remove a track with one occurrence and verify zero remain
- [ ] Seed duplicate occurrences, remove, and verify all are removed
- [ ] Confirm unrelated records preserve order and multiplicity
- [ ] Confirm a noncanonical test playlist is blocked before backup/write

## Journal and backup

- [ ] Confirm the operation and target are journaled before document mutation
- [ ] Confirm the private backup exactly matches original bytes
- [ ] Confirm recorded backup SHA-256 matches the private file
- [ ] Confirm terminal success retains enough state for eligible undo
- [ ] Confirm diagnostics redact provider URI and private backup location

## Failure injection, rollback, and recovery

For every supported injection point, record the operation state, current
document classification, rollback result, and whether new writes are blocked:

- [ ] Before and after journal creation
- [ ] Before and after exact-byte backup publication
- [ ] Before and after pre-write journal transition
- [ ] Before and after provider write/close
- [ ] Before and after reread
- [ ] Before and after parse/semantic verification
- [ ] Before and after rollback transition
- [ ] Before and after exact-byte restoration
- [ ] Before and after restoration verification
- [ ] Simulated or real low-space backup failure
- [ ] Revoked SAF grant
- [ ] Missing/renamed test document
- [ ] Malformed provider reread
- [ ] Cancellation at every suspend boundary

No failure may leave unexplained content. Any unknown state must remain
`recovery-required` and block further writes.

## Process death and startup recovery

- [ ] Force-stop after every durable transition and relaunch
- [ ] Recovery classifies current bytes as original, expected, missing, or unknown
- [ ] Original is finalized without an unnecessary rewrite
- [ ] Expected is verified and finalized
- [ ] Unknown is restored from the verified exact-byte backup
- [ ] Missing or unverifiable state blocks writes and presents corrective status
- [ ] Repeated recovery is idempotent

## Undo and concurrency

- [ ] Undo a successful Add after an unchanged result
- [ ] Undo a successful Remove when reinsertion is unambiguous
- [ ] Undo itself uses journal, backup, verification, and rollback
- [ ] External semantic change after success causes Undo to refuse safely
- [ ] Ambiguous duplicate/reinsertion state causes Undo to refuse safely
- [ ] A second app operation cannot run concurrently

## GoneMAD refresh behavior

Test with GoneMAD foregrounded, backgrounded, stopped, viewing the playlist,
and after a manual library/playlist rescan:

- [ ] Add visibility and latency recorded
- [ ] Remove visibility and latency recorded
- [ ] Rollback visibility and latency recorded
- [ ] Undo visibility and latency recorded
- [ ] Active/open playlist stale-overwrite behavior recorded
- [ ] Required user refresh procedure documented

## Exit evidence

- [ ] Repeated transactions prove write, reread, parse, semantic verification,
      rollback, restoration verification, startup recovery, and safe undo
- [ ] No injected failure leaves silent or unverified content
- [ ] All non-test playlist hashes are unchanged
- [ ] The test playlist is the only document Phase 6 can mutate
- [ ] Real playlist mutation remains disabled
