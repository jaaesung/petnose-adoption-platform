# DB Migration Strategy

## Purpose

Flyway manages database schema history for the backend runtime. The active product schema is defined separately by the canonical v2 documents:

- `docs/PETNOSE_MVP_FINAL_PROJECT_SPEC.md`
- `docs/db/petnose_mvp_schema.dbml`
- `docs/db/V20260508__mvp_canonical_schema.sql`

The file under `docs/db/` is a documentation clean schema. It is not automatically applied by the application.

## Current Policy

- Do not edit an already-applied backend Flyway migration file.
- Add a new backend migration only when implementation work is approved.
- Keep JPA DDL generation disabled for dev/prod runtime.
- Prefer a clean dev DB rebuild only after explicit approval, because it can discard local data.

## Backend Migration Directory

```text
backend/src/main/resources/db/migration/
```

Naming:

```text
V{version}__{description}.sql
```

Examples:

```text
V4__align_users_profile_fields.sql
V5__move_registration_snapshot_to_verification_logs.sql
```

## Environment Policy

| Environment | Flyway | JPA DDL | Notes |
|---|---|---|---|
| dev | enabled | `none` | Flyway owns DDL |
| test | usually disabled | test profile controlled | Tests may use in-memory or mocked persistence |
| prod | enabled | `none` | Direct production DDL is not allowed |

## Canonical v2 Alignment Strategy

When code changes are approved, the safest alignment path is:

1. Add user profile fields to `users`.
2. Move registration result snapshot reads to `verification_logs`.
3. Stop writing removed dog snapshot columns.
4. Drop removed columns and removed tables only after data migration and rollback review.
5. Keep Qdrant point id equal to `dogs.id`; do not add a separate DB column.

For local dev only, a clean rebuild from the canonical v2 clean schema can be faster, but it requires explicit approval before any destructive action.
