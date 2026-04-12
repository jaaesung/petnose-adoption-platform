# Release Readiness — 팀원 배포 가능 여부 판단

이 문서는 현재 저장소가 "팀원이 clone 후 바로 개발을 시작할 수 있는 수준"인지 판단하기 위한 기준표입니다.  
작성 기준일: 2026-04-12

---

## 현재 완료된 것

### 인프라 / 실행 환경

- [x] Docker Compose 5개 서비스 (`mysql`, `qdrant`, `python-embed`, `spring-api`, `nginx`) 모두 healthcheck 통과
- [x] `compose.dev.yaml` — 개발용 포트 노출 + `SPRING_PROFILES_ACTIVE: dev` 명시
- [x] `compose.prod.yaml` — 내부 포트 닫기 + `SPRING_PROFILES_ACTIVE: prod` 설정
- [x] `infra/docker/.env.example` — 모든 필요한 키 포함, 주석 있음
- [x] MySQL 포트 충돌 대응: `MYSQL_PORT` 변경 가능
- [x] `dev-up.sh`, `dev-down.sh`, `healthcheck.sh` (Linux/macOS)
- [x] `dev-up.ps1`, `dev-down.ps1`, `healthcheck.ps1` (Windows)

### Backend

- [x] Spring Boot 3.2.5 + Java 21 기동 및 `actuator/health` 응답
- [x] MySQL 연결 (Flyway 마이그레이션, `ddl-auto: none`)
- [x] DB 스키마 baseline (`V1__baseline.sql` — 7개 테이블, `db/migration/`)
- [x] Python embed 클라이언트 (`EmbedClient`)
- [x] Qdrant 컬렉션 초기화 (`QdrantInitializer` — 연결 실패 시 경고만 출력)
- [x] `DevController` → `@Profile("dev")` 격리 완료
- [x] `contextLoads()` CI 통과

### Python Embed

- [x] FastAPI mock 서비스 (`/health`, `/embed`)
- [x] SHA-256 기반 재현 가능한 더미 벡터 (128차원)
- [x] Dockerfile HEALTHCHECK (urllib 사용, curl 불필요)

### CI / GitHub

- [x] GitHub Actions 3개 job 모두 통과: `backend-test`, `python-smoke`, `docker-build`
- [x] `gradle test --no-daemon --stacktrace` — 실패 시 스택트레이스 출력
- [x] `testLogging { exceptionFormat = FULL }` 추가

### 문서

- [x] `README.md` — 프로젝트 개요, 빠른 시작, CI 설명, 브랜치 전략
- [x] `docs/OPS_NOTES.md` — 실행 명령, 헬스체크, 장애 대응, dev profile 표, branch protection 권장
- [x] `backend/README.md` — 기술 스택, 실행 방법, dev 엔드포인트, profile 조건
- [x] `docs/TEAM_ONBOARDING.md` — 팀원 최초 실행 가이드
- [x] `docs/PRACTICE_RETROSPECTIVE.md` — 시행착오 회고
- [x] `docs/REAL_REPO_BOOTSTRAP_CHECKLIST.md` — 실전 부트스트랩 체크리스트
- [x] GitHub PR/Issue 템플릿

---

## 미완료 또는 주의가 필요한 것

### 기능 미구현 (의도적)

- [ ] **비즈니스 도메인 로직 없음** — 사용자/강아지/입양 관련 API 전혀 없음
- [ ] **실제 비문 임베딩 모델 없음** — `mock-v1` 모드만 동작
- [ ] **Flutter 앱 없음** — `app/` 디렉토리에 README만 있음
- [ ] **인증/인가 없음** — Spring Security 미적용

### 인프라 / 운영 주의사항

