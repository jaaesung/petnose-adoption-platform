# DB / Vector Role (MVP Canonical v2)

## Role Separation

| Layer | Role | Stored Data |
|---|---|---|
| MySQL | Source of truth | users, dogs, dog image metadata, verification history, adoption posts |
| Qdrant | Vector search index | dog nose embedding vectors and minimal search payload |
| File storage | Binary object storage | uploaded nose/profile images |

Core rules:

- MySQL is the source of truth.
- Qdrant can be rebuilt from MySQL plus stored image files.
- Qdrant is not an authority for account, dog lifecycle, adoption post, or verification history.
- MySQL never stores embedding vectors or image BLOBs.

## MySQL Table Scope

The active MVP table set is exactly:

1. `users`
2. `dogs`
3. `dog_images`
4. `verification_logs`
5. `adoption_posts`

## Qdrant Collection Contract

Real model target:

- collection: `dog_nose_embeddings_real_v1`
- vector size: `2048`
- distance: `Cosine`
- point id: `dogs.id`

Qdrant point id is not stored as a column in MySQL. API responses calculate it:

- normal registration: `qdrant_point_id = dog_id`
- duplicate suspected registration: `qdrant_point_id = null`

## Image Storage Policy

- Uploaded image bytes are stored outside MySQL.
- `dog_images.file_path` stores a relative path under the upload root.
- Public file URLs use `/files/{relative_path}`.
- Qdrant payload may contain the relative nose image path for search result display.

## Verification Modeling Policy

`verification_logs` is the source of truth for nose verification attempts.

`dogs.status` keeps lifecycle/service state only. `DUPLICATE_SUSPECTED` remains there because it blocks adoption post creation.

Detailed verification data belongs in `verification_logs`:

- result
- similarity score
- duplicate candidate dog id
- model
- dimension
- failure reason

API response fields `verification_status` and `embedding_status` are calculated from the latest `verification_logs.result`.

## Qdrant Payload Contract

```json
{
  "dog_id": "uuid-string",
  "user_id": 101,
  "nose_image_path": "dogs/{uuid}/nose/{yyyyMMdd_HHmmss}_{filename}.jpg",
  "registered_at": "2026-05-08T00:00:00Z",
  "is_active": true,
  "breed": "optional"
}
```

Do not store personal contact information, credentials, image bytes, base64 image data, or long descriptions in Qdrant payloads.
