# Qdrant 벡터 스키마 초안

> 이 문서는 `dog_nose_embeddings` 컬렉션 기준의 초안입니다. 임베딩 모델 확정 후 차원 수를 갱신하세요.

---

## 컬렉션 정보

| 항목 | 값 |
|---|---|
| 컬렉션명 | `dog_nose_embeddings` |
| 벡터 차원 | TBD (모델 결정 후 확정, 예: 128, 512, 768) |
| 거리 함수 | `Cosine` (비문 유사도 기준) |

---

## 포인트 구조

```json
{
  "id": "UUID (dogs.id와 동일하게 맞추는 것 권장)",
  "vector": [0.12, -0.34, 0.56, "..."],
  "payload": {
    "dog_id": "uuid",
    "user_id": "uuid",
    "breed": "믹스",
    "nose_image_path": "uploads/dogs/uuid/nose_001.jpg",
    "registered_at": "2026-04-12T10:00:00Z",
    "is_active": true
  }
}
```

---

## payload 설명

| 필드 | 타입 | 설명 |
|---|---|---|
| `dog_id` | string (UUID) | MySQL dogs.id와 동일 |
| `user_id` | string (UUID) | 등록한 사용자 ID |
| `breed` | string | 품종 (검색 필터용) |
| `nose_image_path` | string | 원본 비문 이미지 경로 |
| `registered_at` | string (ISO8601) | 임베딩 등록 시각 |
| `is_active` | boolean | 비활성(분양완료, 삭제) 시 false |

payload는 검색 결과에서 바로 쓸 수 있는 최소 정보만 담습니다.  
상세 데이터는 `dog_id`를 이용해 MySQL에서 조회합니다.

---

## 저장 시 주의사항

- Qdrant point `id`는 UUID 또는 unsigned integer만 허용합니다. `dog_id`를 UUID로 사용하면 그대로 쓸 수 있습니다.
- 벡터 차원 수는 컬렉션 생성 시 고정됩니다. 모델 변경 시 컬렉션을 재생성해야 합니다.
- 동일 `dog_id`로 upsert하면 기존 포인트가 덮어써집니다 (재등록 시 활용).

## 조회 시 주의사항

- 유사도 검색 후 반드시 `is_active: true` 필터를 적용하여 비활성 개체를 제외합니다.
- top-K 결과에서 similarity threshold를 설정하여 너무 낮은 유사도 결과는 무시합니다 (임계값은 실험 후 결정).
- 검색 결과의 payload에서 `dog_id`를 꺼내 MySQL `dogs` 테이블과 JOIN하여 상세 정보를 반환합니다.

---

## 컬렉션 생성 예시 (Qdrant HTTP API)

```json
PUT /collections/dog_nose_embeddings
{
  "vectors": {
    "size": 512,
    "distance": "Cosine"
  }
}
```

실제 차원 수는 Python 임베딩 모델 출력 크기에 맞게 변경하세요.
