# 파일 저장 및 URL 정책

> 문서 성격: 보조 참고 문서(Task Reference)
>
> file storage, `/files/{relative_path}`, `dog_images.file_path` 정책을 확인할 때 읽는다.
> active canonical 문서와 충돌하면 active canonical 문서가 우선한다.
> Qdrant/vector 경계는 `docs/reference/STORAGE_AND_VECTOR_BOUNDARY.md`도 함께 확인한다.

작성 기준일: 2026-04-12

---

## 개요

이 문서는 PetNose 플랫폼에서 업로드되는 이미지(비문, 프로필, 기타)의  
**저장 경로 규칙**, **DB 기록 형식**, **외부 URL 체계**, **파일 서빙 전략**을 확정합니다.

실제 업로드 API 구현은 이 문서의 정책을 따릅니다.

---

## 볼륨 및 마운트 경로

| 위치 | 경로 |
|------|------|
| Docker 볼륨 이름 | `uploads_data` |
| 컨테이너 내 마운트 경로 | `${UPLOAD_BASE_PATH:-/var/uploads}` |
| 기본값 | `/var/uploads` |
| `.env` 키 | `UPLOAD_BASE_PATH` |

`uploads_data` 볼륨은 `spring-api`, `python-embed`, `nginx` 세 컨테이너에 동일 경로로 마운트됩니다.

- `spring-api` — 업로드 수신 및 파일 쓰기
- `python-embed` — 비문 이미지 읽기 (임베딩 처리)
- `nginx` — 파일 직접 서빙 (읽기 전용)

---

## 디렉토리 구조 (볼륨 내부)

```
/var/uploads/
└── dogs/
    └── {dog_uuid}/          ← dogs.id (CHAR(36) UUID)
        ├── nose/            ← 비문 이미지
        │   └── {filename}
        ├── profile/         ← 강아지 프로필 사진
        │   └── {filename}
        └── extra/           ← 기타 참고 사진
            └── {filename}
```

### 파일명 규칙

```
{yyyyMMdd_HHmmss}_{sanitized_original_name}.{ext}
```

예시:
```
20260412_143022_front.jpg
20260412_143055_side.jpg
```

- `yyyyMMdd_HHmmss` — 업로드 시각 (서버 시각 기준)
- `sanitized_original_name` — 원본 파일명에서 공백/특수문자를 `_`로 치환, 최대 40자
- 확장자는 소문자로 정규화 (`.JPG` → `.jpg`)
- 동일 시각·이름 충돌 발생 시 `_{4자리 랜덤 hex}` 접미사 추가

---

## DB 기록 형식 (`dog_images.file_path`)

`file_path` 컬럼에는 **볼륨 마운트 경로를 제외한 상대 경로**만 저장합니다.

```
dogs/{dog_uuid}/{image_type}/{filename}
```

예시:
```
dogs/550e8400-e29b-41d4-a716-446655440000/nose/20260412_143022_front.jpg
dogs/550e8400-e29b-41d4-a716-446655440000/profile/20260412_143100_profile.jpg
```

> **이유**: 절대 경로를 저장하면 `UPLOAD_BASE_PATH` 변경 시 DB 전체 수정이 필요합니다.  
> 상대 경로만 저장하면 파일 시스템 경로는 `UPLOAD_BASE_PATH + "/" + file_path`로 조합합니다.

실제 파일 시스템 경로 (Spring Boot 내부):
```java
Path absolutePath = Paths.get(uploadBasePath).resolve(filePathFromDb);
// → /var/uploads/dogs/550e8400-.../nose/20260412_143022_front.jpg
```

---

## 외부 URL 체계

```
GET /files/{relative_path}
```

예시:
```
GET /files/dogs/550e8400-e29b-41d4-a716-446655440000/nose/20260412_143022_front.jpg
```

응답으로 돌아오는 전체 URL (Flutter 앱에서 사용):
```
http://{host}/files/dogs/550e8400-.../nose/20260412_143022_front.jpg
```

