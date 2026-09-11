from __future__ import annotations

import base64
import binascii
import os
import uuid
from pathlib import Path

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI


class ImageGenerationError(RuntimeError):
    pass


class MissingOpenAIKeyError(ImageGenerationError):
    pass


class ImageGenerator:
    def __init__(
        self,
        output_dir: str | Path,
        api_key: str | None = None,
        model: str | None = None,
        client: OpenAI | None = None,
    ) -> None:
        self.output_dir = Path(output_dir)
        self.api_key = api_key if api_key is not None else os.getenv("OPENAI_API_KEY", "")
        self.model = model or os.getenv(
            "OPENAI_IMAGE_MODEL", "gpt-image-2.5-sunburst"
        )
        self._provided_client = client

    def generate(self, prompt: str) -> Path:
        normalized_prompt = prompt.strip()
        if not normalized_prompt:
            raise ImageGenerationError("이미지 prompt를 입력해 주세요.")
        if not self.api_key and self._provided_client is None:
            raise MissingOpenAIKeyError(
                "OPENAI_API_KEY가 설정되지 않아 이미지를 생성할 수 없습니다. "
                "키를 설정한 뒤 앱을 다시 시작해 주세요."
            )

        client = self._provided_client or OpenAI(
            api_key=self.api_key,
            timeout=180.0,
            max_retries=2,
        )
        try:
            response = client.images.generate(
                model=self.model,
                prompt=normalized_prompt,
                size="1536x1024",
                quality="medium",
                output_format="jpeg",
                output_compression=90,
            )
            if not response.data or not response.data[0].b64_json:
                raise ImageGenerationError("OpenAI가 이미지 데이터를 반환하지 않았습니다.")
            image_bytes = base64.b64decode(response.data[0].b64_json, validate=True)
        except (APITimeoutError, APIConnectionError) as exc:
            raise ImageGenerationError(
                "OpenAI 이미지 생성 요청이 시간 초과되었거나 연결에 실패했습니다."
            ) from exc
        except APIStatusError as exc:
            raise ImageGenerationError(
                f"OpenAI 이미지 생성 요청이 실패했습니다. HTTP {exc.status_code}"
            ) from exc
        except (binascii.Error, ValueError) as exc:
            raise ImageGenerationError("OpenAI 이미지 응답을 해석하지 못했습니다.") from exc

        self.output_dir.mkdir(parents=True, exist_ok=True)
        destination = self.output_dir / f"generated-{uuid.uuid4().hex[:12]}.jpg"
        temporary = destination.with_suffix(".tmp")
        temporary.write_bytes(image_bytes)
        temporary.replace(destination)
        return destination
