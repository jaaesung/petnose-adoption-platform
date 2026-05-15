# 팀원 온보딩 가이드

> 문서 성격: 보조 참고 문서(Task Reference)
>
> 새 팀원이 local/dev 환경을 준비하고 PR 전 확인 절차를 따라갈 때 읽는다.
> active canonical 문서와 충돌하면 active canonical 문서가 우선한다.

이 저장소를 처음 받은 팀원을 위한 최초 실행 가이드입니다.

## 환경 의미 (먼저 확인)

- `local dev`: 내 PC에서 기능 구현/디버깅
- `shared dev`: 팀 공용 dev 서버 배포 검증 (self-hosted runner)
- `prod`: 발표/시연용 수동 배포 (자동 배포 아님)

---

## 사전 조건

| 도구 | 버전 | 확인 명령 |
|------|------|-----------|
| Git | 2.x 이상 | `git --version` |
| Docker Desktop | 4.x 이상 (Compose v2 포함) | `docker compose version` |
| Java 21 (JDK) | 21.x | `java -version` |

> **Java 21 설치 방법**  
> - Windows: `winget install EclipseAdoptium.Temurin.21.JDK`  
> - macOS: `brew install --cask temurin@21`  
> - 설치 후 `JAVA_HOME` 환경변수가 Java 21을 가리키는지 확인

> **Docker Desktop 주의**  
> 실행 중이어야 합니다. 아이콘이 트레이에 있는지 확인하세요.

---

## 1단계: 저장소 clone

```bash
git clone https://github.com/jaaesung/petnose-adoption-platform.git
cd petnose-adoption-platform
```

---

## 2단계: 환경변수 파일 생성

```bash
# Linux / macOS
cp infra/docker/.env.example infra/docker/.env

# Windows (PowerShell)
Copy-Item infra\docker\.env.example infra\docker\.env
```

`.env` 파일을 열어 값을 확인하세요.  
**기본값으로도 로컬 실행이 가능합니다.** 단, 아래 항목은 상황에 따라 수정이 필요합니다.

| 항목 | 기본값 | 수정 필요한 경우 |
|------|--------|-----------------|
| `MYSQL_PORT` | `3306` | 로컬에 MySQL이 이미 실행 중이면 `3307`로 변경 |
| `MYSQL_PASSWORD` | `change_me_dev_password` | 팀 개발용 비밀번호로 변경 (`SPRING_DATASOURCE_PASSWORD`와 동일값 유지) |
| `MYSQL_ROOT_PASSWORD` | `change_me_root_password` | 팀 개발용 비밀번호로 변경 |
| `SPRING_DATASOURCE_PASSWORD` | `change_me_dev_password` | `MYSQL_PASSWORD`와 반드시 동일하게 유지 |

> **절대 `.env` 파일을 git commit하지 마세요.** `.gitignore`로 이미 제외되어 있습니다.  
> env/secrets 전략 전체 설명: [ENV_STRATEGY.md](ENV_STRATEGY.md)

---

## 3단계: 서비스 기동

```bash
# Linux / macOS
bash infra/scripts/dev-up.sh

# Windows (PowerShell)
.\infra\scripts\dev-up.ps1
```

처음 실행 시 Docker 이미지 빌드로 인해 **3~5분** 소요됩니다.  
이후 실행부터는 빠릅니다.

---

## 4단계: 상태 확인

```bash
# Linux / macOS
bash infra/scripts/healthcheck.sh

# Windows (PowerShell)
.\infra\scripts\healthcheck.ps1
```

모든 항목이 `[OK]`로 표시되면 정상입니다.

### 수동 확인 (브라우저 / curl)

| 주소 | 설명 |
|------|------|
| `http://localhost/` | Nginx 진입점 (nginx → spring-api) |
| `http://localhost/actuator/health` | Spring Boot 상태 (nginx 경유) |
| `http://localhost:8080/actuator/health` | Spring Boot 직접 확인 |
| `http://localhost:8080/api/dev/ping` | dev profile 전용 ping |
| `http://localhost:8000/health` | Python embed 서비스 |
| `http://localhost:6333/healthz` | Qdrant 벡터 DB |

> `spring-api`는 기동 후 healthcheck가 안정화되기까지 최대 75초 소요됩니다.  
> 처음 실행 시 nginx가 `502`를 잠시 반환하는 것은 정상입니다.

---

## 5단계: 로그 확인

```bash
# 전체 서비스 로그
docker compose \
  --env-file infra/docker/.env \
  -f infra/docker/compose.yaml \
  -f infra/docker/compose.dev.yaml \
  logs --tail=100

# 특정 서비스만
docker compose ... logs -f spring-api
docker compose ... logs -f python-embed
docker compose ... logs -f mysql
```

