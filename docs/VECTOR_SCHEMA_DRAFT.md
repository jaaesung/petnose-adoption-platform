# Vector Schema Draft (MVP Canonical v2)

## Collection Contract

Real model target:

- Name: `dog_nose_embeddings_real_v1`
- Vector size: `2048`
- Distance: `Cosine`
- Point id: `dogs.id`

The older mock collection can exist in local development, but it is not the canonical real-model target.

Configuration keys:

- `qdrant.collection`
- `qdrant.vector-dimension`
- `qdrant.distance`

## Payload Contract

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

Meaning:

- `dog_id`: MySQL `dogs.id`
- `user_id`: MySQL `users.id`
- `nose_image_path`: relative path stored in `dog_images.file_path`

## Security Rules

Do not store these values in Qdrant payloads:

- phone numbers
- addresses
- email addresses
- credentials
- image bytes
- base64 image data
- long dog descriptions

## Storage Boundary

- MySQL does not store vectors.
- Qdrant does not store original images.
- MySQL stores `dog_images.file_path` as a relative path.
- Nginx serves uploaded files through `/files/{relative_path}`.

## Verification Policy

- `verification_logs` is the source of truth for verification attempts and outcomes.
- `dogs.status` stores lifecycle/service status only.
- `DUPLICATE_SUSPECTED` remains a dog status because it blocks adoption post creation.
- Normal registration upserts an active Qdrant point with id equal to `dogs.id`.
- Duplicate suspected registration does not upsert an active Qdrant point for the new dog.
- API fields `qdrant_point_id`, `verification_status`, and `embedding_status` are calculated from `dogs.id` and the latest `verification_logs.result`.

## Operations Checklist

1. Create the collection on startup when missing.
2. Validate collection dimension and distance when it already exists.
3. Keep startup alive but log a warning if the collection contract does not match.
4. Use a new collection when the model dimension changes.
5. Validate duplicate detection with identical-image registration before threshold calibration.