API 응답에서 URL을 조합할 때는 아래 방식을 권장합니다.
```
public_url = "http://" + host + "/files/" + dog_image.file_path
```

---

## 파일 서빙 전략

### 결정: Nginx 직접 서빙 (dev/prod 동일)

| 항목 | Spring Boot 서빙 | **Nginx 서빙 (채택)** |
|------|-----------------|----------------------|
| 성능 | JVM 경유 I/O | OS 수준 sendfile |
| 구현 복잡도 | Spring MVC + `Resource` 반환 | Nginx `alias` 한 줄 |
| 프로파일 분기 | dev/prod 코드 분기 필요 | 없음 |
| 보안 제어 | Spring Security 연계 가능 | Nginx `auth_request` 또는 토큰 검증 불가 |

**현재 단계에서는 인증 없이 URL을 알면 누구나 접근 가능한 구조로 시작합니다.**  
향후 비공개 파일 요건이 생기면 Nginx `auth_request` 또는 Signed URL(Spring 생성) 방식으로 전환합니다.

### Nginx `location /files/` 설정

`infra/nginx/conf.d/default.conf`에 아래 블록이 추가되어 있습니다:

```nginx
location /files/ {
    alias /var/uploads/;
    
    # 허용 확장자 제한 (HTML/JS 실행 차단)
    location ~* \.(jpg|jpeg|png|gif|webp)$ {
        expires 7d;
        add_header Cache-Control "public, immutable";
    }
    
    # 그 외 확장자는 404
    return 404;
}
```

Nginx 컨테이너에도 `uploads_data` 볼륨이 읽기 전용으로 마운트되어 있습니다:
```yaml
# compose.yaml — nginx 서비스
volumes:
  - uploads_data:${UPLOAD_BASE_PATH:-/var/uploads}:ro
```

---

## 환경변수 요약

| 키 | 설명 | 기본값 |
|----|------|--------|
| `UPLOAD_BASE_PATH` | 볼륨 내 업로드 루트 경로 | `/var/uploads` |
| `MAX_UPLOAD_SIZE_MB` | 단일 파일 최대 크기 (MB) | `20` |

> Spring Boot `application.yml`에서:
> ```yaml
> upload:
>   base-path: ${UPLOAD_BASE_PATH:/var/uploads}
> ```
> 이 값을 `@Value("${upload.base-path}")` 또는 `@ConfigurationProperties`로 주입받아 사용합니다.

---

## 업로드 크기 제한 일치 확인

파일 크기 제한은 세 곳에서 일치해야 합니다:

| 위치 | 설정 | 현재 값 |
|------|------|---------|
| Nginx | `client_max_body_size` | `20m` (hardcoded) |
| Spring Boot | `spring.servlet.multipart.max-file-size` | `${MAX_UPLOAD_SIZE_MB:20}MB` |
| Spring Boot | `spring.servlet.multipart.max-request-size` | `${MAX_UPLOAD_SIZE_MB:20}MB` |
| Python Embed | `MAX_IMAGE_BYTES` | `${MAX_UPLOAD_SIZE_MB:-20}000000` |

> **주의**: `.env`의 `MAX_UPLOAD_SIZE_MB`를 변경하면 `nginx/conf.d/default.conf`의  
> `client_max_body_size`도 수동으로 맞춰야 합니다. 이후 `${CLIENT_MAX_BODY_SIZE}` 환경변수로
> 통일할 수 있지만, 현재는 수동 관리입니다.

---

## 현재 MVP/API Contract 제외 항목

| 항목 | 설명 |
|------|------|
| 업로드 API | `POST /api/dogs/{id}/images` — current MVP/API contract 밖이며 별도 승인 필요 |
| 파일 검증 | MIME 타입 실제 확인, 이미지 크기(픽셀) 검증 |
| 중복 제거 | 동일 이미지 SHA-256 해시 기반 중복 저장 방지 |
| Signed URL | 비공개 파일 요건 시 Spring에서 서명 토큰 발급 |
| CDN | 트래픽 증가 시 Nginx → CDN(CloudFront 등)으로 교체 |
