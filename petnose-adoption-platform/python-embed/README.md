# python-embed — 비문 임베딩 서비스

## 역할

강아지 비문 이미지를 받아 임베딩 벡터를 반환하는 전용 서비스입니다.  
Spring Boot에서만 호출하며, Flutter에서 직접 접근하지 않습니다.

API 계약은 [docs/API_CONTRACTS/spring-python.md](../docs/API_CONTRACTS/spring-python.md)를 참고하세요.

---

## 기술 스택

- Python 3.11
- FastAPI + Uvicorn
- 의존성: `fastapi`, `uvicorn[standard]`, `python-multipart`

---

## 현재 구현 상태 — [MOCK MODE]

`EMBED_MODEL=mock-v1` (기본값) 환경에서 동작합니다.  
실제 딥러닝 모델 없이 다음과 같이 동작합니다:

- 이미지 바이트의 SHA-256 해시를 시드로 사용
- 재현 가능한(deterministic) 단위 벡터(128차원) 생성
- **동일 이미지 → 항상 동일 벡터** → 유사도 테스트 가능
- 실제 모델 적용 전까지 안정적인 파이프라인 검증 가능

실제 모델 적용 시 `app/main.py`의 `_load_model()` 함수를 구현하고  
`EMBED_MODEL` 환경변수를 변경하면 됩니다.

---

## 엔드포인트

| 경로 | 메서드 | 설명 |
|---|---|---|
| `/health` | GET | 서비스 상태 확인 (Docker healthcheck 사용) |
| `/embed` | POST | 비문 이미지 → 임베딩 벡터 반환 |

### `/embed` 요청/응답 예시

```bash
curl -X POST http://localhost:8000/embed \
  -F "image=@/path/to/nose.jpg"
```

```json
{
  "status": "ok",
  "vector": [0.12, -0.34, ...],
  "dimension": 128,
  "model": "mock-v1"
}
```

---

## 로컬 실행

Docker Compose 권장:

```bash
bash infra/scripts/dev-up.sh
```

단독 실행:

```bash
cd python-embed
python -m venv .venv
# Linux/macOS:
source .venv/bin/activate
# Windows:
.venv\Scripts\activate

pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

---

## 주요 환경변수

| 변수 | 설명 | 기본값 |
|---|---|---|
| `EMBED_VECTOR_DIM` | 출력 벡터 차원 (Qdrant와 일치 필요) | `128` |
| `EMBED_MODEL` | 모델 식별자 (`mock-v1` = mock mode) | `mock-v1` |
| `MAX_IMAGE_BYTES` | 최대 이미지 크기(바이트) | `20000000` |

---

> 추후 실제 비문 인식 모델 적용 후 이 문서를 갱신할 예정입니다.
