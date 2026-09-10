from __future__ import annotations

import os
import time
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from runwayml import APIConnectionError, APIStatusError, APITimeoutError, RunwayML

from services.image_validator import ImageValidator


SUPPORTED_RATIOS = (
    "1280:720",
    "720:1280",
    "1104:832",
    "960:960",
    "832:1104",
    "1584:672",
)
TERMINAL_FAILURE_STATES = {"FAILED", "CANCELED", "CANCELLED"}


class VideoGenerationError(RuntimeError):
    pass


class MissingRunwayKeyError(VideoGenerationError):
    pass


@dataclass(frozen=True)
class VideoOptions:
    motion_subjects: str
    static_subjects: str
    motion_speed: str
    duration: int
    additional_instruction: str
    ratio: str


@dataclass(frozen=True)
class TaskSnapshot:
    task_id: str
    status: str
    output_urls: tuple[str, ...] = ()
    failure: str | None = None


class VideoGenerator:
    def __init__(
        self,
        output_dir: str | Path,
        api_key: str | None = None,
        model: str | None = None,
        client: RunwayML | None = None,
        validator: ImageValidator | None = None,
    ) -> None:
        self.output_dir = Path(output_dir)
        self.api_key = api_key if api_key is not None else os.getenv(
            "RUNWAYML_API_SECRET", ""
        )
        self.model = model or os.getenv("RUNWAY_VIDEO_MODEL", "gen4.5")
        self._provided_client = client
        self.validator = validator or ImageValidator()

    def _client(self) -> RunwayML:
        if self._provided_client is not None:
            return self._provided_client
        if not self.api_key:
            raise MissingRunwayKeyError(
                "RUNWAYML_API_SECRET가 설정되지 않아 영상을 생성할 수 없습니다. "
                "키를 설정한 뒤 앱을 다시 시작해 주세요."
            )
        return RunwayML(api_key=self.api_key, timeout=60.0, max_retries=2)

    @staticmethod
    def build_prompt(options: VideoOptions) -> str:
        if not options.motion_subjects.strip():
            raise VideoGenerationError("움직일 요소를 입력해 주세요.")
        if options.ratio not in SUPPORTED_RATIOS:
            raise VideoGenerationError("지원하지 않는 영상 비율입니다.")
        if not 2 <= int(options.duration) <= 10:
            raise VideoGenerationError("영상 길이는 2초 이상 10초 이하여야 합니다.")

        parts = [
            f"Animate: {options.motion_subjects.strip()}.",
            f"Motion speed: {options.motion_speed.strip() or 'natural'}.",
        ]
        if options.static_subjects.strip():
            parts.append(f"Keep completely still: {options.static_subjects.strip()}.")
        if options.additional_instruction.strip():
            parts.append(options.additional_instruction.strip())
        prompt = " ".join(parts)
        utf16_code_units = len(prompt.encode("utf-16-le")) // 2
        if utf16_code_units > 1000:
            raise VideoGenerationError(
                "조합된 Runway prompt가 1000자를 초과합니다. 입력을 줄여 주세요."
            )
        return prompt

    def create_task(self, image_path: str | Path, options: VideoOptions) -> str:
        prompt_image = self.validator.to_data_uri(image_path)
        prompt_text = self.build_prompt(options)
        try:
            task = self._client().image_to_video.create(
                model=self.model,
                prompt_image=prompt_image,
                prompt_text=prompt_text,
                ratio=options.ratio,
                duration=int(options.duration),
                output_format="mp4",
            )
        except (APITimeoutError, APIConnectionError) as exc:
            raise VideoGenerationError(
                "Runway 생성 요청이 시간 초과되었거나 연결에 실패했습니다."
            ) from exc
        except APIStatusError as exc:
            raise VideoGenerationError(
                f"Runway 생성 요청이 실패했습니다. HTTP {exc.status_code}"
            ) from exc
        task_id = getattr(task, "id", None)
        if not task_id:
            raise VideoGenerationError("Runway가 task id를 반환하지 않았습니다.")
        return str(task_id)

    def retrieve_task(self, task_id: str) -> TaskSnapshot:
        try:
            task = self._client().tasks.retrieve(task_id)
        except (APITimeoutError, APIConnectionError) as exc:
            raise VideoGenerationError("Runway task 상태 조회에 실패했습니다.") from exc
        except APIStatusError as exc:
            raise VideoGenerationError(
                f"Runway task 상태 조회가 실패했습니다. HTTP {exc.status_code}"
            ) from exc

        status = str(getattr(task, "status", "UNKNOWN")).upper()
        raw_output = getattr(task, "output", None) or []
        output_urls = tuple(str(item) for item in raw_output if item)
        failure = self._failure_message(task) if status in TERMINAL_FAILURE_STATES else None
        return TaskSnapshot(str(getattr(task, "id", task_id)), status, output_urls, failure)

    @staticmethod
    def _failure_message(task: Any) -> str:
        failure = getattr(task, "failure", None)
        if failure:
            return str(failure)
        failure_code = getattr(task, "failure_code", None)
        return str(failure_code) if failure_code else "Runway task가 실패했습니다."

    def download_output(self, output_url: str) -> Path:
        parsed = urllib.parse.urlparse(output_url)
        if parsed.scheme != "https" or not parsed.netloc:
            raise VideoGenerationError("Runway 결과 URL이 유효한 HTTPS URL이 아닙니다.")

        self.output_dir.mkdir(parents=True, exist_ok=True)
        destination = self.output_dir / f"video-{uuid.uuid4().hex[:12]}.mp4"
        temporary = destination.with_suffix(".download")
        request = urllib.request.Request(
            output_url,
            headers={"User-Agent": "swimming-asset-gen/1.0"},
        )
        try:
            with urllib.request.urlopen(request, timeout=180) as response, temporary.open(
                "wb"
            ) as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
        except (OSError, TimeoutError) as exc:
            temporary.unlink(missing_ok=True)
            raise VideoGenerationError("완성된 Runway 영상을 다운로드하지 못했습니다.") from exc
        temporary.replace(destination)
        return destination

    @staticmethod
    def poll_interval_seconds() -> float:
        try:
            configured = float(os.getenv("RUNWAY_POLL_INTERVAL_SECONDS", "5"))
        except ValueError:
            configured = 5.0
        return max(5.0, configured)

    @staticmethod
    def poll_timeout_seconds() -> float:
        try:
            configured = float(os.getenv("RUNWAY_POLL_TIMEOUT_SECONDS", "900"))
        except ValueError:
            configured = 900.0
        return max(30.0, configured)
