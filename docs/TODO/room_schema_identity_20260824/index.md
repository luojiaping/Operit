---
fork_repository: https://github.com/luojiaping/Operit.git
working_branch: development
---

# Room Schema Identity Repair

## Background

The development APK opened an existing `app_database` and Room reported:

`Expected identity hash 3eb9b6d4699ec05210dd704ecc46ebcb, found 462a1eea8e83a614a9dbf7a942efb482`.

Room version 21 had several token statistics schema edits without a version bump.
The device was already at version 21, so `MIGRATION_20_21` could not run again during
an overlay install.

## Scope

- Keep token usage rows while normalizing the published v21 table shapes in `21 -> 22`.
- Create the Agent execution tables in the same migration.
- Export Room schemas for future migration review.
- Do not delete the application database or use a destructive migration.

## Progress

- [DONE] Detect the existing v21 token columns through both Room SQLite migration APIs.
- [DONE] Rebuild the token table with the current entity shape and preserve shared data.
- [DONE] Add the Agent tables to the same `21 -> 22` migration.
- [DONE] Enable Room schema export configuration.
- [TODO] Verify the migration on a device with the reported database and complete remote dev build.
