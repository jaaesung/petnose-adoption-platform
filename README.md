# PetNose Adoption Platform

강아지 비문(코 지문) 인식 기반 유기견 입양 플랫폼 — 졸업작품 monorepo.

---

## 프로젝트 개요

강아지 코 사진을 찍으면 비문 임베딩을 통해 동일 개체를 식별하고, 유기견과 보호자를 연결하는 입양 플랫폼입니다.  
Flutter 앱 → Spring Boot API → Python 임베딩 서비스 → Qdrant 벡터 검색의 파이프라인으로 동작합니다.

## 목표

- 비문 기반 개체 식별로 유기견 재회율 향상
- 졸업작품 수준의 완성도 있는 서비스 시연

## 시스템 구성 요소

| 서비스 | 역할 | 포트 |
|---|---|---|
| `nginx` | 리버스 프록시 | 80 |
| `spring-api` | 비즈니스 로직, 인증, DB 연동 | 8080 (내부) |
| `python-embed` | 비문 이미지 → 임베딩 벡터 변환 | 8000 (내부) |
| `mysql` | 사용자/강아지/입양 데이터 저장 | 3306 (내부) |
| `qdrant` | 벡터 유사도 검색 | 6333 (내부) |

전체 구조는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)를 참고하세요.

---

## 빠른 시작 (로컬 개발)

**사전 조건:** Docker Desktop, Docker Compose v2  
백엔드를 로컬에서 직접 빌드/테스트하려면 **Java 21** (`JAVA_HOME` 설정 필요).

```bash
# 1. 환경변수 파일 준비
cp infra/docker/.env.example infra/docker/.env
# .env 파일을 열어 필요한 값 수정

# 2. 서비스 기동
bash infra/scripts/dev-up.sh

# 3. 상태 확인
bash infra/scripts/healthcheck.sh

# 4. 서비스 종료
bash infra/scripts/dev-down.sh
```

앱 접속: `http://localhost` (Nginx → Spring API)

---

## 디렉토리 구조

```
petnose-adoption-platform/
├── app/                   # Flutter 앱
├── backend/               # Spring Boot API
├── python-embed/          # Python 비문 임베딩 서비스
├── infra/
│   ├── docker/            # compose.yaml, .env.example
│   ├── nginx/             # nginx.conf, conf.d/
│   ├── scripts/           # 운영 스크립트
│   └── aws/               # EC2 배포 가이드
├── docs/                  # 설계 문서
│   └── API_CONTRACTS/     # API 계약 문서
└── .github/               # CI/CD, PR/Issue 템플릿
```

---

## 문서 위치

| 문서 | 설명 |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 시스템 구조 및 서비스 책임 |
| [docs/DB_VECTOR_ROLE.md](docs/DB_VECTOR_ROLE.md) | MySQL / Qdrant 역할 분리 |
| [docs/TABLE_DRAFT.md](docs/TABLE_DRAFT.md) | DB 테이블 초안 |
| [docs/VECTOR_SCHEMA_DRAFT.md](docs/VECTOR_SCHEMA_DRAFT.md) | 벡터 컬렉션 스키마 초안 |
| [docs/BACKUP_PLAN.md](docs/BACKUP_PLAN.md) | 백업/복구 절차 |
| [docs/OPS_NOTES.md](docs/OPS_NOTES.md) | 운영 메모 |
| [docs/API_CONTRACTS/frontend-backend.md](docs/API_CONTRACTS/frontend-backend.md) | Flutter ↔ Spring API 계약 |
| [docs/API_CONTRACTS/spring-python.md](docs/API_CONTRACTS/spring-python.md) | Spring ↔ Python 계약 |
| [infra/aws/ec2-setup.md](infra/aws/ec2-setup.md) | EC2 배포 준비 |

---

## 저장소 clone 및 시작

```bash
git clone https://github.com/jaaesung/petnose-adoption-platform.git
cd petnose-adoption-platform
cp infra/docker/.env.example infra/docker/.env
# .env 열어서 로컬 환경에 맞게 수정 (MySQL 포트 충돌 시 MYSQL_PORT=3307)
bash infra/scripts/dev-up.sh
```

---

## GitHub Actions CI

`main` 또는 `develop` 브랜치로 push 하거나 PR을 열면 CI가 자동 실행됩니다.

- CI 워크플로: `.github/workflows/ci.yaml`
- 포함 job: `backend-test` / `python-smoke` / `docker-build`
- 결과 확인: `https://github.com/jaaesung/petnose-adoption-platform/actions`

---

## 팀 작업 규칙

- **브랜치 전략:** `main` (배포) / `develop` (통합) / `feature/*`, `fix/*`, `infra/*`, `docs/*` (작업)
- **PR:** `.github/pull_request_template.md` 형식을 따른다.
- **커밋 메시지:** `feat:`, `fix:`, `docs:`, `infra:`, `chore:` 등 prefix를 붙인다.
- **`.env` 파일은 절대 커밋하지 않는다.** `.env.example`만 관리한다.
- **서비스별 포트/환경변수명은 `compose.yaml`과 문서를 기준으로 통일한다.**
- 각 서비스 디렉토리의 README를 참고해 로컬 실행 방법을 확인한다.

## Branch Protection 권장 (GitHub 설정)

GitHub 저장소 Settings → Branches → Add rule (`main`) 에서 아래를 권장한다:

- `main` 직접 push 금지 (require PR)
- PR merge 전 CI 통과 필수 (`Status checks: CI`)
- 최소 1인 리뷰 승인 필요
