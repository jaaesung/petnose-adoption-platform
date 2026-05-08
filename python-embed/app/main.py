"""
PetNose - Python Embedding Service

Contract notes:
  - /embed response fields must stay: status, vector, dimension, model
  - mock-v1 must remain stable for existing smoke tests
"""

from __future__ import annotations

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, File, HTTPException, UploadFile

from .embedding import create_embedder_from_env
from .embedding.base import BaseEmbedder, EmbedderError, EmbedderNotReadyError

MAX_IMAGE_SIZE: int = int(os.getenv("MAX_IMAGE_BYTES", str(20 * 1024 * 1024)))
ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/jpg"}

_embedder: BaseEmbedder | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global _embedder
    _embedder = create_embedder_from_env()
    loaded = _embedder.load()
    mode = "MOCK" if _embedder.requested_model_name == "mock-v1" else "REAL"
    print(
        "[EmbedService] start - "
        f"mode={mode}, requested_model={_embedder.requested_model_name}, "
        f"resolved_model={_embedder.model_name}, loaded={loaded}, dim={_embedder.vector_dim}"
    )
    if _embedder.load_error:
        print(f"[EmbedService] load_error={_embedder.load_error}")
    yield
    print("[EmbedService] 종료")


app = FastAPI(title="PetNose Embed Service", version="0.2.0", lifespan=lifespan)


def _require_embedder() -> BaseEmbedder:
    if _embedder is None:
        raise HTTPException(
            status_code=500,
            detail={
                "error": "SERVICE_NOT_INITIALIZED",
                "message": "Embedding service is not initialized.",
            },
        )
    return _embedder


@app.get("/health")
def health():
    embedder = _require_embedder()
    data = embedder.health_dict()
    # Backward-compatible keys (Spring and existing diagnostics)
    return {
        "status": "ok",
        "model_loaded": data["model_loaded"],
        "model": data["model"],
        "vector_dim": data["vector_dim"],
        "backend": data.get("backend"),
        "device": data.get("device"),
        "model_path_exists": data.get("model_path_exists"),
        "load_error": data.get("load_error"),
        "image_size": data.get("image_size"),
    }


@app.post("/embed")
async def embed(image: UploadFile = File(...)):
    embedder = _require_embedder()

    content = await image.read()
    if not content:
        raise HTTPException(
            status_code=400,
            detail={
                "error": "INVALID_IMAGE",
                "message": "이미지 내용이 비어 있습니다.",
            },
        )

    if len(content) > MAX_IMAGE_SIZE:
        raise HTTPException(
            status_code=400,
            detail={
                "error": "IMAGE_TOO_LARGE",
                "message": f"이미지 크기가 제한({MAX_IMAGE_SIZE // 1024 // 1024}MB)을 초과합니다.",
            },
        )

    content_type = (image.content_type or "").lower()
    if content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=400,
            detail={
                "error": "UNSUPPORTED_FORMAT",
                "message": f"지원하지 않는 이미지 형식입니다: {content_type}",
            },
        )

    if not embedder.model_loaded:
        raise HTTPException(
            status_code=503,
            detail={
                "error": "MODEL_NOT_READY",
                "message": embedder.load_error or "모델이 아직 로드되지 않았습니다.",
            },
        )

    try:
        result = embedder.embed(content, content_type)
        if not result.vector:
            raise EmbedderError("출력 벡터가 비어 있습니다.")

        return {
            "status": "ok",
            "vector": result.vector,
            "dimension": result.dimension,
            "model": result.model,
        }

    except EmbedderNotReadyError as exc:
        raise HTTPException(
            status_code=503,
            detail={
                "error": "MODEL_NOT_READY",
                "message": str(exc),
            },
        )
    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail={
                "error": "INVALID_IMAGE",
                "message": str(exc),
            },
        )
    except EmbedderError as exc:
        raise HTTPException(
            status_code=500,
            detail={
                "error": "EMBED_FAILED",
                "message": str(exc),
            },
        )
    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail={
                "error": "EMBED_FAILED",
                "message": f"임베딩 생성 중 오류가 발생했습니다: {str(exc)}",
            },
        )
