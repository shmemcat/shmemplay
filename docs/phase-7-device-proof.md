# Phase 7 real multi-playlist device proof

Execute this matrix on Android 16 with GoneMAD 4.1.11 after making an external
backup of the playlist tree. Use expendable copies of representative real
playlists before valuable originals. Never commit raw provider URIs, private
backup locations, unredacted paths, or track metadata.

Phase 7 permits user-enabled writes to selected real playlists even while the
Phase 6 physical-device checklist remains incomplete. This is not permission to
bypass a known failed safety gate. The unchanged
[`phase-6-device-proof.md`](phase-6-device-proof.md) remains historical and
regression guidance for disposable transactions, fault injection, recovery,
undo, and GoneMAD refresh behavior.

## Build, evidence, and safety prerequisites

- [ ] Record app commit/build, Android build, GoneMAD version, provider, volume,
      fixture contract versions, and whether the playlist set is expendable
- [ ] Revalidate the persisted read/write SAF tree grant
- [ ] Repeat the disposable create/write/reread/rename/update/delete/cleanup
      capability test for the current tree/provider
- [ ] Confirm startup recovery has completed and no operation is active
- [ ] Confirm exact track resolution and the selected volume-aware identity
- [ ] Confirm each selected target is present, canonical/editor-eligible, and
      has an unambiguous persisted document identity
- [ ] Confirm backup publication and integrity can complete before any write
- [ ] Confirm unsupported, malformed, missing, duplicate-identity, or unknown
      targets remain mutation-disabled with a useful reason

## Enablement, confirmation, and reconfirmation

- [ ] Real playlist writes default off and require a deliberate user choice
- [ ] Enablement explains real external files may change and that recovery may
      require user attention; cancel leaves writes disabled
- [ ] Every batch shows action, resolved track, selected target count, current
      membership/duplicates, add-one-if-absent or remove-all semantics, and
      relevant warnings before confirmation
- [ ] The coordinator rereads every selected target immediately before mutation
- [ ] A material change to target identity, eligibility, generated path,
      occurrence count, or planned outcome requires reconfirmation
- [ ] An unchanged plan does not ask for redundant reconfirmation
- [ ] Concurrent addition/removal that already satisfies intent becomes an
      explicit skip when safe
- [ ] Revoked permission, pending recovery, unknown bytes, or unavailable backup
      cancels or blocks the batch before the first document write

## Real multi-target operations and results

- [ ] Add an absent track to several selected playlists in deterministic order
- [ ] Include an already-containing target and verify an explicit skip
- [ ] Remove from targets containing one and multiple occurrences; all matching
      occurrences are removed while unrelated order/multiplicity is preserved
- [ ] Verify unselected playlists remain byte-identical
- [ ] Verify every changed target is closed, reread, parsed, and checked against
      its expected ordered semantic checksum
- [ ] Results remain visible after recreation and report every selected target
      as changed, skipped, failed, restored, or recovery-required
- [ ] Each target reports before/after counts and a redacted reason where useful
- [ ] A mixed batch is not summarized as an unqualified success

## Rollback, recovery, and undo

- [ ] Inject failure after each target position and verify all possibly changed
      targets roll back in reverse order from integrity-checked exact-byte backups
- [ ] Verify each restoration by reread; any uncertainty becomes
      recovery-required and blocks new writes
- [ ] Force-stop at each durable transition, relaunch, and verify idempotent
      startup recovery before another write is offered
- [ ] Missing targets, revoked grants, bad backups, and unknown current bytes
      produce actionable recovery status without claiming success
- [ ] Undo is offered only for eligible targets and performs fresh reads
- [ ] Undo precisely inverts unchanged results, preserves unrelated later edits
      when unambiguous, and refuses ambiguous concurrent changes
- [ ] Undo uses the same journal, backup, verify, rollback, and recovery protocol

## Return flow and minimal UI

- [ ] Add/Remove, selection, confirmation, progress, and results are the primary
      workflow; infrequent Settings, History, and Diagnostics share one overflow
      menu
- [ ] Real-write enablement remains a prominent safety setting and cannot be
      toggled accidentally from an operation overflow shortcut
- [ ] Results expose per-target detail, eligible Undo, recovery instructions,
      Return to GoneMAD, and the conservative refresh message
- [ ] Auto-return defaults off and, when enabled, occurs only after an
      uncomplicated terminal result for every selected target
- [ ] Attention-worthy skips, warnings, failures, restoration, rollback, or
      recovery-required suppress auto-return
- [ ] Rotation, process recreation, and Back do not hide durable responsibility

## GoneMAD and Shmembee reconciliation

For Add, Remove, rollback, and Undo, test GoneMAD foregrounded, backgrounded,
stopped, and viewing the affected playlist:

- [ ] Record immediate visibility, reopen behavior, rescan requirements, latency,
      open-playlist cache behavior, and stale-overwrite behavior
- [ ] Do not promise automatic refresh; retain the documented reopen/rescan
      instruction until device evidence supports something stronger
- [ ] Let GoneMAD rewrite/save a shmemplay result, then verify normalized
      membership, order, duplicate semantics, and checksum interpretation
- [ ] Open and save affected fixtures/playlists with Shmembee, synchronize by the
      normal user workflow, then rescan in shmemplay and GoneMAD
- [ ] Verify Shmembee-generated and rewritten relative/absolute, separator,
      Unicode, empty, duplicate, `.m3u`, and `.m3u8` cases reconcile without
      false membership or duplicate insertion
- [ ] Exercise concurrent desktop change before confirmation, during an
      operation where feasible, and before Undo; record safe skip,
      reconfirmation, refusal, rollback, or recovery outcome

## Exit evidence

- [ ] Repeated real multi-target Add and Remove operations prove per-target
      backup, journal, write, reread, parse, semantic verification, and results
- [ ] Failure injection proves reverse rollback, restoration verification,
      startup recovery, write blocking on uncertainty, and safe Undo
- [ ] Confirmation and material-change reconfirmation are understandable and do
      not silently broaden targets
- [ ] Auto-return never hides an attention-worthy result
- [ ] GoneMAD and Shmembee reconciliation outcomes and required refresh steps are
      recorded, including any backend/provider behavior still unknown
- [ ] External backup comparison accounts for every selected and unselected file
