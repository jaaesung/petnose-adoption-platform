# 환경변수 및 Secrets 전략

이 문서는 PetNose 저장소에서 환경변수와 비밀값을 어떻게 분리하고 관리하는지 설명합니다.

---

## 기본 원칙

1. **비밀값은 절대 커밋하지 않는다** — `.env` 파일은 `.gitignore` 로 제외되어 있습니다.
2. **환경(dev/prod)마다 다른 값을 사용한다** — 특히 비밀번호는 환경마다 다르게 설정합니다.
3. **키 이름은 dev/prod 공통으로 사용한다** — 동일한 키 이름으로 값만 바꿔 주입합니다.
4. **로컬에서만 필요한 값은 서버에 올리지 않는다** — 포트 노출 설정 등은 로컬 전용입니다.

---

## 환경 의미 구분

- `local dev`: 개인 PC 개발 (`infra/docker/.env` + `compose.dev`)
- `shared dev`: 공용 dev 서버 검증 (`cd-dev.yaml`, self-hosted runner)
- `prod`: 수동 승인 기반 배포 (`cd-prod.yaml`)

---

## 환경변수 분류 기준

| 분류 | 설명 | 관리 위치 |
|------|------|-----------|
| **[SECRET]** | 비밀번호, SSH 키 등 — 절대 커밋 금지 | 서버 `.env`, GitHub Secrets |
| **[COMMON]** | dev/prod 공통 설정 — 비밀 아님 | `.env.example` 기반, 환경별 값 조정 |
| **[DEV]** | 로컬 개발 전용 — 포트 노출 등 | `.env` (로컬), 서버 배포 시 불필요 |

---

## 전체 환경변수 목록 및 분류

### MySQL / 데이터베이스

| 변수 | 분류 | 설명 | 사용처 |
|------|------|------|--------|
| `MYSQL_DATABASE` | COMMON | DB 이름 | compose.yaml → mysql |
| `MYSQL_USER` | COMMON | 앱 접속 계정명 (= `SPRING_DATASOURCE_USERNAME`) | compose.yaml → mysql |
| `MYSQL_PASSWORD` | **SECRET** | 앱 접속 비밀번호 (= `SPRING_DATASOURCE_PASSWORD`) | compose.yaml → mysql |
| `MYSQL_ROOT_PASSWORD` | **SECRET** | MySQL root 비밀번호 | compose.yaml → mysql |
| `MYSQL_PORT` | DEV | 로컬 노출 포트 (충돌 시 3307) | compose.dev.yaml |

> `MYSQL_PASSWORD`와 `SPRING_DATASOURCE_PASSWORD`는 **같은 값**이어야 합니다.  
> `MYSQL_USER`와 `SPRING_DATASOURCE_USERNAME`도 **같은 값**이어야 합니다.

### Spring Boot

| 변수 | 분류 | 설명 | 사용처 |
|------|------|------|--------|
| `SPRING_PROFILES_ACTIVE` | COMMON | `dev` 또는 `prod` | compose.yaml, compose.dev.yaml, compose.prod.yaml |
| `SPRING_DATASOURCE_URL` | COMMON | MySQL JDBC URL | compose.yaml → spring-api |
| `SPRING_DATASOURCE_USERNAME` | COMMON | DB 사용자명 (= `MYSQL_USER`) | compose.yaml → spring-api |
| `SPRING_DATASOURCE_PASSWORD` | **SECRET** | DB 비밀번호 (= `MYSQL_PASSWORD`) | compose.yaml → spring-api |
| `SPRING_APP_PORT` | DEV | 로컬 노출 포트 | compose.dev.yaml |

### Python Embed 서비스

| 변수 | 분류 | 설명 | 사용처 |
|------|------|------|--------|
| `PYTHON_EMBED_URL` | COMMON | Spring → Python 내부 URL | compose.yaml → spring-api |
| `PYTHON_EMBED_PORT` | DEV | 로컬 노출 포트 | compose.dev.yaml |
| `EMBED_MODEL` | COMMON | 모델 선택 (`mock-v1` 또는 실제 모델명) | compose.yaml → python-embed |
| `QDRANT_VECTOR_DIM` | COMMON | 벡터 차원 (Python에서 `EMBED_VECTOR_DIM`으로 전달됨) | compose.yaml → python-embed |
| `MAX_UPLOAD_SIZE_MB` | COMMON | 최대 업로드 크기 (`MAX_IMAGE_BYTES`로 변환 전달됨) | compose.yaml → python-embed |

> compose.yaml에서 변수명 변환:  
> `QDRANT_VECTOR_DIM` → `EMBED_VECTOR_DIM` (python-embed가 읽는 이름)  
> `MAX_UPLOAD_SIZE_MB` × 1,000,000 → `MAX_IMAGE_BYTES`

