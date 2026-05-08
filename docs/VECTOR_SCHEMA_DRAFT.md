# VECTOR SCHEMA DRAFT (MVP)

## 1. Collection Contract

- Name: `dog_nose_embeddings`
- Vector size: `128` (`mock-v1`)
- Distance: `Cosine`
- Point id: `dogs.id`와 동일 UUID string

설정 오버라이드:
- `qdrant.collection`
- `qdrant.vector-dimension`
- `qdrant.distance`

기본값:
- `dog_nose_embeddings`, `128`, `Cosine`

실제 모델(`EMBED_MODEL=dog-nose-identification2`) 테스트 시 권장:
- collection: `dog_nose_embeddings_real_v1`
- vector size: `2048` (현재 로컬 모델 분석 기준)
- 기존 mock 컬렉션(`128`)과 혼용 금지

## 2. Payload Contract

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

필수 의미:
- `dog_id`: MySQL `dogs.id`
- `user_id`: MySQL `users.id`
- `nose_image_path`: MySQL에 저장된 relative path 맥락

## 3. 보안/개인정보 금지 규칙

Qdrant payload 금지:
- `owner_phone`, `address`, `email`, `password`
- `raw_image_bytes`, `raw_image_base64`
- 긴 `description` 원문
- 기타 PII

## 4. 저장 경계

- MySQL에는 벡터를 저장하지 않는다.
- Qdrant에는 원본 이미지를 저장하지 않는다.
- MySQL `dog_images.relative_path`만 저장하고,
  Nginx가 `/files/{relative_path}`를 정적 서빙한다.

## 5. 인증/중복 의심 정책

- `verification_logs`는 인증 시도 이력의 source of truth다.
- `dogs`의 인증 관련 컬럼은 최신 snapshot이다.
- `DUPLICATE_SUSPECTED`이면 active point upsert를 기본적으로 생략한다.
- `VERIFIED`일 때만 active point upsert 후 `dogs.embedding_status=COMPLETED`로 본다.

## 6. 운영 체크리스트

1. 앱 기동 시 컬렉션 미존재면 생성
2. 컬렉션이 이미 존재하면 dimension/distance 계약 검증
3. 불일치 시 앱은 기동을 유지하되 경고 로그를 남김
4. 스키마 변경(모델 차원 변경 등) 시 컬렉션 재구성 계획을 별도 수립
5. Qdrant 차원은 변경 불가이므로, 모델 전환 시 새 컬렉션 생성 또는 볼륨 리셋이 필요
6. 같은 파일 재등록 중복 판정 우선 검증 후 threshold calibration을 별도 수행