---

## 서비스 종료

```bash
# Linux / macOS
bash infra/scripts/dev-down.sh

# Windows (PowerShell)
.\infra\scripts\dev-down.ps1

# 데이터 볼륨까지 삭제 (초기화)
docker compose \
  --env-file infra/docker/.env \
  -f infra/docker/compose.yaml \
  -f infra/docker/compose.dev.yaml \
  down -v
```

---

## 자주 막히는 문제

> 공용 dev 배포 실패 1차 진단은 [OPS_NOTES.md](OPS_NOTES.md)의
> `실패 모드별 1차 진단 루틴` 섹션을 우선 확인하세요.

### Java 버전 불일치 (`invalid source release: 21`)

```
error: invalid source release: 21
```

로컬 `java -version`이 21 미만입니다. Java 21 JDK를 설치하고 `JAVA_HOME`을 설정하세요.  
Docker로 실행하는 경우에는 Java 설치가 없어도 됩니다 (Docker 안에서 처리).

---

### Docker Desktop이 실행 중이지 않음

```
docker: Cannot connect to the Docker daemon
```

Docker Desktop을 실행하세요. 트레이 아이콘에서 "Docker Desktop is running" 확인.

---

### MySQL 포트 충돌 (`address already in use: 0.0.0.0:3306`)

```
Error response from daemon: driver failed programming external connectivity
```

로컬에 MySQL이 3306으로 실행 중입니다.  
`infra/docker/.env`에서 `MYSQL_PORT=3307`로 변경 후 재기동하세요.

---

### `/api/dev/*` 엔드포인트가 404 또는 없는 경우

`DevController`는 `@Profile("dev")`로 격리되어 있습니다.  
`SPRING_PROFILES_ACTIVE=dev`일 때만 활성화됩니다.

- `.env`에 `SPRING_PROFILES_ACTIVE=dev` 확인
- `compose.dev.yaml`에서 `SPRING_PROFILES_ACTIVE: dev` 오버라이드 확인

---

### spring-api가 계속 `unhealthy` 상태인 경우

MySQL 기동 완료 전에 Spring Boot가 연결을 시도하면 발생합니다.  
`start_period: 60s` 설정으로 최대 75초 대기합니다. 잠시 기다린 뒤 다시 확인하세요.

```bash
# 컨테이너 상태 확인
docker compose ... ps
# spring-api 로그 확인
docker compose ... logs spring-api
```

---

### `(이미지 빌드 실패)` 경우

```bash
# 캐시 없이 재빌드
docker compose ... up -d --build --no-cache
```

---

## 팀 브랜치 규칙 요약

| 브랜치 | 용도 |
|--------|------|
| `main` | 안정 배포 기준. 직접 push 금지 — PR로만 merge |
| `develop` | 통합 브랜치. feature/fix 작업 완료 후 PR |
| `feature/*` | 신기능 개발 |
| `fix/*` | 버그 수정 |
| `infra/*` | 인프라/배포 설정 변경 |
| `docs/*` | 문서만 변경 |

---

## PR 전에 확인할 체크리스트

- [ ] `infra/docker/.env`가 커밋에 포함되지 않았는가
- [ ] `gradle test --no-daemon`이 로컬에서 통과하는가 (Java 21 필요)
- [ ] `docker compose config`가 오류 없이 통과하는가
- [ ] `.env.example`에 새로 추가한 환경변수가 반영되었는가
- [ ] 브랜치명이 규칙에 맞는가 (`feature/`, `fix/`, `infra/`, `docs/`)
- [ ] PR 제목과 설명이 `.github/pull_request_template.md` 형식을 따르는가

---

## 팀별 최소 규칙 (handoff)

- backend 팀
  - `@Profile("dev")` 엔드포인트를 제품 API로 사용하지 않습니다.
  - DB 기준값은 MySQL이며, 벡터 검색 결과는 Qdrant를 보조로 사용합니다.
  - 변경 후 최소 `gradle test --no-daemon --stacktrace` 실행.
- python 팀
  - 기본 `EMBED_MODEL=mock-v1` 계약을 깨지 않습니다.
  - 차원(`128`)과 응답 스키마 변경 시 backend 계약 문서를 먼저 갱신합니다.
- flutter 팀
  - 앱 코드는 아직 스캐폴드 단계입니다(README 초안 상태).
  - API base URL 하드코딩 금지, 환경별 분리만 먼저 적용합니다.
