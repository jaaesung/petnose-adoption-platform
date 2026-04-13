#!/usr/bin/env bash
# 백업 스크립트 — MySQL dump + 업로드 파일 백업
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
COMPOSE_BASE="${DOCKER_DIR}/compose.yaml"
COMPOSE_DEV="${DOCKER_DIR}/compose.dev.yaml"
BACKUP_DIR="${REPO_ROOT}/backups"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"

# .env 확인
if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] .env 파일이 없습니다."
  exit 1
fi

compose_dev() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_BASE}" \
    -f "${COMPOSE_DEV}" \
    "$@"
}

require_running_service() {
  local service="$1"
  if ! compose_dev ps --services --status running | grep -qx "${service}"; then
    echo "[ERROR] '${service}' 서비스가 실행 중이 아닙니다. dev 환경을 먼저 기동하세요."
    exit 1
  fi
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

MYSQL_USER_VALUE="$(read_env_var MYSQL_USER)"
MYSQL_PASSWORD_VALUE="$(read_env_var MYSQL_PASSWORD)"
MYSQL_DATABASE_VALUE="$(read_env_var MYSQL_DATABASE)"
UPLOAD_BASE_PATH_VALUE="$(read_env_var UPLOAD_BASE_PATH)"
UPLOAD_BASE_PATH_VALUE="${UPLOAD_BASE_PATH_VALUE:-/var/uploads}"

if [ -z "${MYSQL_USER_VALUE}" ] || [ -z "${MYSQL_PASSWORD_VALUE}" ] || [ -z "${MYSQL_DATABASE_VALUE}" ]; then
  echo "[ERROR] MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE 값이 .env에 필요합니다."
  exit 1
fi

require_running_service "mysql"
require_running_service "spring-api"

mkdir -p "${BACKUP_DIR}/mysql" "${BACKUP_DIR}/uploads"
MYSQL_BACKUP_FILE="${BACKUP_DIR}/mysql/backup_${TIMESTAMP}.sql.gz"
UPLOADS_BACKUP_FILE="${BACKUP_DIR}/uploads/uploads_${TIMESTAMP}.tar.gz"

# 1. MySQL dump
echo "[INFO] MySQL 백업 시작..."
compose_dev exec -T mysql \
  mysqldump -u "${MYSQL_USER_VALUE}" -p"${MYSQL_PASSWORD_VALUE}" "${MYSQL_DATABASE_VALUE}" \
  | gzip > "${MYSQL_BACKUP_FILE}"

if [ ! -s "${MYSQL_BACKUP_FILE}" ]; then
  echo "[ERROR] MySQL 백업 파일이 비어있거나 생성되지 않았습니다: ${MYSQL_BACKUP_FILE}"
  exit 1
fi
echo "[OK]   MySQL 백업 완료: ${MYSQL_BACKUP_FILE}"

# 2. 업로드 파일 백업 (spring-api 컨테이너의 uploads 마운트를 통해 수행)
echo "[INFO] 업로드 파일 백업 시작... (path=${UPLOAD_BASE_PATH_VALUE})"
compose_dev exec -T spring-api sh -lc "mkdir -p '${UPLOAD_BASE_PATH_VALUE}' && tar -czf - -C '${UPLOAD_BASE_PATH_VALUE}' ." \
  > "${UPLOADS_BACKUP_FILE}"

if [ ! -s "${UPLOADS_BACKUP_FILE}" ]; then
  echo "[ERROR] 업로드 백업 파일이 비어있거나 생성되지 않았습니다: ${UPLOADS_BACKUP_FILE}"
  exit 1
fi
echo "[OK]   업로드 백업 완료: ${UPLOADS_BACKUP_FILE}"

# 3. Qdrant 스냅샷 (선택 — Qdrant 컨테이너가 올라와 있을 때만)
# Qdrant는 MySQL에서 재구성 가능하므로 선택 항목입니다.
# echo "[INFO] Qdrant 스냅샷 생성..."
# curl -sf -X POST http://localhost:6333/collections/dog_nose_embeddings/snapshots
# echo "[OK]   Qdrant 스냅샷 생성 완료 (컨테이너 내부 /qdrant/snapshots/ 확인)"

echo "[INFO] 백업 완료"
echo "  - mysql:   ${MYSQL_BACKUP_FILE}"
echo "  - uploads: ${UPLOADS_BACKUP_FILE}"
