#!/usr/bin/env bash
# 백업 초안 스크립트 — MySQL dump + 업로드 파일 백업
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/../docker"
ENV_FILE="${DOCKER_DIR}/.env"
BACKUP_DIR="${SCRIPT_DIR}/../../backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# .env 로드
if [ ! -f "${ENV_FILE}" ]; then
  echo "[ERROR] .env 파일이 없습니다."
  exit 1
fi
source "${ENV_FILE}"

mkdir -p "${BACKUP_DIR}/mysql" "${BACKUP_DIR}/uploads"

# 1. MySQL dump
echo "[INFO] MySQL 백업 시작..."
docker compose --env-file "${ENV_FILE}" -f "${DOCKER_DIR}/compose.yaml" \
  exec -T mysql \
  mysqldump -u "${MYSQL_USER}" -p"${MYSQL_PASSWORD}" "${MYSQL_DATABASE}" \
  | gzip > "${BACKUP_DIR}/mysql/backup_${TIMESTAMP}.sql.gz"
echo "[OK]   MySQL 백업 완료: backup_${TIMESTAMP}.sql.gz"

# 2. 업로드 파일 백업 (볼륨 -> 타르볼)
echo "[INFO] 업로드 파일 백업 시작..."
docker run --rm \
  -v petnose_uploads_data:/var/uploads:ro \
  -v "${BACKUP_DIR}/uploads:/backup" \
  alpine \
  tar -czf "/backup/uploads_${TIMESTAMP}.tar.gz" -C /var/uploads .
echo "[OK]   업로드 백업 완료: uploads_${TIMESTAMP}.tar.gz"

# 3. Qdrant 스냅샷 (선택 — Qdrant 컨테이너가 올라와 있을 때만)
# Qdrant는 MySQL에서 재구성 가능하므로 선택 항목입니다.
# echo "[INFO] Qdrant 스냅샷 생성..."
# curl -sf -X POST http://localhost:6333/collections/dog_nose_embeddings/snapshots
# echo "[OK]   Qdrant 스냅샷 생성 완료 (컨테이너 내부 /qdrant/snapshots/ 확인)"

echo "[INFO] 백업 완료: ${BACKUP_DIR}"
