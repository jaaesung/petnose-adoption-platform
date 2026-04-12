# 시스템 아키텍처

## 문서 목적

이 문서는 PetNose 플랫폼의 런타임 구조, 서비스 간 책임 분리, 주요 요청 흐름을 기술합니다.  
구현 세부사항보다는 전체 설계 방향을 공유하는 것이 목적입니다.

---

## 런타임 구조

```
[Flutter App]
      │  HTTP
      ▼
  [Nginx :80]          ← 단일 진입점, 리버스 프록시
      │
      ▼
[Spring Boot :8080]    ← 메인 오케스트레이터
   │         │
   │         └──── HTTP ──── [Python Embed :8000]
   │                               │
   │                         비문 임베딩 변환
   │                               │
   │                         ┌─────▼──────┐
   │                         │   Qdrant   │  벡터 유사도 검색
   │                         │  :6333     │
   │                         └────────────┘
   │
   └──── JDBC ──── [MySQL :3306]   ← 관계형 데이터 저장소
```

외부에 노출되는 포트는 Nginx의 80(또는 443)뿐입니다.  
Spring Boot, Python, MySQL, Qdrant는 Docker 내부 네트워크에서만 통신합니다.

---

## 서비스별 책임

### Spring Boot (`spring-api`)

- **메인 오케스트레이터.** 모든 비즈니스 로직을 여기서 처리합니다.
- 사용자 인증/인가 (JWT)
- 강아지 등록, 입양 게시글 관리, 신고 처리
- MySQL 읽기/쓰기
- Python 임베딩 서비스 호출 및 Qdrant 벡터 저장/검색 호출
- 이미지 업로드 처리 (`uploads/` 볼륨)

### Python Embed (`python-embed`)

- **임베딩 전용 서비스.** 비즈니스 로직은 없습니다.
- 입력: 비문 이미지 (multipart 또는 바이트)
- 출력: 고정 차원 임베딩 벡터 (JSON)
- Spring Boot로부터만 호출됩니다. Flutter에서 직접 접근하지 않습니다.

### Qdrant

- **벡터 검색 전용.** Spring Boot가 직접 HTTP API로 호출합니다.
- 비문 임베딩 저장 및 top-K 유사도 검색
- 데이터 원본(Source of Truth)은 MySQL이며, Qdrant는 검색 인덱스 역할입니다.
- 자세한 내용은 [DB_VECTOR_ROLE.md](DB_VECTOR_ROLE.md)를 참고하세요.

### MySQL

- **관계형 데이터의 Source of Truth.**
- 사용자, 강아지, 입양 게시글, 인증 로그 등 모든 도메인 데이터를 보관합니다.

### Nginx

- 클라이언트 요청을 `spring-api`로 프록시합니다.
- `client_max_body_size` 설정으로 이미지 업로드 크기를 제한합니다.
- 향후 HTTPS 설정 시 이 레이어에서 SSL을 종료합니다.

---

## 주요 흐름

### 강아지 비문 등록

```
Flutter → POST /api/dogs/register (이미지 포함)
  → Spring Boot: 이미지 저장 (uploads 볼륨)
  → Spring Boot → Python Embed: POST /embed (이미지 바이트)
  ← Python Embed: { "vector": [...] }
  → Spring Boot → Qdrant: PUT /collections/dog_nose_embeddings/points
  → Spring Boot → MySQL: INSERT INTO dogs ...
  ← Spring Boot → Flutter: { "dog_id": "...", "status": "registered" }
```

### 비문 인증 (동일 개체 확인)

```
Flutter → POST /api/dogs/verify (이미지 포함)
  → Spring Boot → Python Embed: 임베딩 변환
  → Spring Boot → Qdrant: 유사도 검색 (top-K)
  → Spring Boot: 결과 해석, MySQL에서 개체 정보 조회
  ← Spring Boot → Flutter: { "matched": true, "dog": {...} }
```

---

## 운영 원칙

- Spring Boot가 Python과 Qdrant를 호출하는 유일한 주체입니다. Flutter는 Spring Boot만 바라봅니다.
- MySQL과 Qdrant 동기화 실패 처리 방식은 [DB_VECTOR_ROLE.md](DB_VECTOR_ROLE.md)를 따릅니다.
- 모든 서비스는 Docker Compose 내부 네트워크로 격리됩니다.
- 환경별 설정은 `compose.dev.yaml` / `compose.prod.yaml`로 오버라이드합니다.
