# 백업 계획

> 문서 성격: 보조 참고 문서(Task Reference)
>
> MySQL, uploads, Qdrant backup/restore 절차를 확인할 때 읽는다.
> active canonical 문서와 충돌하면 active canonical 문서가 우선한다.

---

## MySQL 백업

### Dev

- 주기: 필요 시 수동 실행
- 방법: `bash infra/scripts/backup.sh`
- 산출물 경로: `backups/mysql/backup_<YYYYMMDD_HHMMSS>.sql.gz`

> 스크립트는 아래 compose 타깃을 고정 사용합니다.  
> `docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml ...`

### Prod

- 주기: 매일 새벽 자동 실행 (cron 또는 EC2 스케줄러)
- 방법: `mysqldump` + gzip 압축
- 저장 위치: EC2 외부 볼륨 또는 S3 버킷 (설정 후 확정)
- 보존 기간: 최근 7일분

```bash
mysqldump -h 127.0.0.1 -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" \
  | gzip > "backup_$(date +%Y%m%d_%H%M%S).sql.gz"
```

---

## 업로드 파일 백업 (이미지)

`uploads_data` 볼륨(컨테이너 내부 `${UPLOAD_BASE_PATH:-/var/uploads}`)의 파일을 백업합니다.

### Dev

- 주기: 필요 시 수동 실행
- 방법: `bash infra/scripts/backup.sh` (MySQL + uploads 동시 생성)
- 산출물 경로: `backups/uploads/uploads_<YYYYMMDD_HHMMSS>.tar.gz`

### Prod

- `rsync` 또는 `tar` 기반 압축 후 외부 저장소로 전송
- MySQL dump와 동일 주기로 실행
- S3 sync를 고려할 수 있음 (aws cli 필요)

```bash
tar -czf "uploads_$(date +%Y%m%d_%H%M%S).tar.gz" /var/uploads
```

---

## 복구 절차 개요

1. MySQL 복구
```bash
bash infra/scripts/restore.sh mysql backups/mysql/backup_<YYYYMMDD_HHMMSS>.sql.gz
```

2. 업로드 파일 복구
```bash
# 기본: 기존 파일 유지 + 동일 경로 파일만 덮어쓰기
bash infra/scripts/restore.sh uploads backups/uploads/uploads_<YYYYMMDD_HHMMSS>.tar.gz

# 드릴 권장: 기존 파일 먼저 비우고 복구(스냅샷에 더 가깝게 재현)
bash infra/scripts/restore.sh uploads backups/uploads/uploads_<YYYYMMDD_HHMMSS>.tar.gz --wipe-first
```

---

## 로컬/dev Drill (1회 실검증)

아래 절차는 "백업 파일 생성 + 실제 복구 가능"을 1회 검증하는 최소 시나리오입니다.

### 0) 사전 조건

```bash
bash infra/scripts/dev-up.sh
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml ps
```

`mysql`, `spring-api`가 `running` 상태여야 합니다.

### 1) 샘플 데이터 생성 (DB + uploads)

```bash
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml exec -T mysql \
  sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "
    CREATE TABLE IF NOT EXISTS backup_restore_drill (
      id INT PRIMARY KEY AUTO_INCREMENT,
      token VARCHAR(128) NOT NULL,
      note VARCHAR(255) NOT NULL
    );
    DELETE FROM backup_restore_drill WHERE token='\''drill_local_validation'\'';
    INSERT INTO backup_restore_drill (token, note) VALUES ('\''drill_local_validation'\'', '\''before_backup'\'');
  "'

docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml exec -T spring-api \
  sh -lc 'mkdir -p "$UPLOAD_BASE_PATH/drill" && printf "%s\n" "before_backup" > "$UPLOAD_BASE_PATH/drill/drill_local_validation.txt"'
```

### 2) 백업 실행

```bash
bash infra/scripts/backup.sh
```

### 3) 산출물 경로 확인

```bash
ls -1t backups/mysql/backup_*.sql.gz | head -n 1
ls -1t backups/uploads/uploads_*.tar.gz | head -n 1
```

정상 패턴:
- `backups/mysql/backup_<YYYYMMDD_HHMMSS>.sql.gz`
- `backups/uploads/uploads_<YYYYMMDD_HHMMSS>.tar.gz`

### 4) 백업 후 데이터 변조 (복구 검증용)

```bash
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml exec -T mysql \
  sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "
    UPDATE backup_restore_drill
    SET note='\''after_backup_mutation'\''
    WHERE token='\''drill_local_validation'\'';
  "'

docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml exec -T spring-api \
  sh -lc 'printf "%s\n" "after_backup_mutation" > "$UPLOAD_BASE_PATH/drill/drill_local_validation.txt"'
```

### 5) 복구 실행

```bash
MYSQL_BACKUP="$(ls -1t backups/mysql/backup_*.sql.gz | head -n 1)"
UPLOADS_BACKUP="$(ls -1t backups/uploads/uploads_*.tar.gz | head -n 1)"

bash infra/scripts/restore.sh mysql "$MYSQL_BACKUP"
bash infra/scripts/restore.sh uploads "$UPLOADS_BACKUP" --wipe-first
```

### 6) 복구 상태 검증

```bash
docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml exec -T mysql \
  sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -Nse "
    SELECT note FROM backup_restore_drill WHERE token='\''drill_local_validation'\'';
  "'

docker compose --env-file infra/docker/.env -f infra/docker/compose.yaml -f infra/docker/compose.dev.yaml exec -T spring-api \
  sh -lc 'cat "$UPLOAD_BASE_PATH/drill/drill_local_validation.txt"'
```

둘 다 `before_backup`이면 드릴 통과입니다.

드릴 PASS 기준(최소):
1. MySQL 검증 쿼리 결과가 `before_backup`
2. uploads 검증 파일 내용이 `before_backup`
3. `http://localhost/actuator/health` 응답 성공
4. 결과가 `docs/ops-evidence/backup-restore-drill-log.md`에 기록됨

### 7) 드릴 결과 기록 (proof)

- 드릴 결과는 `docs/ops-evidence/backup-restore-drill-log.md`에 누적 기록합니다.
- 최소 기록 항목:
  - 실행 시각 / 실행자 / 대상 환경(dev/prod)
  - 사용한 백업 파일 경로(MySQL, uploads)
  - 복구 전 변조값과 복구 후 검증값(DB query 결과, 파일 내용/해시)
  - 복구 후 health check 결과(`http://localhost/actuator/health`)
  - 최종 판정(PASS/FAIL) 및 실패 시 원인

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

## 주의사항

- 백업 파일에 민감 정보가 포함될 수 있으므로 저장소에 커밋하지 않습니다.
- `backup.sh`/`restore.sh`는 `spring-api`, `mysql` 서비스가 실행 중이어야 동작합니다.
- uploads 복구 기본 모드는 "덮어쓰기"이며, 스냅샷과 동일 상태 재현이 필요하면 `--wipe-first`를 사용하세요.
- Qdrant 스냅샷 복구는 본 문서 범위 밖(선택)이며, 기본 drill은 MySQL + uploads만 검증합니다.
