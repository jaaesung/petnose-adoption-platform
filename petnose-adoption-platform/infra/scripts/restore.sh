#!/usr/bin/env bash
# 복구 초안 스크립트
# 사용: bash restore.sh <mysql|uploads> <백업파일경로>
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"

if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] .env 파일이 없습니다."
  exit 1
fi
source "${ENV_FILE}"

TARGET="${1:-}"
BACKUP_FILE="${2:-}"

if [ -z "${TARGET}" ] || [ -z "${BACKUP_FILE}" ]; then
  echo "사용법: bash restore.sh <mysql|uploads> <백업파일경로>"
  echo "  예) bash restore.sh mysql backups/mysql/backup_20260412.sql.gz"
  exit 1
fi

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "[ERROR] 백업 파일을 찾을 수 없습니다: ${BACKUP_FILE}"
  exit 1
fi

case "${TARGET}" in
  mysql)
    echo "[INFO] MySQL 복구 시작: ${BACKUP_FILE}"
    echo "[WARN] 기존 데이터가 덮어써질 수 있습니다. 계속하려면 Enter를 누르세요."
    read -r
    gunzip -c "${BACKUP_FILE}" | docker compose --env-file "${ENV_FILE}" \
      -f "${DOCKER_DIR}/compose.yaml" \
      exec -T mysql \
      mysql -u "${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}"
    echo "[OK]   MySQL 복구 완료."
    ;;

  uploads)
    echo "[INFO] 업로드 파일 복구 시작: ${BACKUP_FILE}"
    docker run --rm \
      -v petnose_uploads_data:/var/uploads \
      -v "$(realpath "${BACKUP_FILE}"):/backup.tar.gz:ro" \
      alpine \
      tar -xzf /backup.tar.gz -C /var/uploads
    echo "[OK]   업로드 파일 복구 완료."
    ;;

  *)
    echo "[ERROR] 알 수 없는 타겟: ${TARGET} (mysql 또는 uploads 중 선택)"
    exit 1
    ;;
esac
