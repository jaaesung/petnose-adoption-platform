# MySQL vs Qdrant 역할 분리

## 원칙 요약

| 항목 | MySQL | Qdrant |
|---|---|---|
| 역할 | 관계형 데이터 Source of Truth | 벡터 검색 인덱스 |
| 저장 대상 | 모든 도메인 데이터 | 비문 임베딩 벡터 + 최소 payload |
| 조회 방식 | SQL (JPA/MyBatis) | 벡터 유사도 검색 (cosine, L2) |
| 복구 기준 | MySQL dump → Qdrant 재생성 가능 | MySQL로부터 재구성 가능 |

**Source of Truth는 MySQL입니다.**  
Qdrant 데이터가 유실되면 MySQL 데이터를 기반으로 재임베딩 → 재적재할 수 있어야 합니다.

---

## MySQL 저장 대상

- 사용자 계정, 프로필, 인증 로그
- 강아지 개체 정보 (품종, 이름, 상태 등)
- 비문 이미지 경로 (파일은 `uploads/` 볼륨에 저장)
- 입양 게시글, 신고, 매칭 이력
- Qdrant 포인트 ID와 `dog_id` 매핑 (연관관계 추적용)

## Qdrant 저장 대상

- 비문 임베딩 벡터
- payload (검색 결과에서 바로 쓸 최소 정보만)

### payload 후보

```json
{
  "dog_id": "uuid",
  "user_id": 101,
  "breed": "string",
  "nose_image_path": "string",
  "registered_at": "ISO8601",
  "is_active": true
}
```

`user_id`는 MySQL `users.id`와 동일한 BIGINT 값을 사용합니다.

**payload 원칙:**
- payload에는 조회 성능을 위한 최소 식별 정보만 넣습니다.
- 상세 데이터는 payload에서 `dog_id`를 꺼낸 뒤 MySQL에서 JOIN하여 조회합니다.
- payload를 MySQL 테이블의 복사본처럼 쓰지 않습니다.

---

## 등록 트랜잭션 순서

```
1. MySQL: dogs 테이블에 INSERT (dog_id 생성)
2. Python Embed: 이미지 → 벡터 변환
3. Qdrant: 포인트 upsert (point_id = dog_id 또는 UUID)
4. MySQL: qdrant_point_id 컬럼 업데이트 (선택)
```

MySQL INSERT를 먼저 수행하여 트랜잭션 실패 시 롤백이 가능하게 합니다.  
Qdrant upsert 실패 시에는 MySQL 레코드는 유지하고 재시도 큐 또는 수동 재처리를 통해 동기화합니다.

---

## 실패 처리 원칙

- **Qdrant upsert 실패:** MySQL INSERT를 롤백하지 않습니다. 비문 임베딩 없이도 기본 개체 정보는 유지합니다. 실패 로그를 남기고 재처리 가능하게 합니다.
- **MySQL INSERT 실패:** 전체 등록 흐름을 중단합니다. Qdrant에는 아무것도 쓰지 않습니다.
- **Python Embed 호출 실패:** 임베딩 없이 강아지 등록 자체를 거부하거나, 임베딩 대기 상태로 저장합니다 (팀 결정 필요).
- **재동기화:** MySQL에서 `qdrant_point_id IS NULL` 레코드를 조회해 배치로 재임베딩할 수 있습니다.
