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
| MySQL 연결 | 구현됨 (Flyway 마이그레이션, JPA ddl-auto: none) |
| DB 마이그레이션 | Flyway 활성화 — `db/migration/V1__baseline.sql` (초기 7개 테이블) |
| Python embed 클라이언트 | 구현됨 (`EmbedClient`) |
| Qdrant 컬렉션 초기화 | 구현됨 (`QdrantInitializer`) |
| Dev 테스트 엔드포인트 | 구현됨 (`/api/dev/*`, **dev profile 전용**) |
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
# Java 21 필수 (로컬 JAVA_HOME=Java21 확인 후 실행)
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
# Java 21 필수 (CI: actions/setup-java@v4 java-version=21)
gradle test --no-daemon --stacktrace
# H2 인메모리 DB 사용 — MySQL 없이 실행 가능
# test profile: Flyway 비활성화, DevController 로드 안 함 (@Profile("dev") 전용)
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
| `UPLOAD_BASE_PATH` | 이미지 저장 루트 경로 (`uploads_data` 볼륨 마운트 경로) | `/var/uploads` |
| `MAX_UPLOAD_SIZE_MB` | 단일 파일 최대 크기 (Nginx, Spring 동일 적용) | `20` |

---

## Dev 엔드포인트

> `[DEV ONLY]` — `@Profile("dev")` 적용됨. `SPRING_PROFILES_ACTIVE=dev` 일 때만 활성화됩니다.  
> prod 환경(`SPRING_PROFILES_ACTIVE=prod`)에서는 이 컨트롤러가 로드되지 않습니다.

| 경로 | 설명 |
|---|---|
| `GET /api/dev/ping` | 기동 확인 |
| `GET /api/dev/embed-ping` | Python embed 서비스 연결 확인 |
| `POST /api/dev/embed-sample` | 이미지 업로드 후 임베딩 결과 반환 |
| `GET /api/dev/qdrant-config` | Qdrant 설정 값 확인 |
| `GET /actuator/health` | Spring 공식 health |

---

## 팀 최소 운영 규칙

- `/api/dev/*`는 개발 진단용이며 제품 기능 계약에 포함하지 않습니다.
- DB source of truth는 MySQL입니다. Qdrant는 검색 보조 데이터로 취급합니다.
- API/DB 스키마 변경 시 `docs/API_CONTRACTS/*`, `docs/TABLE_DRAFT.md` 동기화 후 PR 생성합니다.

## 파일 저장 경로

업로드된 이미지는 `${UPLOAD_BASE_PATH}` 아래에 다음 구조로 저장됩니다:

```
/var/uploads/
└── dogs/{dog_uuid}/{image_type}/{yyyyMMdd_HHmmss}_{name}.jpg
```

`dog_images.file_path` 컬럼에는 **볼륨 루트를 제외한 상대 경로**만 기록합니다.  
외부 URL: `GET /files/{relative_path}` (Nginx 직접 서빙)

자세한 정책: [docs/FILE_STORAGE_AND_URL_POLICY.md](../docs/FILE_STORAGE_AND_URL_POLICY.md)

---

> 추후 패키지 구조, API 엔드포인트 목록, 도메인 계층 설명을 추가할 예정입니다.