- [ ] **`compose.prod.yaml`의 `ports: []` 미검증** — 일부 Docker Compose 버전에서 빈 배열 merge 동작이 다를 수 있음. prod 배포 전 `docker compose config` 결과 검증 필요
- [ ] **secrets 관리 전략 없음** — 현재 `.env`에 평문 비밀번호. 실제 배포 시 Docker Secrets 또는 AWS Secrets Manager 필요
- [ ] **HTTPS 미적용** — `compose.prod.yaml` 주석에 `443:443` 언급만 있음. Certbot/Let's Encrypt 설정 없음
- [ ] **`gradle/actions/setup-gradle@v3`** — Node 20 deprecation 경고 발생. `@v4` 업그레이드 시 해소 가능하나 현재 미적용

### 로컬 환경 의존

- [ ] **로컬 Java 21 필요** — Docker만으로 서비스 기동 가능하지만, `gradle test` 로컬 실행에는 Java 21 필수
- [ ] **`develop` 브랜치 미동기** — 현재 `main`에만 최신 커밋 있음. `develop` 브랜치를 `main`과 동기화 필요

### GitHub 설정 미완료

- [ ] **Branch protection 규칙 미설정** — GitHub UI에서 직접 설정 필요 (문서에만 권장사항 기재)

---

## 팀원에게 배포 전 마지막 확인 항목

```
□ infra/docker/.env.example 을 .env로 복사 후 비밀번호 확인
□ MYSQL_PORT 충돌 여부 확인 (로컬 MySQL 3306 실행 중인지)
□ Docker Desktop 실행 중인지 확인
□ bash infra/scripts/dev-up.sh 실행 → 모든 서비스 healthy
□ bash infra/scripts/healthcheck.sh 실행 → 모든 항목 [OK]
□ http://localhost/ 접속 확인
□ http://localhost:8080/api/dev/ping 응답 확인
```

---

## 실제 졸업작품 레포로 옮길 때 다시 확인할 것

1. **JPA Entity 구현** — `docs/TABLE_DRAFT.md` 기반으로 엔티티 작성. 완료 후 `ddl-auto: none` → `validate` 전환 권장 ([docs/DB_MIGRATION_STRATEGY.md](DB_MIGRATION_STRATEGY.md) 참고)
2. **실제 비문 임베딩 모델** — `python-embed/app/main.py`의 `_load_model()` 구현 (PyTorch/ONNX 등)
3. **Spring Security 적용** — JWT 또는 OAuth2 기반 인증
4. **secrets 관리** — `.env` → Docker Secrets 또는 클라우드 비밀 관리 서비스
5. **HTTPS 설정** — Nginx + Certbot
6. **`compose.prod.yaml` 실제 검증** — `docker compose -f compose.yaml -f compose.prod.yaml config` 출력 검토
7. **branch protection 설정** — GitHub UI에서 `main` 보호 규칙 설정
8. **팀원 fresh clone 검증** — 팀원 1명에게 직접 TEAM_ONBOARDING.md 따라해 보도록 요청

---

## 현재 저장소 판정

| 항목 | 판정 | 비고 |
|------|------|------|
| 팀원 clone 후 서비스 기동 | ✅ 가능 | `.env` 복사 + Docker Desktop만 있으면 됨 |
| CI 통과 | ✅ green | 3개 job 모두 pass |
| dev/prod profile 격리 | ✅ 완료 | `@Profile("dev")`, compose 모두 일치 |
| 비즈니스 기능 | ❌ 없음 | mock 수준만 있음 (의도적) |
| 실제 배포 준비 | ⚠️ 미완 | HTTPS, secrets, 실제 모델 없음 |
| 졸업작품 베이스로 승격 | ⚠️ 조건부 | 인프라 골격은 사용 가능, 도메인 구현 필요 |

### 최종 판정: **팀원 온보딩 가능 (연습/개발 환경 기준)**

이 저장소는 **"팀원이 clone 후 개발을 시작할 수 있는 수준"** 에 도달했습니다.  
단, 실제 졸업작품 서비스로 바로 사용하기 위해서는 비즈니스 도메인 구현, 실제 임베딩 모델, 보안 설정이 추가로 필요합니다.

인프라 골격(Docker Compose, CI, profile 격리, 스크립트, 문서)은 실전 레포의 기반으로 그대로 활용할 수 있습니다.
