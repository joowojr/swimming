from __future__ import annotations

import base64
import shutil
import uuid
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, UnidentifiedImageError


RUNWAY_DATA_URI_LIMIT_BYTES = 5_000_000
RUNWAY_MIN_ASPECT_RATIO = 0.5
RUNWAY_MAX_ASPECT_RATIO = 2.0
RECOMMENDED_MIN_EDGE = 640
RECOMMENDED_MAX_EDGE = 4096
SUPPORTED_FORMATS = {
    "JPEG": ("image/jpeg", ".jpg"),
    "PNG": ("image/png", ".png"),
    "WEBP": ("image/webp", ".webp"),
}


@dataclass(frozen=True)
class ImageValidationResult:
    valid: bool
    errors: tuple[str, ...]
    warnings: tuple[str, ...]
    width: int | None = None
    height: int | None = None
    image_format: str | None = None
    mime_type: str | None = None
    file_size: int | None = None
    data_uri_size: int | None = None

    @property
    def aspect_ratio(self) -> float | None:
        if self.width is None or self.height in (None, 0):
            return None
        return self.width / self.height

    def summary(self) -> str:
        if self.width is None or self.height is None:
            details = "이미지 정보를 읽지 못했습니다."
        else:
            details = (
                f"{self.image_format} · {self.width}×{self.height} · "
                f"비율 {self.aspect_ratio:.3f} · 파일 {self.file_size / 1024:.1f} KiB · "
                f"data URI {self.data_uri_size / 1024:.1f} KiB"
            )

        lines = [details]
        lines.extend(f"오류: {message}" for message in self.errors)
        lines.extend(f"참고: {message}" for message in self.warnings)
        return "\n\n".join(lines)


class ImageValidator:
    """Validate only documented, deterministic Runway input constraints."""

    def validate(self, image_path: str | Path) -> ImageValidationResult:
        path = Path(image_path)
        if not path.is_file():
            return ImageValidationResult(False, ("이미지 파일을 찾을 수 없습니다.",), ())

        file_size = path.stat().st_size
        errors: list[str] = []
        warnings: list[str] = []

        try:
            with Image.open(path) as image:
                image.verify()
            with Image.open(path) as image:
                width, height = image.size
                image_format = (image.format or "").upper()
        except (UnidentifiedImageError, OSError, ValueError):
            return ImageValidationResult(
                False,
                ("손상되었거나 지원하지 않는 이미지입니다.",),
                (),
                file_size=file_size,
            )

        format_config = SUPPORTED_FORMATS.get(image_format)
        mime_type = format_config[0] if format_config else None
        if format_config is None:
            errors.append("Runway는 JPEG, PNG, WebP 이미지만 지원합니다.")

        aspect_ratio = width / height if height else 0
        if not RUNWAY_MIN_ASPECT_RATIO <= aspect_ratio <= RUNWAY_MAX_ASPECT_RATIO:
            errors.append(
                "gen4.5 입력 이미지 비율은 0.5 이상 2.0 이하여야 합니다."
            )

        prefix_size = len(f"data:{mime_type or 'application/octet-stream'};base64,")
        encoded_size = 4 * ((file_size + 2) // 3) + prefix_size
        if encoded_size > RUNWAY_DATA_URI_LIMIT_BYTES:
            errors.append("Runway data URI 입력 제한 5 MB를 초과합니다.")

        if min(width, height) < RECOMMENDED_MIN_EDGE:
            warnings.append("Runway는 640px 미만의 변을 권장하지 않습니다.")
        if max(width, height) > RECOMMENDED_MAX_EDGE:
            warnings.append("Runway는 4K보다 큰 참조 이미지를 권장하지 않습니다.")

        return ImageValidationResult(
            not errors,
            tuple(errors),
            tuple(warnings),
            width=width,
            height=height,
            image_format=image_format,
            mime_type=mime_type,
            file_size=file_size,
            data_uri_size=encoded_size,
        )

    def copy_to_outputs(self, image_path: str | Path, output_dir: str | Path) -> Path:
        source = Path(image_path).resolve()
        result = self.validate(source)
        if not result.valid:
            raise ValueError(result.summary())

        extension = SUPPORTED_FORMATS[result.image_format or ""][1]
        destination_dir = Path(output_dir).resolve()
        destination_dir.mkdir(parents=True, exist_ok=True)
        if source.parent == destination_dir:
            return source

        destination = destination_dir / f"uploaded-{uuid.uuid4().hex[:12]}{extension}"
        shutil.copy2(source, destination)
        return destination

    def to_data_uri(self, image_path: str | Path) -> str:
        path = Path(image_path)
        result = self.validate(path)
        if not result.valid or result.mime_type is None:
            raise ValueError(result.summary())
        encoded = base64.b64encode(path.read_bytes()).decode("ascii")
        return f"data:{result.mime_type};base64,{encoded}"
