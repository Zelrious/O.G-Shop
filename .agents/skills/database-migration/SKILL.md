---
name: database-migration
description: Design, implement, or review an O.G Shop PostgreSQL and Flyway schema change, data migration, constraint, index, seed, or database integration test.
---

# Database migration

1. Read `../../rules/database.md` and `../../workflows/database-change.md`.
2. Verify the owning module, invariant, existing migration history and affected queries.
3. Report compatibility, lock, data conversion, recovery and test plan; wait for approval.
4. Add a new immutable Flyway migration and any matching code/test updates.
5. Verify on PostgreSQL and document the result; never use real personal data.