### Qdrant

| 변수 | 분류 | 설명 | 사용처 |
|------|------|------|--------|
| `QDRANT_HOST` | COMMON | 내부 호스트명 (`qdrant` 고정) | compose.yaml → spring-api |
| `QDRANT_PORT` | COMMON/DEV | 내부 6333 고정, 로컬 노출에도 사용 | compose.yaml, compose.dev.yaml |
| `QDRANT_COLLECTION` | COMMON | 벡터 컬렉션 이름 | compose.yaml → spring-api |
| `QDRANT_VECTOR_DIM` | COMMON | 벡터 차원 | compose.yaml → spring-api, python-embed |

### 파일 업로드 / Nginx

| 변수 | 분류 | 설명 | 사용처 |
|------|------|------|--------|
| `UPLOAD_BASE_PATH` | COMMON | 업로드 저장 경로 | compose.yaml → spring-api, python-embed |
| `MAX_UPLOAD_SIZE_MB` | COMMON | 최대 업로드 크기 (MB) | compose.yaml → spring-api, python-embed |
| `NGINX_PORT` | DEV | 로컬 Nginx 노출 포트 | compose.yaml |

> **주의**: `infra/nginx/conf.d/default.conf`의 `client_max_body_size`는 현재 **하드코딩(20m)**되어 있습니다.  
> `MAX_UPLOAD_SIZE_MB` 값을 변경할 경우 nginx conf도 함께 수정해야 합니다.

### CD (배포 자동화)

| 변수 | 분류 | 설명 | 관리 위치 |
|------|------|------|-----------|
| `PROD_SSH_KEY` | **SECRET** | prod 서버 SSH 개인키 | GitHub Actions Secrets |
| `PROD_USER` | COMMON | prod 서버 SSH 사용자명 | GitHub Actions Secrets |
| `PROD_HOST` | COMMON | prod 서버 IP 또는 도메인 | GitHub Actions Secrets |

> dev CD(`cd-dev.yaml`)는 SSH secrets 대신 self-hosted runner(`self-hosted`, `Linux`, `X64`, `petnose-dev`)에서 서버 로컬 배포를 수행합니다.

### 배포 이미지 / GHCR 인증 (서버 런타임)

| 변수 | 분류 | 설명 | 사용처 |
|------|------|------|--------|
| `SPRING_API_IMAGE` | COMMON | 배포할 Spring API 이미지 태그 | compose.prod.yaml |
| `PYTHON_EMBED_IMAGE` | COMMON | 배포할 Python Embed 이미지 태그 | compose.prod.yaml |
| `GHCR_USERNAME` | SECRET (optional) | GHCR login 사용자명 | `infra/scripts/deploy.sh` |
| `GHCR_TOKEN` | SECRET (optional) | GHCR login 토큰(read:packages) | `infra/scripts/deploy.sh` |

> Canonical deploy path: 서버에서 소스 빌드하지 않고 GHCR 이미지를 pull 하여 배포합니다.

### Dev CD (current practical requirements)

`cd-dev.yaml` currently enforces fail-fast checks before local deploy on dev runner:
- strict `image_tag` format for dev deploy (`develop-latest` or `develop-<sha7>`)
- GHCR manifest existence check for both images before deploy
- server deploy file hash match check (`infra/scripts/deploy.sh`, `infra/docker/compose.yaml`, `infra/docker/compose.prod.yaml`)
- local preflight (`/opt/petnose`, `.env`, Docker/Compose, compose config)
- one-run env override for image tags (no persistent rewrite of server `.env` image keys)

GitHub Secrets required for one real dev validation:
1. 없음 (dev CD는 self-hosted runner 기반)

Server prerequisites required for one real dev validation:
1. `/opt/petnose` exists with this repository deployed
2. `/opt/petnose/infra/docker/.env` exists and is runtime-correct
3. Docker engine + docker compose plugin are installed for the runner service user
4. Server can pull target GHCR tags
5. If GHCR packages are private: set `GHCR_USERNAME` and `GHCR_TOKEN` on server `.env`

---

## 환경별 관리 위치

### 로컬 개발 (`dev`)

```
infra/docker/.env          ← .gitignore 로 제외됨, 팀원 각자 생성
infra/docker/.env.example  ← 커밋됨, 키 목록과 설명만 포함
```

- 팀원은 `.env.example` → `.env` 복사 후 `[SECRET]` 값만 수정합니다.
- 로컬 dev 비밀번호는 간단해도 됩니다 (`change_me_dev_password` 등).

### GitHub Actions CI

