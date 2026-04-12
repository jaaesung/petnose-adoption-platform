"""
PetNose — Python Embedding Service

비문(코 지문) 이미지를 받아 임베딩 벡터를 반환합니다.

[MOCK MODE]
모델 파일이 없는 dev 환경에서는 mock 모드로 동작합니다.
mock 모드: 이미지 바이트의 SHA-256 해시를 시드로 사용해
재현 가능한(deterministic) 더미 벡터를 생성합니다.
같은 이미지 → 항상 같은 벡터 → 유사도 테스트 가능.

실제 모델 적용 시 EMBED_MODEL 환경변수를 변경하고
_load_model() 함수를 구현하면 됩니다.
"""

import hashlib
import math
import os
import struct
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import JSONResponse

# ── 설정 ────────────────────────────────────────────────────────────────────
VECTOR_DIM: int = int(os.getenv("EMBED_VECTOR_DIM", "128"))
MODEL_NAME: str = os.getenv("EMBED_MODEL", "mock-v1")
MAX_IMAGE_SIZE: int = int(os.getenv("MAX_IMAGE_BYTES", str(20 * 1024 * 1024)))  # 20MB

_model_loaded: bool = False


# ── 모델 로딩 ────────────────────────────────────────────────────────────────
def _load_model() -> bool:
    """
    실제 모델 파일 로딩 로직을 여기에 구현합니다.
    현재는 mock 모드이므로 항상 True를 반환합니다.
    """
    if MODEL_NAME == "mock-v1":
        return True
    # TODO: 실제 모델 로딩 구현
    # e.g., model = torch.load(...)
    return False


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _model_loaded
    _model_loaded = _load_model()
    mode = "MOCK" if MODEL_NAME == "mock-v1" else "REAL"
    print(f"[EmbedService] 시작 — 모드: {mode}, 모델: {MODEL_NAME}, 차원: {VECTOR_DIM}")
    yield
    print("[EmbedService] 종료")


app = FastAPI(title="PetNose Embed Service", version="0.1.0", lifespan=lifespan)


# ── 유틸 ─────────────────────────────────────────────────────────────────────
def _mock_vector(content: bytes) -> list[float]:
    """
    이미지 바이트 해시 기반 재현 가능한 단위 벡터 생성.
    동일 이미지 → 동일 벡터 (테스트 안정성 보장).
    """
    digest = hashlib.sha256(content).digest()
    seed = struct.unpack(">Q", digest[:8])[0]

    vector: list[float] = []
    for _ in range(VECTOR_DIM):
        seed = (seed * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
        val = (seed / 0x7FFFFFFFFFFFFFFF) - 1.0
        vector.append(val)

    magnitude = math.sqrt(sum(x * x for x in vector))
    if magnitude == 0:
        magnitude = 1.0
    return [x / magnitude for x in vector]


# ── 엔드포인트 ────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    """서비스 상태 확인 — Docker healthcheck 및 Spring Boot에서 호출합니다."""
    return {
        "status": "ok",
        "model_loaded": _model_loaded,
        "model": MODEL_NAME,
        "vector_dim": VECTOR_DIM,
    }


@app.post("/embed")
async def embed(image: UploadFile = File(...)):
    """
    비문 이미지를 받아 임베딩 벡터를 반환합니다.

    - Content-Type: multipart/form-data
    - 필드명: image
    - 허용 형식: JPEG, PNG
    """
    content = await image.read()

    if not content:
        raise HTTPException(status_code=400, detail={
            "error": "INVALID_IMAGE",
            "message": "이미지 내용이 비어 있습니다.",
        })

    if len(content) > MAX_IMAGE_SIZE:
        raise HTTPException(status_code=400, detail={
            "error": "IMAGE_TOO_LARGE",
            "message": f"이미지 크기가 제한({MAX_IMAGE_SIZE // 1024 // 1024}MB)을 초과합니다.",
        })

    content_type = image.content_type or ""
    if content_type not in ("image/jpeg", "image/png", "image/jpg"):
        raise HTTPException(status_code=400, detail={
            "error": "UNSUPPORTED_FORMAT",
            "message": f"지원하지 않는 이미지 형식입니다: {content_type}",
        })

    try:
        if MODEL_NAME == "mock-v1":
            vector = _mock_vector(content)
        else:
            # TODO: 실제 모델 추론 구현
            raise NotImplementedError("실제 모델이 아직 구현되지 않았습니다.")

        return {
            "status": "ok",
            "vector": vector,
            "dimension": VECTOR_DIM,
            "model": MODEL_NAME,
        }

    except NotImplementedError as e:
        raise HTTPException(status_code=501, detail={
            "error": "MODEL_NOT_IMPLEMENTED",
            "message": str(e),
        })
    except Exception as e:
        raise HTTPException(status_code=500, detail={
            "error": "EMBED_FAILED",
            "message": f"임베딩 생성 중 오류가 발생했습니다: {str(e)}",
        })
