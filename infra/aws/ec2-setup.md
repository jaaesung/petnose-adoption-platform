# EC2 배포 준비 가이드

> AWS EC2에 이 서비스를 처음 배포할 때 필요한 최소 준비사항을 정리합니다.

---

## 1. EC2 인스턴스 권장 사양

| 항목 | 권장값 |
|---|---|
| 인스턴스 타입 | t3.medium 이상 (메모리 4GB+) |
| OS | Ubuntu 22.04 LTS |
| 스토리지 | 30GB+ (이미지 업로드 및 DB 고려) |
| 탄력적 IP | 고정 IP 할당 권장 |

---

## 2. 필수 소프트웨어 설치

```bash
# 패키지 업데이트
sudo apt-get update && sudo apt-get upgrade -y

# Docker 설치 (공식 방법)
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker

# Docker Compose v2 확인 (Docker Desktop 포함 시 기본 포함)
docker compose version
```

---

## 3. 보안 그룹 / 방화벽 설정

인바운드 규칙에 다음 포트를 허용합니다.

| 포트 | 프로토콜 | 허용 대상 | 설명 |
|---|---|---|---|
| 22 | TCP | 내 IP | SSH |
| 80 | TCP | 0.0.0.0/0 | HTTP (Nginx) |
| 443 | TCP | 0.0.0.0/0 | HTTPS (향후) |

내부 서비스 포트(3306, 6333, 8080, 8000)는 외부에 열지 않습니다.

---

## 4. 프로젝트 배치 및 환경변수 설정

```bash
# 저장소 클론
git clone <repository-url> /opt/petnose
cd /opt/petnose

# 환경변수 설정
cp infra/docker/.env.example infra/docker/.env
nano infra/docker/.env  # prod 값으로 수정
```

`.env` 파일에서 반드시 변경해야 할 항목:
- `APP_ENV=prod`
- `SPRING_PROFILES_ACTIVE=prod`
- `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD`, `SPRING_DATASOURCE_PASSWORD` — 강력한 패스워드로 변경
- `SPRING_API_IMAGE`, `PYTHON_EMBED_IMAGE` — GHCR 배포 태그 지정
  (예: `main-latest`, `main-<sha7>`)
- (private GHCR 사용 시) `GHCR_USERNAME`, `GHCR_TOKEN` 설정

---

## 5. 디렉토리 준비

```bash
# 백업 디렉토리 생성
mkdir -p /opt/petnose/backups/mysql /opt/petnose/backups/uploads
```

볼륨(`mysql_data`, `qdrant_storage`, `uploads_data`)은 Docker가 자동 생성합니다.

---

## 6. 서비스 기동

```bash
cd /opt/petnose
bash infra/scripts/deploy.sh
```

---

## 7. 배포 흐름 (Canonical)

```bash
cd /opt/petnose
# 필요 시 배포 태그 변경 후
#   SPRING_API_IMAGE=ghcr.io/<owner>/petnose-spring-api:main-<sha7>
#   PYTHON_EMBED_IMAGE=ghcr.io/<owner>/petnose-python-embed:main-<sha7>
# 를 .env에 반영
bash infra/scripts/deploy.sh
```

`deploy.sh`는 다음 순서로 동작합니다.
- `docker compose pull`
- `docker compose up -d --no-build`
- post-deploy healthcheck(`http://localhost/actuator/health`, nginx 경유) 실패 시 즉시 종료

---

## 8. HTTPS 적용 (선택 — Certbot)

```bash
sudo apt-get install certbot python3-certbot-nginx -y
sudo certbot --nginx -d <your-domain.com>
```

HTTPS 설정 후 `infra/nginx/conf.d/default.conf`에 443 서버 블록을 추가하고  
`compose.prod.yaml`의 nginx 포트에 `443:443`을 추가합니다.

---

## 9. 자동 백업 설정 (cron)

```bash
# crontab 편집
crontab -e

# 매일 새벽 3시 백업
0 3 * * * /opt/petnose/infra/scripts/backup.sh >> /var/log/petnose-backup.log 2>&1
```
