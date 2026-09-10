import gradio as gr
from PIL import Image

import app


def test_gradio_blocks를_구성할_수_있다() -> None:
    demo = app.build_app()

    assert isinstance(demo, gr.Blocks)
    assert app.CSS_PATH.is_file()


def test_기존_이미지를_outputs로_복사하고_preview할_수_있다(tmp_path, monkeypatch) -> None:
    source = tmp_path / "source.jpg"
    Image.new("RGB", (1280, 720), "#5e8f80").save(source, format="JPEG")
    monkeypatch.setattr(app, "OUTPUT_DIR", tmp_path / "outputs")

    preview, candidate, inspection, status, approved = app.use_uploaded_image(str(source))

    assert preview == candidate
    assert (tmp_path / "outputs") in (tmp_path / candidate).parents
    assert "JPEG" in inspection
    assert "승인" in status
    assert approved == ""
