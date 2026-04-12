#!/usr/bin/env bash
# prod 배포 초안 스크립트
# 실제 실행 전 .env 파일, secrets 설정, 서버 접근 방법을 확정하세요.
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

echo "[INFO] 최신 코드 pull..."
git pull origin main

echo "[INFO] 이미지 빌드..."
docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_BASE}" \
  -f "${COMPOSE_PROD}" \
  build --no-cache

echo "[INFO] 서비스 재기동..."
docker compose \
  --env-file "${ENV_FILE}" \
  -f "${COMPOSE_BASE}" \
  -f "${COMPOSE_PROD}" \
  up -d

echo "[INFO] 배포 완료."
echo "  로그 확인: docker compose -f ${COMPOSE_BASE} -f ${COMPOSE_PROD} logs -f"
