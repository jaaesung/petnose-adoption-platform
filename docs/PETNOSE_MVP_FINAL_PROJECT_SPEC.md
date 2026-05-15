# PetNose MVP Final Project Spec

## Canonical Baseline

This document defines the active PetNose MVP canonical model as of simplified DBML v2.

The active domain tables are:

1. `users`
2. `dogs`
3. `dog_images`
4. `verification_logs`
5. `adoption_posts`

MySQL is the source of truth for account, dog, image metadata, verification history, and adoption post data. Qdrant is a vector search index only. Image binaries are stored as files; MySQL stores relative paths in `dog_images.file_path`.

## Removed From MVP v2

Former profile, auth history, report, token, image quality/crop, Firebase, chat, push, reservation, payment, contract, report/admin dashboard, and non-canonical role extension areas are not part of the current canonical MVP v2 schema or API surface.

## Users

`users` stores account identity and author display information directly.

Columns:

- `id`
- `email`
- `password_hash`
- `role`
- `display_name`
- `contact_phone`
- `region`
- `is_active`
- `created_at`

Allowed roles are `USER` and `ADMIN`.

`display_name` is nullable. Nose verification is the first step before creating an adoption post, so signup and dog verification must not require the author display name up front. Before an adoption post is created, the service should validate that `users.display_name` exists.

## Dogs

`dogs` stores basic dog information and lifecycle/service status.

Columns:

- `id`
- `owner_user_id`
- `name`
- `breed`
- `gender`
- `birth_date`
- `description`
- `status`
- `created_at`
- `updated_at`

`dogs.status` is the dog lifecycle/service status:

- `PENDING`
- `REGISTERED`
- `DUPLICATE_SUSPECTED`
- `REJECTED`
- `ADOPTED`
- `INACTIVE`

`DUPLICATE_SUSPECTED` remains on `dogs.status` because it blocks adoption post creation. Detailed verification information such as similarity, duplicate candidate, model, and dimension belongs in `verification_logs`.

`dogs` does not store `qdrant_point_id`. The Qdrant point id is always equal to `dogs.id`.

## Dog Images

`dog_images` stores image metadata and relative file paths only.

Allowed `image_type` values:

- `NOSE`
- `PROFILE`

Columns:

- `id`
- `dog_id`
- `image_type`
- `file_path`
- `mime_type`
- `file_size`
- `sha256`
- `uploaded_at`

Image files are not stored in MySQL BLOB columns.

## Verification Logs

`verification_logs` is the source of truth for nose verification attempts and results.

Columns:

- `id`
- `dog_id`
- `dog_image_id`
- `requested_by_user_id`
- `result`
- `similarity_score`
- `candidate_dog_id`
- `model`
- `dimension`
- `failure_reason`
- `created_at`

Allowed `result` values:

- `PENDING`
- `PASSED`
- `DUPLICATE_SUSPECTED`
- `EMBED_FAILED`
- `QDRANT_SEARCH_FAILED`
- `QDRANT_UPSERT_FAILED`

The embedding vector itself is not stored in MySQL. Vectors are stored only in Qdrant.

## Adoption Posts

`adoption_posts` stores adoption listing content and publishing state.

Columns:

- `id`
- `author_user_id`
- `dog_id`
- `title`
- `content`
- `status`
- `published_at`
- `closed_at`
- `created_at`
- `updated_at`

Allowed `status` values:

- `DRAFT`
- `OPEN`
- `RESERVED`
- `COMPLETED`
- `CLOSED`

Before creating or opening an adoption post, the service should require a valid author `display_name` and a dog that is eligible for posting. Dogs in `DUPLICATE_SUSPECTED`, `REJECTED`, or `INACTIVE` state are not eligible.

## Handover-Time Dog Nose Verification

Handover-time dog nose verification is included in the MVP trust/safety flow after adoption post publication and status management are available.

This feature answers the handover question: when an adopter visits the handover location, is the dog in front of them the same dog as the dog registered in the adoption post?

The handover verification API is intentionally different from dog registration:

- `POST /api/dogs/register` registers a dog and performs duplicate suspicion detection before adoption post creation.
- `POST /api/adoption-posts/{post_id}/handover-verifications` does not register a dog.
- It verifies a freshly captured nose image against the dog already linked to an existing adoption post.
- The expected dog is `adoption_posts.dog_id`.
- Spring Boot remains the orchestrator for Python Embed and Qdrant calls. Flutter must not call Python Embed, Qdrant, or MySQL directly.

The MVP handover verification check is stateless:

- It does not save the handover image.
- It does not create a dog.
- It does not create a `dog_images` row.
- It does not create a `verification_logs` row in the current MVP implementation.
- It does not mutate `adoption_posts.status`.
- It does not mutate `dogs.status`.
- It does not automatically complete adoption.
- Adoption completion remains a separate owner-only status update action.

This feature does not add reservation, payment, contract, Firebase, chat, push, report/admin, `SHELTER`, or `ADOPTER` concepts.

## Dog Registration Pipeline Policy

`POST /api/dogs/register` keeps the existing response contract, even though several response fields are now calculated rather than stored on `dogs`.

Calculated response fields:

- `qdrant_point_id`
  - normal registration: `dog_id`
  - duplicate suspected: `null`
- `verification_status`
  - derived from the latest `verification_logs.result`
- `embedding_status`
  - derived from the latest `verification_logs.result`

Normal registration writes the vector to Qdrant with point id `dogs.id`. Duplicate suspected registration must not upsert an active Qdrant point for the new dog.

## Final MVP User Flow

1. A `USER` registers a dog with a nose image through `POST /api/dogs/register`.
2. Duplicate suspicion blocks adoption post creation when `registration_allowed=false`.
3. A verified dog can be used to create an adoption post.
4. Public users can view adoption posts without nose image exposure.
5. At handover time, an authenticated user can upload a freshly captured nose image for stateless verification against the post's expected dog.
6. A matched handover verification result is a safety signal only.
7. Completion still happens through the existing owner-only post status update flow.
