> 보관 문서(Archive)
>
> 이 문서는 과거 설계/초안 기록입니다.
> 현재 구현 기준으로 사용하지 마세요.
> 현재 기준은 `docs/README.md`와 `docs/PROJECT_KNOWLEDGE_INDEX.md`에서 시작하세요.
> active canonical 문서와 충돌하면 active canonical 문서가 우선합니다.

# 실전 레포 부트스트랩 체크리스트

실제 졸업작품 레포를 처음 만들 때 따라야 할 순서입니다.  
이 체크리스트는 연습 레포 구축 과정에서 겪은 시행착오를 바탕으로 작성되었습니다.

---

## Phase 0 — 환경 준비

- [ ] 로컬 Java 버전 확인: `java -version` → **Java 21** 이어야 함
  - 아니면 먼저 Java 21 설치 후 `JAVA_HOME` 설정
- [ ] Docker Desktop 설치 및 실행 확인: `docker compose version`
- [ ] Git 설치 확인: `git --version`
- [ ] GitHub CLI 인증: `gh auth login` (CI 로그 확인에 필요)
- [ ] GitHub에 빈 저장소 먼저 생성 (Initialize without README 권장)

---

## Phase 1 — 로컬 Git 초기화 (순서 엄수)

- [ ] **프로젝트 폴더를 먼저 생성**
  ```bash
  mkdir petnose-adoption-platform
  cd petnose-adoption-platform
  ```
- [ ] **폴더 안에서** `git init` 실행
  ```bash
  git init
  git rev-parse --show-toplevel  # 반드시 확인 — 프로젝트 루트여야 함
  ```
- [ ] `.gitignore` 작성 (`.env`, `*.jar`, `build/`, `__pycache__/` 등)
- [ ] `.gitattributes` 작성 (`* text=auto eol=lf`)
- [ ] GitHub remote 연결
  ```bash
  git remote add origin https://github.com/<user>/<repo>.git
  ```
- [ ] 초기 커밋 + push
  ```bash
  git add .gitignore .gitattributes
  git commit -m "chore: init repo"
  git push -u origin main
  ```

> **주의**: `git init`을 부모 디렉토리에서 실행하면 `.github/workflows/`가 저장소 루트에 위치하지 않아 GitHub Actions가 트리거되지 않습니다.

---

## Phase 2 — Monorepo 디렉토리 구조 생성

- [ ] 디렉토리 구조 생성
  ```
  backend/          # Spring Boot
  python-embed/     # Python FastAPI
  app/              # Flutter (또는 나중에)
  infra/
    docker/         # compose.yaml, .env.example
    nginx/          # nginx.conf, conf.d/
    scripts/        # dev-up.sh, dev-down.sh, healthcheck.sh
  docs/
  .github/
    workflows/
    ISSUE_TEMPLATE/
  ```
- [ ] 각 디렉토리에 최소한 `.gitkeep` 또는 `README.md` 배치

---

## Phase 3 — 우선 작성할 문서

- [ ] `README.md` — 프로젝트 개요, 빠른 시작, 디렉토리 구조
- [ ] `docs/ARCHITECTURE.md` — 서비스 구조 다이어그램
- [ ] `infra/docker/.env.example` — **모든 필요한 키를 주석과 함께** 작성
- [ ] `docs/TEAM_ONBOARDING.md` — 팀원 최초 실행 가이드

---

## Phase 4 — 인프라 설정 작성

- [ ] `infra/docker/compose.yaml` 작성 (기본 서비스 정의)
  - [ ] healthcheck를 **이미지에 있는 도구**로만 작성 (curl 없는 이미지 주의)
  - [ ] MySQL healthcheck: `CMD-SHELL` 에서 컨테이너 변수는 `$$VAR` 사용
  - [ ] `SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-dev}` 기본값 포함
- [ ] `infra/docker/compose.dev.yaml` 작성
  - [ ] dev에서만 열 포트 명시
  - [ ] `SPRING_PROFILES_ACTIVE: dev` 명시 오버라이드
- [ ] `infra/docker/compose.prod.yaml` 작성
  - [ ] `SPRING_PROFILES_ACTIVE: prod` 명시
  - [ ] 내부 서비스 포트 `ports: []` 닫기
