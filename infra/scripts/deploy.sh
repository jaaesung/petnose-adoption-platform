#!/usr/bin/env bash
# Canonical deploy script (GHCR pull-based)
# - no source build on server
# - pull images from GHCR and run compose up
# - fail-fast on post-deploy healthcheck
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
COMPOSE_BASE="${DOCKER_DIR}/compose.yaml"
COMPOSE_PROD="${DOCKER_DIR}/compose.prod.yaml"

if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] Missing env file: ${ENV_FILE}"
  exit 1
fi

if [ ! -f "${COMPOSE_BASE}" ] || [ ! -f "${COMPOSE_PROD}" ]; then
  echo "[ERROR] Missing compose files: ${COMPOSE_BASE}, ${COMPOSE_PROD}"
  exit 1
fi

require_cmd() {
  local cmd="$1"
  command -v "${cmd}" > /dev/null 2>&1 || {
    echo "[ERROR] Required command not found: ${cmd}"
    exit 1
  }
}

require_cmd docker
require_cmd curl

if ! docker compose version > /dev/null 2>&1; then
  echo "[ERROR] docker compose plugin is not available."
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

SPRING_IMAGE_FILE="$(read_env_var SPRING_API_IMAGE)"
PYTHON_IMAGE_FILE="$(read_env_var PYTHON_EMBED_IMAGE)"
SPRING_IMAGE_EFFECTIVE="${SPRING_API_IMAGE:-${SPRING_IMAGE_FILE}}"
PYTHON_IMAGE_EFFECTIVE="${PYTHON_EMBED_IMAGE:-${PYTHON_IMAGE_FILE}}"
GHCR_USER="${GHCR_USERNAME:-$(read_env_var GHCR_USERNAME)}"
GHCR_TOKEN_VALUE="${GHCR_TOKEN:-$(read_env_var GHCR_TOKEN)}"

echo "[INFO] Deploy image targets"
echo "  SPRING_API_IMAGE=${SPRING_IMAGE_EFFECTIVE:-<unset, compose.prod default>}"
echo "  PYTHON_EMBED_IMAGE=${PYTHON_IMAGE_EFFECTIVE:-<unset, compose.prod default>}"

if [ -n "${SPRING_API_IMAGE:-}" ] || [ -n "${PYTHON_EMBED_IMAGE:-}" ]; then
  echo "[INFO] Image tag source: shell environment override"
else
  echo "[INFO] Image tag source: ${ENV_FILE} (or compose.prod defaults)"
fi

if [ -n "${GHCR_USER}" ] && [ -n "${GHCR_TOKEN_VALUE}" ]; then
  echo "[INFO] GHCR login attempt..."
  printf '%s' "${GHCR_TOKEN_VALUE}" | docker login ghcr.io -u "${GHCR_USER}" --password-stdin
elif [ -n "${GHCR_USER}" ] || [ -n "${GHCR_TOKEN_VALUE}" ]; then
  echo "[ERROR] Partial GHCR credentials. Set both GHCR_USERNAME and GHCR_TOKEN."
  exit 1
else
  echo "[WARN] GHCR_USERNAME/GHCR_TOKEN not set. Assuming existing docker login or public package visibility."
fi

echo "[INFO] Compose config validation..."
compose_prod config > /dev/null

echo "[INFO] Pull images..."
compose_prod pull

echo "[INFO] Compose up (--no-build)..."
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

# Canonical post-deploy healthcheck target:
# - actuator endpoint through nginx
# - do not gate deploy success on "/" because root 2xx is not guaranteed
HEALTHCHECK_LABEL="${DEPLOY_HEALTHCHECK_LABEL:-spring-actuator-via-nginx}"
HEALTHCHECK_URL="${DEPLOY_HEALTHCHECK_URL:-http://localhost/actuator/health}"
HEALTHCHECK_ATTEMPTS="${DEPLOY_HEALTHCHECK_ATTEMPTS:-25}"
HEALTHCHECK_SLEEP_SEC="${DEPLOY_HEALTHCHECK_SLEEP_SEC:-3}"

echo "[INFO] Post-deploy healthcheck..."
echo "  target: ${HEALTHCHECK_URL} (${HEALTHCHECK_LABEL})"
if ! check_http_with_retry "${HEALTHCHECK_LABEL}" "${HEALTHCHECK_URL}" "${HEALTHCHECK_ATTEMPTS}" "${HEALTHCHECK_SLEEP_SEC}"; then
  compose_prod ps
  compose_prod logs --tail=200 spring-api nginx python-embed
  exit 1
fi

echo "[INFO] Deploy success."
echo "  status: docker compose --env-file ${ENV_FILE} -f ${COMPOSE_BASE} -f ${COMPOSE_PROD} ps"
echo "  logs:   docker compose --env-file ${ENV_FILE} -f ${COMPOSE_BASE} -f ${COMPOSE_PROD} logs -f"
