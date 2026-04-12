#!/usr/bin/env bash
# Canonical deploy script (GHCR pull-based)
# - 서버에서 소스 빌드하지 않음
# - GHCR 이미지 pull 후 compose up
# - post-deploy healthcheck 실패 시 즉시 종료
#
# UNVERIFIED PLACEHOLDER:
#   - GHCR private pull 인증(GHCR_USERNAME/GHCR_TOKEN)
#   - 실제 서버 네트워크/방화벽 상태
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
COMPOSE_BASE="${DOCKER_DIR}/compose.yaml"
COMPOSE_PROD="${DOCKER_DIR}/compose.prod.yaml"

# .env 확인
if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] .env 파일이 없습니다."
  exit 1
fi

compose_prod() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_BASE}" \
    -f "${COMPOSE_PROD}" \
    "$@"
}

read_env_var() {
  local key="$1"
  local line
  line="$(grep -m1 "^${key}=" "${ENV_FILE}" || true)"
  if [ -z "${line}" ]; then
    return 0
  fi
  printf '%s' "${line#*=}" | sed 's/\r$//'
}

SPRING_IMAGE="$(read_env_var SPRING_API_IMAGE)"
PYTHON_IMAGE="$(read_env_var PYTHON_EMBED_IMAGE)"
GHCR_USER="$(read_env_var GHCR_USERNAME)"
GHCR_TOKEN_VALUE="$(read_env_var GHCR_TOKEN)"

echo "[INFO] 배포 대상 이미지"
echo "  SPRING_API_IMAGE=${SPRING_IMAGE:-<unset, compose.prod 기본값 사용>}"
echo "  PYTHON_EMBED_IMAGE=${PYTHON_IMAGE:-<unset, compose.prod 기본값 사용>}"

if [ -n "${GHCR_USER}" ] && [ -n "${GHCR_TOKEN_VALUE}" ]; then
  echo "[INFO] GHCR 로그인 시도..."
  printf '%s' "${GHCR_TOKEN_VALUE}" | docker login ghcr.io -u "${GHCR_USER}" --password-stdin
else
  echo "[WARN] GHCR_USERNAME/GHCR_TOKEN 미설정 — 기존 docker login 상태 또는 public 패키지를 가정합니다. (UNVERIFIED)"
fi

echo "[INFO] 이미지 pull..."
compose_prod pull

echo "[INFO] 서비스 기동/갱신 (--no-build)..."
compose_prod up -d --no-build

check_http_with_retry() {
  local label="$1"
  local url="$2"
  local attempts="${3:-20}"
  local sleep_sec="${4:-3}"

  local i
  for i in $(seq 1 "${attempts}"); do
    if curl -sf --max-time 5 "${url}" > /dev/null 2>&1; then
      echo "[OK]   ${label}  ->  ${url}"
      return 0
    fi
    echo "[WAIT] ${label} (${i}/${attempts})"
    sleep "${sleep_sec}"
  done

  echo "[FAIL] ${label}  ->  ${url}"
  return 1
}

echo "[INFO] post-deploy healthcheck..."
if ! check_http_with_retry "nginx" "http://localhost/" 20 3; then
  compose_prod ps
  compose_prod logs --tail=150 nginx spring-api
  exit 1
fi

if ! check_http_with_retry "spring-actuator-via-nginx" "http://localhost/actuator/health" 25 3; then
  compose_prod ps
  compose_prod logs --tail=200 spring-api nginx python-embed
  exit 1
fi

echo "[INFO] 배포 성공."
echo "  상태 확인: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_BASE} -f ${COMPOSE_PROD} ps"
echo "  로그 확인: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_BASE} -f ${COMPOSE_PROD} logs -f"
