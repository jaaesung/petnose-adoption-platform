#!/usr/bin/env bash
# 복구 스크립트
# 사용:
#   bash restore.sh mysql <백업파일경로.sql.gz>
#   bash restore.sh uploads <백업파일경로.tar.gz> [--wipe-first]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
COMPOSE_BASE="${DOCKER_DIR}/compose.yaml"
COMPOSE_DEV="${DOCKER_DIR}/compose.dev.yaml"

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

TARGET="${1:-}"
BACKUP_FILE="${2:-}"
UPLOADS_OPTION="${3:-}"

usage() {
  echo "사용법:"
  echo "  bash infra/scripts/restore.sh mysql backups/mysql/backup_<YYYYMMDD_HHMMSS>.sql.gz"
  echo "  bash infra/scripts/restore.sh uploads backups/uploads/uploads_<YYYYMMDD_HHMMSS>.tar.gz [--wipe-first]"
}

if [ -z "${TARGET}" ] || [ -z "${BACKUP_FILE}" ]; then
  usage
  exit 1
fi

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "[ERROR] 백업 파일을 찾을 수 없습니다: ${BACKUP_FILE}"
  exit 1
fi

case "${TARGET}" in
  mysql)
    if [ -n "${UPLOADS_OPTION}" ]; then
      echo "[ERROR] mysql 복구는 세 번째 인자를 받지 않습니다."
      usage
      exit 1
    fi

    if [ -z "${MYSQL_USER_VALUE}" ] || [ -z "${MYSQL_PASSWORD_VALUE}" ] || [ -z "${MYSQL_DATABASE_VALUE}" ]; then
      echo "[ERROR] MYSQL_USER / MYSQL_PASSWORD / MYSQL_DATABASE 값이 .env에 필요합니다."
      exit 1
    fi

    if [[ "${BACKUP_FILE}" != *.sql.gz ]]; then
      echo "[WARN] mysql 백업 파일은 일반적으로 .sql.gz 확장자를 사용합니다: ${BACKUP_FILE}"
    fi

    require_running_service "mysql"

    echo "[INFO] MySQL 복구 시작: ${BACKUP_FILE}"
    echo "[WARN] 기존 데이터가 덮어써질 수 있습니다. 계속하려면 Enter를 누르세요."
    read -r
    gunzip -c "${BACKUP_FILE}" | compose_dev exec -T mysql \
      mysql -u "${MYSQL_USER_VALUE}" -p"${MYSQL_PASSWORD_VALUE}" "${MYSQL_DATABASE_VALUE}"
    echo "[OK]   MySQL 복구 완료."
    ;;

  uploads)
    WIPE_FIRST="false"
    case "${UPLOADS_OPTION}" in
      "")
        ;;
      --wipe-first)
        WIPE_FIRST="true"
        ;;
      *)
        echo "[ERROR] 알 수 없는 uploads 옵션: ${UPLOADS_OPTION}"
        usage
        exit 1
        ;;
    esac

    if [[ "${BACKUP_FILE}" != *.tar.gz ]]; then
      echo "[WARN] uploads 백업 파일은 일반적으로 .tar.gz 확장자를 사용합니다: ${BACKUP_FILE}"
    fi

    require_running_service "spring-api"

    echo "[INFO] 업로드 파일 복구 시작: ${BACKUP_FILE}"
    if [ "${WIPE_FIRST}" = "true" ]; then
      echo "[WARN] --wipe-first 사용: ${UPLOAD_BASE_PATH_VALUE} 경로 기존 파일을 먼저 삭제한 뒤 복구합니다."
    else
      echo "[WARN] 기본 모드: 기존 파일은 유지되고, 동일 경로 파일만 덮어써집니다."
    fi
    echo "[WARN] 계속하려면 Enter를 누르세요."
    read -r

    if [ "${WIPE_FIRST}" = "true" ]; then
      compose_dev exec -T spring-api \
        sh -lc "mkdir -p '${UPLOAD_BASE_PATH_VALUE}' && find '${UPLOAD_BASE_PATH_VALUE}' -mindepth 1 -delete"
    fi

    cat "${BACKUP_FILE}" | compose_dev exec -T spring-api \
      sh -lc "mkdir -p '${UPLOAD_BASE_PATH_VALUE}' && tar -xzf - -C '${UPLOAD_BASE_PATH_VALUE}'"
    echo "[OK]   업로드 파일 복구 완료. (wipe-first=${WIPE_FIRST})"
    ;;

  *)
    echo "[ERROR] 알 수 없는 타겟: ${TARGET} (mysql 또는 uploads 중 선택)"
    exit 1
    ;;
esac
