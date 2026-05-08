from __future__ import annotations

from io import BytesIO


class ImageDecodeError(ValueError):
    pass


def decode_rgb_image(image_bytes: bytes):
    """
    Decode raw bytes to RGB PIL image.
    """
    try:
        from PIL import Image, UnidentifiedImageError  # type: ignore

        image = Image.open(BytesIO(image_bytes))
        return image.convert("RGB")
    except (UnidentifiedImageError, OSError, ModuleNotFoundError) as exc:
        raise ImageDecodeError("이미지를 디코딩할 수 없습니다.") from exc
