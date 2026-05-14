# Project Knowledge Index

## Active Canonical Baseline

The active PetNose MVP canonical baseline is simplified DBML v2.

Primary references:

- `docs/PETNOSE_MVP_FINAL_PROJECT_SPEC.md`
- `docs/PETNOSE_MVP_API_CONTRACT.md`
- `docs/db/petnose_mvp_schema.dbml`
- `docs/db/V20260508__mvp_canonical_schema.sql`

## Active Domain Tables

The active MVP domain table set is:

1. `users`
2. `dogs`
3. `dog_images`
4. `verification_logs`
5. `adoption_posts`

MySQL is the source of truth. Qdrant is a vector search index only. Dog image binaries are file-system objects; MySQL stores relative paths only.

## Current Registration Policy

The existing real-model `/api/dogs/register` pipeline remains the behavior to preserve:

- normal registration returns `registration_allowed=true`
- duplicate suspected registration returns HTTP 200 with `registration_allowed=false`
- normal registration upserts a Qdrant point with point id equal to `dogs.id`
- duplicate suspected registration skips Qdrant upsert for the new dog

Response fields `qdrant_point_id`, `verification_status`, and `embedding_status` are API-calculated fields. They are not stored as columns on `dogs` in the canonical v2 schema.

## Current Flutter API Flow

The current Flutter MVP flow is documented in `docs/PETNOSE_MVP_API_CONTRACT.md` and is implemented for:

- `POST /api/dogs/register`
- `GET /api/users/me`
- `PATCH /api/users/me/profile`
- `POST /api/adoption-posts`
- `GET /api/adoption-posts` public feed, stabilized for `OPEN`, `RESERVED`, and `COMPLETED`
- `GET /api/adoption-posts/{post_id}` public detail for public statuses
- `GET /api/adoption-posts/me` owner-only post list
- `PATCH /api/adoption-posts/{post_id}/status` owner-only status management

Public adoption post list/detail responses must not expose `nose_image_url`. Dog registration is owner-scoped and may return the newly submitted dog's own `nose_image_url`; `top_match` must never include a raw nose image URL.

`POST /api/dogs/register` currently prefers JWT principal ownership at the controller boundary but keeps the temporary local/dev `user_id` fallback. Removing that fallback before production hardening is a follow-up, not part of broad service changes in the Flutter contract branch.

## Removed Scope

Former profile, auth history, report, token, Firebase, chat, and dog image quality/crop extension areas are outside the current canonical MVP v2 table and API set.
