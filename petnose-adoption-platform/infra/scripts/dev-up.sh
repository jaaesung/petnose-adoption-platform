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

if [ $? -ne 0 ]; then
  echo "[ERROR] docker compose 실행에 실패했습니다. 위 로그를 확인하세요."
  exit 1
fi

echo "[INFO] 서비스가 기동되었습니다."
echo "  접속: http://localhost"
echo "  로그: docker compose -f ${COMPOSE_BASE} -f ${COMPOSE_DEV} logs -f"
