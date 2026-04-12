# DB 마이그레이션 전략 (Flyway)

---

## 왜 Flyway를 도입하는가

JPA `ddl-auto: update`는 개발 초기에는 편리하지만 다음 문제를 일으킵니다.

| 문제 | 설명 |
|------|------|
| 컬럼 삭제/이름 변경 불가 | `update`는 컬럼 추가만 처리. 삭제·이름 변경은 무시됨 |
| 실행 순서 비결정적 | 엔티티 로딩 순서에 따라 DDL 순서가 달라질 수 있음 |
| 이력 추적 불가 | 스키마가 언제, 왜, 어떻게 바뀌었는지 알 수 없음 |
| 팀 환경 불일치 | 팀원마다 로컬 DB 상태가 달라질 수 있음 |

Flyway는 SQL 파일로 스키마 변경을 버전 관리하여 위 문제를 해결합니다.

---

## 현재 baseline 수준

**V1__baseline.sql** — 초기 7개 테이블 생성

| 테이블 | 설명 |
|--------|------|
| `users` | 사용자 계정 (email, 역할, 활성여부) |
| `shelter_profiles` | 보호소/분양자 프로필 (users 1:1) |
| `dogs` | 강아지 개체 (UUID PK, Qdrant point_id 포함) |
| `dog_images` | 비문/일반 이미지 경로 |
| `auth_logs` | 비문 인증 시도 이력 + 유사도 점수 |
| `adoption_posts` | 입양 게시글 |
| `reports` | 부적절 콘텐츠 신고 |

> **이 파일은 확정 스키마가 아닙니다.**  
> 현재는 개발 기반을 마련하는 baseline입니다. 엔티티 구현 시 V2 이후 파일로 보완합니다.

---

## 마이그레이션 파일 위치 및 네이밍 규칙

```
backend/src/main/resources/db/migration/
├── V1__baseline.sql           ← 초기 baseline (현재)
├── V2__add_xxx.sql            ← 향후 추가 예시
└── V3__alter_dogs_xxx.sql     ← 향후 변경 예시
```

**파일명 규칙**: `V{버전}__{설명}.sql`

- 버전은 정수 또는 `1.1`, `1.2` 형태 사용 가능
- 설명은 영어 소문자 + 밑줄 (`add_column`, `create_index` 등)
- **한 번 적용된 파일은 절대 수정하지 않는다** — Flyway가 체크섬을 검증하므로 수정 시 오류 발생

---

## 앞으로 테이블 변경 절차

1. `backend/src/main/resources/db/migration/` 에 새 `.sql` 파일 추가
2. 버전 번호를 현재 최신보다 높게 설정 (`V2__`, `V3__` 등)
3. 로컬에서 `docker compose ... up` 실행 → Flyway 자동 적용 확인
4. `flyway_schema_history` 테이블에서 새 버전이 `SUCCESS` 상태인지 확인
5. PR에 SQL 파일 포함하여 팀 리뷰 후 merge

```sql
-- V2 예시: users 테이블에 nickname 컬럼 추가
ALTER TABLE users ADD COLUMN nickname VARCHAR(50) AFTER email;
```

---

## 환경별 스키마 관리 원칙

| 환경 | Flyway | JPA ddl-auto | 설명 |
|------|--------|--------------|------|
| **dev** (Docker Compose) | ✅ 활성화 | `none` | Flyway가 DDL 전담. JPA는 스키마 미관여 |
| **test** (CI, `gradle test`) | ❌ 비활성화 | `create-drop` | H2 in-memory로 엔티티 기반 자동 생성. Flyway MySQL SQL은 H2 비호환 |
| **prod** | ✅ 활성화 | `none` | 동일하게 Flyway 전담. 운영 DB에 직접 DDL 금지 |

### test에서 Flyway를 비활성화하는 이유

`V1__baseline.sql`은 MySQL 전용 구문(`ENGINE=InnoDB`, `utf8mb4_unicode_ci`)을 포함합니다.  
H2는 이를 지원하지 않으므로 `spring.flyway.enabled: false`로 비활성화합니다.  
테스트에서는 JPA `create-drop`이 H2에 맞는 스키마를 자동 생성합니다.

---

## JPA ddl-auto와 Flyway의 역할 분리

```
Flyway 책임:
  - 스키마 생성 (CREATE TABLE)
  - 컬럼 추가/삭제/변경 (ALTER TABLE)
  - 인덱스 생성/삭제
  - 초기 참조 데이터 삽입 (필요 시)

JPA 책임:
  - 엔티티 ↔ 테이블 매핑 (ORM)
  - SQL 쿼리 실행 (CRUD)
  - ❌ DDL 생성은 하지 않음 (ddl-auto: none)

향후 엔티티 구현 완료 후:
  ddl-auto: none → validate 로 변경 권장
  → JPA가 엔티티와 DB 스키마의 정합성을 시작 시 검증
```

---

## 기존 DB에서 Flyway를 처음 도입하는 경우

기존에 `ddl-auto: update`로 테이블이 생성된 DB에서 처음 Flyway를 켜면:

1. **V1__baseline.sql의 `IF NOT EXISTS`** 덕분에 기존 테이블은 건드리지 않음
2. Flyway가 `flyway_schema_history` 테이블을 새로 생성하고 V1을 기록
3. 이후 V2, V3 마이그레이션이 정상적으로 적용됨

> 만약 기존 스키마가 V1__baseline.sql과 달라서 Flyway 적용 후 문제가 생기면:  
> `spring.flyway.baseline-on-migrate=true` + `baseline-version=1` 을 사용해  
> 현재 상태를 V1로 간주하고 V2부터 적용하는 방식으로 전환할 수 있습니다.

---

## 파일 위치 요약

| 파일 | 용도 |
|------|------|
| `backend/src/main/resources/db/migration/V1__baseline.sql` | 초기 전체 스키마 |
| `backend/src/main/resources/application.yml` | Flyway 활성화, `ddl-auto: none` |
| `backend/src/test/resources/application.yml` | Flyway 비활성화, `ddl-auto: create-drop` |
| `backend/build.gradle.kts` | `flyway-core`, `flyway-mysql` 의존성 |
