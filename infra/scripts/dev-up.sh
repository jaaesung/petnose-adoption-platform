#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
COMPOSE_BASE="${DOCKER_DIR}/compose.yaml"
COMPOSE_DEV="${DOCKER_DIR}/compose.dev.yaml"

# .env 파일 존재 확인
if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] .env 파일이 없습니다."
  echo "  cp ${DOCKER_DIR}/.env.example ${ENV_FILE}"
  echo "  위 명령을 실행한 뒤 .env 값을 확인하세요."
  exit 1
fi

echo "[INFO] dev 환경 서비스를 시작합니다..."

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_BASE}" \
  -f "${COMPOSE_DEV}" \
  up -d --build

echo "[INFO] 서비스가 기동되었습니다."
echo "  접속:      http://localhost"
echo "  헬스체크:  bash infra/scripts/healthcheck.sh"
echo "  로그:      docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml logs -f"
