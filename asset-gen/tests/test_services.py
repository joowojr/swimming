from pathlib import Path
from types import SimpleNamespace

import pytest
from PIL import Image

from services.image_generator import ImageGenerator, MissingOpenAIKeyError
from services.media_uploader import MediaUploadOptions, MediaUploader
from services.video_generator import (
    MissingRunwayKeyError,
    VideoGenerator,
    VideoOptions,
)


def valid_image(path: Path) -> Path:
    Image.new("RGB", (1280, 720), "#6a9488").save(path, format="JPEG")
    return path


def video_options() -> VideoOptions:
    return VideoOptions(
        motion_subjects="water ripples",
        static_subjects="camera and building",
        motion_speed="slow",
        duration=5,
        additional_instruction="Locked camera.",
        ratio="1280:720",
    )


def test_openai_key가_없으면_명확한_오류를_낸다(tmp_path: Path) -> None:
    generator = ImageGenerator(tmp_path, api_key="")

    with pytest.raises(MissingOpenAIKeyError, match="OPENAI_API_KEY"):
        generator.generate("quiet pool at dawn")


def test_runway_key가_없으면_실제_요청을_보내지_않는다(tmp_path: Path) -> None:
    image = valid_image(tmp_path / "source.jpg")
    generator = VideoGenerator(tmp_path, api_key="")

    with pytest.raises(MissingRunwayKeyError, match="RUNWAYML_API_SECRET"):
        generator.create_task(image, video_options())


def test_runway_sdk에_현재_image_to_video_payload를_전달한다(tmp_path: Path) -> None:
    image = valid_image(tmp_path / "source.jpg")
    captured: dict[str, object] = {}

    class ImageToVideo:
        def create(self, **kwargs):
            captured.update(kwargs)
            return SimpleNamespace(id="task-123")

    client = SimpleNamespace(image_to_video=ImageToVideo())
    generator = VideoGenerator(tmp_path, api_key="test", client=client)

    task_id = generator.create_task(image, video_options())

    assert task_id == "task-123"
    assert captured["model"] == "gen4.5"
    assert str(captured["prompt_image"]).startswith("data:image/jpeg;base64,")
    assert captured["ratio"] == "1280:720"
    assert captured["duration"] == 5
    assert captured["output_format"] == "mp4"
    assert "water ripples" in str(captured["prompt_text"])


def test_upload_script의_실제_인자_순서를_사용한다(tmp_path: Path) -> None:
    video = tmp_path / "result.mp4"
    video.write_bytes(b"video")
    uploader = MediaUploader(tmp_path)
    options = MediaUploadOptions(
        bucket="media-bucket",
        place_id=1,
        city_id=2,
        place_name="Pool",
        logical_name="2_pool_1",
        city_name="Seoul",
        country_code="KR",
        timezone="Asia/Seoul",
    )

    command = uploader.build_command(video, options)

    assert command[1:] == (
        "--file",
        str(video.resolve()),
        "--bucket",
        "media-bucket",
        "--place-id",
        "1",
        "--city-id",
        "2",
        "--place-name",
        "Pool",
        "--logical-name",
        "2_pool_1",
        "--city-name",
        "Seoul",
        "--country-code",
        "KR",
        "--timezone",
        "Asia/Seoul",
    )
