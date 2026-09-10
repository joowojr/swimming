from pathlib import Path

from PIL import Image

from services.image_validator import ImageValidator


def make_image(path: Path, size: tuple[int, int], image_format: str = "JPEG") -> Path:
    Image.new("RGB", size, "#7aa99a").save(path, format=image_format)
    return path


def test_runway_조건을_만족하는_jpeg를_승인한다(tmp_path: Path) -> None:
    image = make_image(tmp_path / "valid.jpg", (1280, 720))

    result = ImageValidator().validate(image)

    assert result.valid
    assert result.mime_type == "image/jpeg"
    assert result.aspect_ratio == 1280 / 720


def test_gen45_비율_범위를_벗어난_이미지를_거부한다(tmp_path: Path) -> None:
    image = make_image(tmp_path / "wide.jpg", (1800, 600))

    result = ImageValidator().validate(image)

    assert not result.valid
    assert any("비율" in error for error in result.errors)


def test_지원하지_않는_gif를_거부한다(tmp_path: Path) -> None:
    image = make_image(tmp_path / "image.gif", (800, 800), "GIF")

    result = ImageValidator().validate(image)

    assert not result.valid
    assert any("JPEG, PNG, WebP" in error for error in result.errors)
