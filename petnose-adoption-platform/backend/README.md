# backend — Spring Boot API

## 역할

플랫폼의 메인 오케스트레이터입니다.  
사용자 인증/인가, 강아지 등록, 비문 인증, 입양 게시글 관리 등 모든 비즈니스 로직을 처리합니다.  
MySQL, Qdrant, Python Embed 서비스와 통신합니다.

시스템 구조는 [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md)를 참고하세요.

---

## 기술 스택

- Java 21
- Spring Boot 3.2.x
- Spring Data JPA (MySQL 8.0)
- Spring Actuator
- WebFlux WebClient (Python embed 호출용)
- Lombok

---

## 현재 구현 상태

| 항목 | 상태 |
|---|---|
| 기동 및 actuator health | 구현됨 |
| MySQL 연결 | 구현됨 (JPA ddl-auto: update) |
| Python embed 클라이언트 | 구현됨 (`EmbedClient`) |
| Qdrant 컬렉션 초기화 | 구현됨 (`QdrantInitializer`) |
| Dev 테스트 엔드포인트 | 구현됨 (`/api/dev/*`) |
| 도메인 비즈니스 로직 | 추후 구현 예정 |

---

## 로컬 실행 (Docker Compose 권장)

```bash
# 루트에서
bash infra/scripts/dev-up.sh
```

단독 실행 (MySQL 별도 필요):

```bash
cd backend
# Gradle 8.7 필요 (gradle/actions/setup-gradle@v3 또는 직접 설치)
gradle bootRun
```

---

## 빌드

```bash
cd backend
gradle bootJar --no-daemon
# 결과: build/libs/petnose-api.jar
```

---

## 테스트 실행

```bash
cd backend
gradle test --no-daemon
# H2 인메모리 DB 사용 — MySQL 없이 실행 가능
```

---

## 주요 설정 환경변수

| 환경변수 | 설명 | 기본값 |
|---|---|---|
| `SPRING_DATASOURCE_URL` | MySQL JDBC URL | `jdbc:mysql://localhost:3306/petnose...` |
| `PYTHON_EMBED_URL` | Python embed 서비스 URL | `http://localhost:8000` |
| `QDRANT_HOST` | Qdrant 호스트 | `qdrant` |
| `QDRANT_PORT` | Qdrant 포트 | `6333` |
| `QDRANT_COLLECTION` | 사용할 컬렉션명 | `dog_nose_embeddings` |
| `QDRANT_VECTOR_DIM` | 벡터 차원 (Python과 일치 필요) | `128` |

---

## Dev 엔드포인트

> `[DEV ONLY]` — 도메인 구현 후 제거 또는 profile 분기 예정

| 경로 | 설명 |
|---|---|
| `GET /api/dev/ping` | 기동 확인 |
| `GET /api/dev/embed-ping` | Python embed 서비스 연결 확인 |
| `POST /api/dev/embed-sample` | 이미지 업로드 후 임베딩 결과 반환 |
| `GET /api/dev/qdrant-config` | Qdrant 설정 값 확인 |
| `GET /actuator/health` | Spring 공식 health |

---

> 추후 패키지 구조, API 엔드포인트 목록, 도메인 계층 설명을 추가할 예정입니다.
