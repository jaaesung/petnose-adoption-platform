# DB / Vector Role (MVP Baseline)

## 1. 역할 분리 원칙

| 구분 | MySQL | Qdrant |
|---|---|---|
| 역할 | Source of Truth | 벡터 검색 인덱스 |
| 저장 데이터 | 도메인 정합 데이터(계정/강아지/게시글/신고/인증이력) | 임베딩 벡터 + 최소 payload |
| 복구 기준 | 기준 데이터 원본 | MySQL 기준으로 재구성 가능 |

핵심 원칙:
- MySQL이 정본이다.
- Qdrant 유실 시 MySQL 기준으로 재임베딩/재적재한다.
- Qdrant는 검색 성능 계층이며 정본이 아니다.

## 2. MySQL 테이블 범위 (MVP)

이번 MVP baseline에서 관리하는 테이블은 정확히 아래 8개다.

1. `users`
2. `refresh_tokens`
3. `shelter_profiles`
4. `dogs`
5. `dog_images`
6. `verification_logs`
7. `adoption_posts`
8. `reports`

## 3. Qdrant 컬렉션 계약

- collection: `dog_nose_embeddings`
- vector size: `128` (`mock-v1`)
- distance: `Cosine`
- point id: `dogs.id`와 동일 UUID 문자열

Spring 설정 기본값:
- `qdrant.collection=dog_nose_embeddings`
- `qdrant.vector-dimension=128`
- `qdrant.distance=Cosine`

실제 모델(`dog-nose-identification2`) 테스트 권장값:
- `qdrant.collection=dog_nose_embeddings_real_v1`
- `qdrant.vector-dimension=2048` (현재 로컬 모델 분석 기준)
- 기존 mock 컬렉션과 분리 운영

## 4. 이미지 저장 원칙

- 이미지 원본 파일은 MySQL에 저장하지 않는다.
- MySQL에는 `dog_images.relative_path`만 저장한다.
- URL 서빙은 Nginx 정적 경로를 사용한다.
  - `GET /files/{relative_path}`
  - Nginx가 uploads 볼륨에서 직접 응답
- Qdrant에는 이미지 바이트/원본/base64를 저장하지 않는다.

## 5. 인증 상태 모델링 원칙

- `verification_logs`는 인증 시도 이력의 source of truth다.
- `dogs`의 아래 컬럼은 최신 snapshot이다.
  - `nose_verification_status`
  - `embedding_status`
  - `duplicate_candidate_dog_id`
  - `duplicate_similarity_score`
  - `verified_at`

중복 의심 정책:
- `DUPLICATE_SUSPECTED`이면 active Qdrant point upsert를 기본적으로 수행하지 않는다.
- `VERIFIED`인 경우에만 active point upsert 후 `dogs.embedding_status=COMPLETED`로 본다.
- 실제 모델 전환 초기에는 동일 파일 재등록 검증을 우선하고, threshold calibration을 별도 진행한다.

## 6. Qdrant payload 계약

```json
{
  "dog_id": "uuid-string",
  "user_id": 101,
  "nose_image_path": "dogs/{uuid}/nose/{yyyyMMdd_HHmmss}_{filename}.jpg",
  "registered_at": "2026-05-07T00:00:00Z",
  "is_active": true,
  "breed": "optional"
}
```

금지 payload:
- 개인정보(전화번호/주소/이메일/비밀번호)
- 원본 이미지 바이너리, base64
- 긴 description 전문

## 7. 이번 MVP에서 제외한 후순위 테이블

아래는 확장 후보이며 이번 migration baseline에는 포함하지 않았다.

- `qdrant_sync_jobs`
- `admin_actions`
- `favorites`
- `adoption_applications`
- `post_images`
- `dog_breeds`
- `dog_verification_attempts`