```
현재 CI는 H2 in-memory DB를 사용하므로 외부 비밀값이 필요 없습니다.
GitHub Actions Secrets 설정이 현재 필요하지 않습니다.
```

### GitHub Actions CD

GitHub 저장소 → Settings → Secrets and variables → Actions 에서 설정:

```
PROD_SSH_KEY     ← prod 서버 SSH 개인키
PROD_USER        ← SSH 사용자명
PROD_HOST        ← prod 서버 주소
```

`cd-dev.yaml`은 자동(이미지 publish 성공 + develop) 또는 수동(`workflow_dispatch`)으로 실행할 수 있습니다.
수동 실행 시 `image_tag` 입력값(예: `develop-latest`, `develop-a1b2c3d`)을 사용합니다.
`cd-dev.yaml`은 self-hosted runner에서 `/opt/petnose` 로컬 preflight 후 `infra/scripts/deploy.sh`를 실행합니다.
배포 이미지 태그는 해당 run에서만 env override(`SPRING_API_IMAGE`, `PYTHON_EMBED_IMAGE`)로 주입합니다.

### 서버 런타임 (dev 서버 / prod 서버)

서버 내 `/opt/petnose/infra/docker/.env` 파일 또는 시스템 환경변수로 관리:

```
SPRING_PROFILES_ACTIVE=prod          ← (dev 서버면 dev)
MYSQL_DATABASE=petnose
MYSQL_USER=petnose
MYSQL_PASSWORD=<강력한_비밀번호>      ← [SECRET] 각 서버별 고유 값
MYSQL_ROOT_PASSWORD=<root_비밀번호>   ← [SECRET]
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/petnose?...
SPRING_DATASOURCE_USERNAME=petnose
SPRING_DATASOURCE_PASSWORD=<강력한_비밀번호>  ← MYSQL_PASSWORD 와 동일
EMBED_MODEL=mock-v1                   ← 실제 모델 적용 시 변경
QDRANT_VECTOR_DIM=128
SPRING_API_IMAGE=ghcr.io/jaaesung/petnose-spring-api:main-latest
PYTHON_EMBED_IMAGE=ghcr.io/jaaesung/petnose-python-embed:main-latest
# GHCR private 패키지 사용 시에만 필요
GHCR_USERNAME=<github_username>
GHCR_TOKEN=<read_packages_token>
... (나머지 COMMON 값들)
```

서버에서는 `[DEV]` 분류 변수(`MYSQL_PORT`, `PYTHON_EMBED_PORT`, `SPRING_APP_PORT`)는 불필요합니다.

---

## 절대 커밋하면 안 되는 값

```
infra/docker/.env                ← .gitignore 확인 필수
MYSQL_PASSWORD (실제 값)
MYSQL_ROOT_PASSWORD (실제 값)
SPRING_DATASOURCE_PASSWORD (실제 값)
PROD_SSH_KEY (SSH 개인키)
```

---

## dev / prod 값 분리 원칙

| 항목 | dev | prod |
|------|-----|------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| `MYSQL_PASSWORD` | 간단한 개발용 비밀번호 | 강력한 비밀번호 (고유) |
| `MYSQL_ROOT_PASSWORD` | 간단한 개발용 비밀번호 | 강력한 비밀번호 (고유) |
| `EMBED_MODEL` | `mock-v1` | 실제 모델명 (구현 후) |
| DB 포트 노출 | `MYSQL_PORT=3306` 로컬 노출 | 포트 닫음 (`ports: []`) |
| DevController `/api/dev/*` | 활성화 | 비활성화 (`@Profile("dev")`) |

---

## 키 이름 불일치 주의사항

아래 변수 쌍은 **값이 동일해야** 합니다:

| 변수 A | 변수 B | 이유 |
|--------|--------|------|
| `MYSQL_USER` | `SPRING_DATASOURCE_USERNAME` | MySQL 컨테이너와 Spring 모두 같은 계정 사용 |
| `MYSQL_PASSWORD` | `SPRING_DATASOURCE_PASSWORD` | MySQL 컨테이너와 Spring 모두 같은 비밀번호 사용 |

Python embed 서비스는 compose.yaml에서 변수명을 변환하여 전달받습니다:

| `.env` 키 | Python이 실제로 읽는 키 | 변환 위치 |
|-----------|------------------------|-----------|
| `QDRANT_VECTOR_DIM` | `EMBED_VECTOR_DIM` | compose.yaml `EMBED_VECTOR_DIM: ${QDRANT_VECTOR_DIM:-128}` |
| `MAX_UPLOAD_SIZE_MB` | `MAX_IMAGE_BYTES` | compose.yaml `MAX_IMAGE_BYTES: ${MAX_UPLOAD_SIZE_MB:-20}000000` |
