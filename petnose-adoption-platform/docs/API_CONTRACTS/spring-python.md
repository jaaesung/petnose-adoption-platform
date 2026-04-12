# API 계약 — Spring Boot ↔ Python Embed

> Spring Boot(`spring-api`)가 Python 임베딩 서비스(`python-embed`)를 호출하는 계약 초안입니다.  
> Flutter는 Python 서비스에 직접 접근하지 않습니다.

Base URL (내부): `http://python-embed:8000`  
환경변수: `PYTHON_EMBED_URL=http://python-embed:8000`

---

## 1. 임베딩 생성

비문 이미지를 받아 임베딩 벡터를 반환합니다.

```
POST /embed
Content-Type: multipart/form-data

Form fields:
  image: file (JPEG/PNG)

Response 200:
{
  "vector": [0.12, -0.34, 0.56, ...],  // float 배열, 고정 차원
  "dim": 512,
  "model": "petnose-v1"  // 사용된 모델 식별자
}

Response 400:
{
  "error": "INVALID_IMAGE",
  "message": "이미지를 처리할 수 없습니다."
}

Response 500:
{
  "error": "EMBED_FAILED",
  "message": "임베딩 생성 중 오류가 발생했습니다."
}
```

---

## 2. 헬스체크

Spring Boot가 `python-embed` 의존성 확인 시 사용합니다.

```
GET /health

Response 200:
{
  "status": "ok",
  "model_loaded": true
}
```

---

## 호출 규약

### Spring Boot 측

- `RestTemplate` 또는 `WebClient`로 호출합니다.
- `PYTHON_EMBED_URL` 환경변수에서 base URL을 읽습니다.
- 요청 시 이미지 바이트를 multipart로 전송합니다.

### Timeout

| 항목 | 권장값 |
|---|---|
| Connection timeout | 3s |
| Read timeout | 10s (모델 로딩 고려) |

### Retry

- Python 서비스가 일시적으로 응답 불가 시 최대 2회 재시도합니다.
- 재시도 후에도 실패하면 Spring Boot는 `EMBED_FAILED` 에러를 클라이언트에 반환합니다.
- 무한 재시도는 금지합니다. 타임아웃 후 빠른 실패(fail-fast)를 권장합니다.

### 에러 처리

- 400 응답: 이미지 자체 문제 → Spring Boot가 클라이언트에 422 반환
- 500 응답 / timeout: Python 서비스 오류 → Spring Boot가 503 또는 500 반환
- 연결 불가: Python 컨테이너 다운 → Spring Boot가 503 반환

---

## 버전 관리

- 모델 버전이 바뀌면 벡터 차원이 달라질 수 있습니다.
- 모델 변경 시 Qdrant 컬렉션을 재생성하고 전체 재임베딩이 필요합니다.
- `model` 필드를 응답에 포함하여 Spring Boot가 모델 버전 불일치를 감지할 수 있게 합니다.
