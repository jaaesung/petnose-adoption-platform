from __future__ import annotations

import hashlib
import math
import struct

from .base import BaseEmbedder, EmbedResult


class MockEmbedder(BaseEmbedder):
    """
    deterministic mock embedder.

    IMPORTANT:
    Keep this behavior stable for smoke/regression tests.
    """

    def __init__(self, vector_dim: int) -> None:
        super().__init__(requested_model_name="mock-v1")
        self.model_name = "mock-v1"
        self.vector_dim = vector_dim
        self.backend = "mock"
        self.device = "cpu"

    def load(self) -> bool:
        self.model_loaded = True
        self.load_error = None
        return True

    def embed(self, image_bytes: bytes, content_type: str | None = None) -> EmbedResult:
        digest = hashlib.sha256(image_bytes).digest()
        seed = struct.unpack(">Q", digest[:8])[0]

        vector: list[float] = []
        for _ in range(self.vector_dim):
            seed = (seed * 6364136223846793005 + 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
            val = (seed / 0x7FFFFFFFFFFFFFFF) - 1.0
            vector.append(val)

        magnitude = math.sqrt(sum(x * x for x in vector))
        if magnitude == 0:
            magnitude = 1.0
        normalized = [x / magnitude for x in vector]

        return EmbedResult(vector=normalized, dimension=len(normalized), model=self.model_name)
