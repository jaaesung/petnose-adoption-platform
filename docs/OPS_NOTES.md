# 운영 메모

---

## 로컬 실행

> **사전 조건:** Docker Desktop, Docker Compose v2  
> 백엔드를 직접 빌드/테스트하려면 **Java 21** 필요 (`java -version` 확인).

> **env/secrets 전략 전체**: [docs/ENV_STRATEGY.md](ENV_STRATEGY.md) 참고.  
> `.env.example`의 `[SECRET]` 항목(`MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`)은  
> 반드시 실제 값으로 교체하고 `MYSQL_PASSWORD`와 `SPRING_DATASOURCE_PASSWORD`는 동일하게 유지하세요.

### MySQL 포트 충돌 해결

로컬에 MySQL이 3306으로 실행 중이면 컨테이너 포트 충돌이 발생합니다.  
`infra/docker/.env`에서 `MYSQL_PORT=3307`로 변경하면 해결됩니다:

```dotenv
MYSQL_PORT=3307
```

### Linux / macOS (bash)

```bash
# 1. 환경변수 파일 준비 (최초 1회)
cp infra/docker/.env.example infra/docker/.env

# 2. 서비스 기동 (--build 포함)
bash infra/scripts/dev-up.sh

# 3. 상태 확인
bash infra/scripts/healthcheck.sh

# 4. 서비스 종료
bash infra/scripts/dev-down.sh
```

### Windows (PowerShell)

```powershell
# 1. 환경변수 파일 준비 (최초 1회)
Copy-Item infra\docker\.env.example infra\docker\.env

# 2. 서비스 기동
.\infra\scripts\dev-up.ps1

# 3. 서비스 종료
.\infra\scripts\dev-down.ps1
```

---

## 직접 compose 명령

```bash
# 기동
docker compose \
  --env-file infra/docker/.env \
  -f infra/docker/compose.yaml \
  -f infra/docker/compose.dev.yaml \
  up -d --build

# 종료
docker compose \
  --env-file infra/docker/.env \
  -f infra/docker/compose.yaml \
  -f infra/docker/compose.dev.yaml \
  down
```

---

## 로그 확인

```bash
# 전체 로그 (최근 100줄)
docker compose \
  --env-file infra/docker/.env \
  -f infra/docker/compose.yaml \
  -f infra/docker/compose.dev.yaml \
  logs --tail=100

# 특정 서비스 로그 (실시간)
docker compose ... logs -f spring-api
docker compose ... logs -f python-embed
docker compose ... logs -f nginx
docker compose ... logs -f mysql
```

---

## Health Check 확인 위치

| 서비스 | 주소 | 비고 |
|---|---|---|
| Nginx | `http://localhost/` | 공개 진입점 |
| Spring Boot | `http://localhost/actuator/health` | nginx 경유 |
| Spring Boot (직접) | `http://localhost:8080/actuator/health` | dev 포트 오픈 시 |
| Dev Ping | `http://localhost:8080/api/dev/ping` | 연결 확인용 |
| Python Embed | `http://localhost:8000/health` | dev 포트 오픈 시 |
| Qdrant | `http://localhost:6333/healthz` | dev 포트 오픈 시 |

---

## 장애 시 1차 확인 포인트

```bash
# 모든 컨테이너 상태 확인
docker compose --env-file infra/docker/.env \
  -f infra/docker/compose.yaml \
  -f infra/docker/compose.dev.yaml \
  ps
```

1. **MySQL 연결 실패 (spring-api 미기동)** — `.env`의 `SPRING_DATASOURCE_URL`, `MYSQL_PASSWORD` 확인
2. **502 Bad Gateway (Nginx)** — `spring-api` 컨테이너 상태 및 로그 확인
3. **임베딩 실패** — `python-embed` 컨테이너 로그 확인, `EMBED_MODEL=mock-v1` 여부 확인
4. **Qdrant 연결 실패** — `qdrant` 컨테이너 상태, `http://localhost:6333/healthz` 응답 확인
5. **spring-api 기동 지연** — `start_period: 60s`이므로 최대 75초까지 대기

---

## 컨테이너/볼륨 주의사항

- 데이터 볼륨 (`mysql_data`, `qdrant_storage`, `uploads_data`)은 `docker compose down`으로 삭제되지 않습니다.
- 완전 초기화: `docker compose ... down -v` (데이터 영구 삭제 주의)
- 볼륨 이름은 `petnose-adoption-platform_mysql_data` 형태로 생성됩니다 (프로젝트명 prefix).

---

## Python embed mock 모드

`.env`에 `EMBED_MODEL=mock-v1`이 설정되어 있으면 실제 모델 없이도 동작합니다.  
mock 모드에서는 이미지 SHA-256 해시 기반으로 재현 가능한 더미 벡터(128차원)를 반환합니다.  
실제 모델 적용 시 `python-embed/app/main.py`의 `_load_model()` 함수를 구현하세요.

---

## Dev 전용 엔드포인트 (`/api/dev/*`)

`DevController`는 `@Profile("dev")`로 격리되어 있습니다.

| 환경 | `SPRING_PROFILES_ACTIVE` | `/api/dev/*` 활성화 |
|------|--------------------------|---------------------|
| dev (로컬/개발 서버) | `dev` | ✅ 활성화 |
| test (CI `gradle test`) | `test` | ❌ 비활성화 |
| prod | `prod` | ❌ 비활성화 |

`compose.dev.yaml`에서 `SPRING_PROFILES_ACTIVE: dev`를 명시합니다.  
`compose.prod.yaml`에서 `SPRING_PROFILES_ACTIVE: prod`가 설정되어 있어 prod 배포 시 자동 격리됩니다.

---

## GitHub Branch Protection 권장

Settings → Branches → Add rule (대상: `main`):

- `main` 직접 push 금지 — PR을 통해서만 merge
- Status check 필수: `CI / Spring Boot 테스트` + `CI / Python smoke 테스트` + `CI / Docker 이미지 빌드 확인`
- 최소 1인 리뷰 승인 후 merge

> 현재는 설정되어 있지 않으므로 저장소 관리자가 GitHub UI에서 직접 설정해야 합니다.