- [ ] `infra/nginx/nginx.conf`, `conf.d/default.conf` 작성
- [ ] `infra/scripts/dev-up.sh`, `dev-down.sh`, `healthcheck.sh` 작성 (실행 권한 포함)
- [ ] `infra/scripts/dev-up.ps1`, `dev-down.ps1`, `healthcheck.ps1` 작성 (Windows 지원)

---

## Phase 5 — Backend 최소 실행물 작성

- [ ] `backend/build.gradle.kts` 작성
  - [ ] `sourceCompatibility = JavaVersion.VERSION_21` — **로컬 Java 버전과 일치 확인**
  - [ ] Spring Boot 3.x: `dialect` 명시 **하지 않음** (자동 감지)
  - [ ] `testLogging { exceptionFormat = FULL }` 포함
- [ ] `backend/src/main/resources/application.yml` 작성
  - [ ] `spring.application.name` 포함
  - [ ] dialect 설정 제거
- [ ] `backend/src/test/resources/application.yml` 작성
  - [ ] H2 in-memory DB 설정
  - [ ] `spring.application.name` 포함 (placeholder 해석 실패 방지)
- [ ] dev 전용 컨트롤러/Bean에 **처음부터** `@Profile("dev")` 붙이기
- [ ] `@Value`에 **항상** `:defaultValue` 기본값 포함
- [ ] `PetNoseApplicationTests.contextLoads()` 작성 + 로컬 통과 확인

---

## Phase 6 — Python Embed 최소 실행물 작성

- [ ] `python-embed/app/main.py` 작성 (FastAPI, `/health`, `/embed`)
- [ ] `python-embed/Dockerfile` 작성
  - [ ] HEALTHCHECK: `curl` 없는 경우 `python -c "import urllib.request..."` 사용
- [ ] `python-embed/requirements.txt` 작성

---

## Phase 7 — 로컬 실행 검증

- [ ] `infra/docker/.env.example` → `.env` 복사
- [ ] `docker compose config` — 오류 없이 통과
- [ ] `bash infra/scripts/dev-up.sh` — 5개 서비스 모두 `healthy`
- [ ] `bash infra/scripts/healthcheck.sh` — 모든 항목 `[OK]`
- [ ] `cd backend && gradle test --no-daemon` — `contextLoads()` 통과 (Java 21 필요)
- [ ] `curl http://localhost/actuator/health` 응답 확인
- [ ] `curl http://localhost:8080/api/dev/ping` 응답 확인 (dev profile)

---

## Phase 8 — GitHub Actions CI 작성 및 검증

- [ ] `.github/workflows/ci.yaml` 작성
  - [ ] `backend-test` job: `actions/setup-java@v4` (java 21) + `gradle test --no-daemon --stacktrace`
  - [ ] `python-smoke` job: import 확인
  - [ ] `docker-build` job: 양쪽 이미지 빌드 확인
- [ ] `develop` 브랜치 push 또는 PR → CI 트리거 확인
- [ ] **3개 job 모두 통과** 확인 (`gh run watch` 또는 GitHub UI)
- [ ] 실패 시: `gh run view --log-failed` 로 로그 확인

---

## Phase 9 — Branch Protection 설정

- [ ] GitHub UI: Settings → Branches → Add rule (`main`)
  - [ ] Require pull request before merging
  - [ ] Require status checks: `CI / Spring Boot 테스트`, `CI / Python smoke 테스트`, `CI / Docker 이미지 빌드 확인`
  - [ ] Require 1 approval
- [ ] `develop` 브랜치 생성 및 push

---

## Phase 10 — 팀원 온보딩 검증

- [ ] 팀원 1명에게 fresh clone 요청
- [ ] `docs/TEAM_ONBOARDING.md`만 보고 실행 가능한지 확인
- [ ] 막히는 지점 기록 → 문서 보완
- [ ] `.env.example`의 모든 키가 실제로 필요한 것인지 재확인

---

## 완료 기준 체크

| 항목 | 기준 |
|------|------|
| CI | main/develop push 시 3개 job 모두 green |
| 로컬 실행 | fresh clone 후 10분 이내에 서비스 기동 가능 |
| 문서 | README만 읽고 실행 흐름 이해 가능 |
| Profile | dev/prod/test 환경이 각각 올바르게 격리 |
| 보안 | `.env`가 git에 포함되지 않음 |
