from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from pathlib import Path


LOGICAL_NAME_PATTERN = re.compile(r"^[A-Za-z0-9_-]+$")
COUNTRY_CODE_PATTERN = re.compile(r"^[A-Z]{2}$")
TIMEZONE_PATTERN = re.compile(r"^[A-Za-z_+-]+(?:/[A-Za-z0-9_+-]+)+$")
SECRET_PATTERN = re.compile(
    r"(?i)(aws_access_key_id|aws_secret_access_key|aws_session_token|authorization)"
    r"(\s*[=:]\s*)(\S+)"
)


class MediaUploadError(RuntimeError):
    pass


@dataclass(frozen=True)
class MediaUploadOptions:
    bucket: str
    place_id: int
    city_id: int
    place_name: str
    logical_name: str
    city_name: str
    country_code: str
    timezone: str


@dataclass(frozen=True)
class MediaUploadResult:
    exit_code: int
    output: str


class MediaUploader:
    def __init__(self, repository_root: str | Path) -> None:
        self.repository_root = Path(repository_root).resolve()
        self.script_path = self.repository_root / "infra/scripts/upload-media.sh"

    def build_command(
        self, video_path: str | Path, options: MediaUploadOptions
    ) -> tuple[str, ...]:
        self._validate(video_path, options)
        return (
            str(self.script_path),
            "--file",
            str(Path(video_path).resolve()),
            "--bucket",
            options.bucket.strip(),
            "--place-id",
            str(options.place_id),
            "--city-id",
            str(options.city_id),
            "--place-name",
            options.place_name.strip(),
            "--logical-name",
            options.logical_name.strip(),
            "--city-name",
            options.city_name.strip(),
            "--country-code",
            options.country_code.strip(),
            "--timezone",
            options.timezone.strip(),
        )

    def run(
        self, video_path: str | Path, options: MediaUploadOptions
    ) -> MediaUploadResult:
        if not self.script_path.is_file():
            raise MediaUploadError(f"업로드 스크립트를 찾을 수 없습니다: {self.script_path}")
        command = self.build_command(video_path, options)
        try:
            completed = subprocess.run(
                command,
                cwd=self.repository_root,
                capture_output=True,
                text=True,
                timeout=1800,
                check=False,
            )
        except subprocess.TimeoutExpired as exc:
            raise MediaUploadError("미디어 업로드가 30분 제한을 초과했습니다.") from exc
        except OSError as exc:
            raise MediaUploadError("업로드 스크립트를 실행하지 못했습니다.") from exc

        combined = "\n".join(
            section.strip() for section in (completed.stdout, completed.stderr) if section.strip()
        )
        return MediaUploadResult(completed.returncode, self._redact(combined))

    def _validate(self, video_path: str | Path, options: MediaUploadOptions) -> None:
        video = Path(video_path)
        if not video.is_file():
            raise MediaUploadError("업로드할 영상 파일을 찾을 수 없습니다.")
        if video.suffix.lower() != ".mp4":
            raise MediaUploadError("기존 업로드 스크립트에는 MP4 파일만 전달할 수 있습니다.")
        if not options.bucket.strip():
            raise MediaUploadError("S3 bucket 이름을 입력해 주세요.")
        if options.place_id <= 0 or options.city_id <= 0:
            raise MediaUploadError("place id와 city id는 양의 정수여야 합니다.")
        if not options.place_name.strip() or "\n" in options.place_name:
            raise MediaUploadError("place name을 한 줄로 입력해 주세요.")
        if not options.city_name.strip() or "\n" in options.city_name:
            raise MediaUploadError("city name을 한 줄로 입력해 주세요.")
        if not LOGICAL_NAME_PATTERN.fullmatch(options.logical_name.strip()):
            raise MediaUploadError("logical name은 영문, 숫자, 밑줄, 하이픈만 허용합니다.")
        if not COUNTRY_CODE_PATTERN.fullmatch(options.country_code.strip()):
            raise MediaUploadError("country code는 ISO alpha-2 대문자여야 합니다.")
        if not TIMEZONE_PATTERN.fullmatch(options.timezone.strip()):
            raise MediaUploadError("timezone 형식이 올바르지 않습니다.")

    @staticmethod
    def _redact(value: str) -> str:
        return SECRET_PATTERN.sub(r"\1\2[REDACTED]", value)
