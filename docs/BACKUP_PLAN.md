# 백업 계획

---

## MySQL 백업

### Dev

- 주기: 필요 시 수동 실행
- 방법: `infra/scripts/backup.sh` 실행
- 저장 위치: 로컬 `backups/mysql/` 디렉토리

> 스크립트는 `docker compose --env-file infra/docker/.env -f compose.yaml -f compose.dev.yaml exec` 경로를 사용합니다.  
> Compose 프로젝트명은 `name: petnose`로 고정되어 볼륨/컨테이너 prefix가 일관됩니다.

### Prod

- 주기: 매일 새벽 자동 실행 (cron 또는 EC2 스케줄러)
- 방법: `mysqldump` + gzip 압축
- 저장 위치: EC2 외부 볼륨 또는 S3 버킷 (설정 후 확정)
- 보존 기간: 최근 7일분

```bash
# 기본 dump 명령 예시
mysqldump -h 127.0.0.1 -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  | gzip > backup_$(date +%Y%m%d_%H%M%S).sql.gz
```

---

## 업로드 파일 백업 (이미지)

`uploads/` 볼륨에 저장된 비문 이미지 및 프로필 이미지를 백업합니다.

### Dev

- 필요 시 수동 복사

### Prod

- `rsync` 또는 `tar` 기반 압축 후 외부 저장소로 전송
- MySQL dump와 동일 주기로 실행
- S3 sync를 고려할 수 있음 (aws cli 필요)

```bash
# 예시
tar -czf uploads_$(date +%Y%m%d).tar.gz /var/uploads
```

---

## Qdrant 백업

Qdrant는 MySQL로부터 재구성 가능하므로 우선순위는 낮습니다.  
단, 임베딩 재생성 비용이 큰 경우 Qdrant 스냅샷을 활용할 수 있습니다.

### 스냅샷 생성 (Qdrant API)

```bash
# 컬렉션 스냅샷 생성
POST http://localhost:6333/collections/dog_nose_embeddings/snapshots
```

- 스냅샷은 Qdrant 컨테이너 내부 `/qdrant/snapshots/`에 저장됩니다.
- 볼륨 마운트를 통해 호스트에 파일을 확보해 두세요.
- 복구 시 `POST /collections/{name}/snapshots/recover` API를 사용합니다.

---

## 복구 절차 개요

1. **MySQL 복구**
   ```bash
   bash infra/scripts/restore.sh mysql backups/mysql/backup_YYYYMMDD.sql.gz
   ```

2. **업로드 파일 복구**
   ```bash
   bash infra/scripts/restore.sh uploads backups/uploads_YYYYMMDD.tar.gz
   ```

3. **Qdrant 복구** (스냅샷 있을 경우)
   - Qdrant API로 스냅샷 복구

4. **Qdrant 재구성** (스냅샷 없을 경우)
   - MySQL `dogs` 테이블에서 `qdrant_point_id IS NULL` 또는 전체 레코드를 대상으로
   - Spring Boot 배치 엔드포인트 또는 스크립트로 임베딩 재생성

---

## 주의사항

- 백업 파일에 DB 패스워드 등 민감 정보가 포함될 수 있으므로 저장소에 커밋하지 않습니다.
- 복구 테스트를 주기적으로 수행하여 실제 복구 가능 여부를 확인합니다.
- prod 환경에서는 백업 스크립트 실행 결과를 로그로 남깁니다.
- `backup.sh`/`restore.sh`는 업로드 파일 처리 시 `spring-api` 컨테이너의 `${UPLOAD_BASE_PATH}` 마운트를 사용합니다.
- 따라서 복구/백업 실행 전 dev 스택(`spring-api`, `mysql`)이 실행 중이어야 합니다.
