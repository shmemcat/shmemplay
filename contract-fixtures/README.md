# Cross-repository contract fixtures

This directory will hold versioned, platform-neutral fixtures consumed by both
Shmemplaylist (Kotlin) and Shmembee (C#). Phase 1 establishes categories only;
it intentionally makes no assertions before the contract decisions in Phase 2.

- `parser/`: encoded M3U inputs and expected source records.
- `normalization/`: path normalization and comparison cases.
- `writer/`: deterministic writer or source-preserving editor cases.
- `checksums/`: semantic checksum vectors and existence state.
- `operations/`: add, remove, stale-state, rollback, recovery, and undo cases.
- `gonemad/`: redacted, reproducible GoneMAD-generated and rewritten examples.

Each populated category will include a manifest naming the contract version,
exact input bytes or encoding, expected output or error, and rationale.
