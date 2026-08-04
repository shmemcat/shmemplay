# Cross-repository contract fixtures

This directory holds versioned, platform-neutral fixtures consumed by both
Shmemplaylist (Kotlin) and Shmembee (C#). `manifest.json` is the fixture-set
index. Category manifests reference exact byte files; paths are relative to
this directory so consumers do not depend on the host filesystem layout.

- `parser/`: encoded M3U inputs and expected source records.
- `normalization/`: path normalization and comparison cases.
- `writer/`: deterministic writer or source-preserving editor cases.
- `checksums/`: semantic checksum vectors and existence state.
- `operations/`: add, remove, stale-state, rollback, recovery, undo, and Phase 7
  multi-target planning/result-order cases.
- `gonemad/`: redacted, reproducible GoneMAD-generated and rewritten examples.

Phase 2 fixtures cover `m3u-parser-v1`, `phone-path-v1`,
`semantic-checksum-v1`, `m3u-writer-v1`,
`canonical-gonemad-profile-v1`, and `playlist-operations-v1`.
The optional `phase7BatchCases` section extends `playlist-operations-v1`
without changing its per-playlist `cases` schema. Consumers should validate
deterministic mutation order, reverse rollback order, material-reread
reconfirmation, and each target's expected transformed paths; outcome labels
describe coordinator behavior that remains backend-specific.
