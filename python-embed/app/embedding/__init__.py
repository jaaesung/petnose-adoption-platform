from __future__ import annotations

import os

from .base import BaseEmbedder, EmbedderError
from .mock_embedder import MockEmbedder


def create_embedder_from_env() -> BaseEmbedder:
    embed_model = os.getenv("EMBED_MODEL", "mock-v1").strip()
    vector_dim = int(os.getenv("EMBED_VECTOR_DIM", "128"))

    if embed_model == "mock-v1":
        return MockEmbedder(vector_dim=vector_dim)

    if embed_model == "dog-nose-identification2":
        from .dog_nose_identification2_embedder import DogNoseIdentification2Embedder

        model_dir = os.getenv("DOG_NOSE_MODEL_DIR", "/models/dog_nose_identification2")
        model_path = os.getenv("DOG_NOSE_MODEL_PATH", "").strip() or None
        embed_device = os.getenv("EMBED_DEVICE", "cpu")
        return DogNoseIdentification2Embedder(
            model_dir=model_dir,
            model_path=model_path,
            embed_device=embed_device,
        )

    raise EmbedderError(
        f"지원하지 않는 EMBED_MODEL입니다: {embed_model}. "
        "지원값: mock-v1, dog-nose-identification2"
    )
