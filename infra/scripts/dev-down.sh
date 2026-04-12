#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
COMPOSE_BASE="${DOCKER_DIR}/compose.yaml"
COMPOSE_DEV="${DOCKER_DIR}/compose.dev.yaml"

echo "[INFO] dev 환경 서비스를 종료합니다..."

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_BASE}" \
  -f "${COMPOSE_DEV}" \
  down

echo "[INFO] 서비스가 종료되었습니다."
echo "  데이터 볼륨을 함께 삭제하려면: docker compose ... down -v"
