#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
COMPOSE_BASE="${DOCKER_DIR}/compose.yaml"
COMPOSE_DEV="${DOCKER_DIR}/compose.dev.yaml"

echo "=== 컨테이너 상태 ==="
docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_BASE}" \
  -f "${COMPOSE_DEV}" \
  ps

echo ""
echo "=== HTTP 헬스체크 ==="

check_http() {
  local label="$1"
  local url="$2"
  if curl -sf --max-time 5 "${url}" > /dev/null 2>&1; then
    echo "[OK]   ${label}  →  ${url}"
  else
    echo "[FAIL] ${label}  →  ${url}"
  fi
}

# Nginx 경유 확인
check_http "nginx      " "http://localhost/"

# nginx 경유 Spring actuator
check_http "nginx→spring" "http://localhost/actuator/health"

# Spring 직접 (dev 포트 오픈 시)
check_http "spring-api " "http://localhost:8080/actuator/health"

# Spring dev ping
check_http "dev-ping   " "http://localhost:8080/api/dev/ping"

# Python Embed (dev 포트 오픈 시)
check_http "python-embed" "http://localhost:8000/health"

# Qdrant (dev 포트 오픈 시)
check_http "qdrant     " "http://localhost:6333/healthz"

echo ""
echo "헬스체크 완료."
