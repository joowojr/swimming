from __future__ import annotations

import time
from pathlib import Path

import gradio as gr
from dotenv import load_dotenv

from services.image_generator import ImageGenerationError, ImageGenerator
from services.image_validator import ImageValidator
from services.media_uploader import (
    MediaUploadError,
    MediaUploadOptions,
    MediaUploader,
)
from services.video_generator import (
    SUPPORTED_RATIOS,
    TERMINAL_FAILURE_STATES,
    VideoGenerationError,
    VideoGenerator,
    VideoOptions,
)


ASSET_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = ASSET_DIR.parent
OUTPUT_DIR = ASSET_DIR / "outputs"
CSS_PATH = ASSET_DIR / "styles.css"
load_dotenv(ASSET_DIR / ".env")

image_validator = ImageValidator()
image_generator = ImageGenerator(OUTPUT_DIR)
video_generator = VideoGenerator(OUTPUT_DIR, validator=image_validator)
media_uploader = MediaUploader(REPOSITORY_ROOT)


def _file_path(value: object) -> str:
    if isinstance(value, str):
        return value
    name = getattr(value, "name", None)
    return str(name) if name else ""


def switch_source_mode(mode: str):
    return gr.update(visible=mode == "새 이미지 생성"), gr.update(
        visible=mode == "기존 이미지 사용"
    )


def generate_image(prompt: str):
    try:
        path = image_generator.generate(prompt)
        validation = image_validator.validate(path)
        return (
            str(path),
            str(path),
            f"### 자동 검수\n\n{validation.summary()}",
            "생성된 이미지를 확인하고 승인해 주세요.",
            "",
        )
    except ImageGenerationError as exc:
        return None, "", "### 자동 검수\n\n검수할 이미지가 없습니다.", f"오류: {exc}", ""


def use_uploaded_image(uploaded_file: object):
    source = _file_path(uploaded_file)
    if not source:
        return None, "", "### 자동 검수\n\n이미지를 업로드해 주세요.", "대기", ""
    try:
        validation = image_validator.validate(source)
        if not validation.valid:
            return source, "", f"### 자동 검수\n\n{validation.summary()}", "검수 실패", ""
        path = image_validator.copy_to_outputs(source, OUTPUT_DIR)
        return (
            str(path),
            str(path),
            f"### 자동 검수\n\n{validation.summary()}",
            "이미지를 확인하고 승인해 주세요.",
            "",
        )
    except (OSError, ValueError) as exc:
        return None, "", "### 자동 검수\n\n검수할 수 없습니다.", f"오류: {exc}", ""


def approve_image(candidate_path: str):
    if not candidate_path:
        return "", "승인할 이미지를 먼저 준비해 주세요."
    validation = image_validator.validate(candidate_path)
    if not validation.valid:
        return "", f"승인할 수 없습니다.\n\n{validation.summary()}"
    return candidate_path, "이미지가 승인되었습니다. 영상 옵션을 입력할 수 있습니다."


def create_video(
    approved_path: str,
    motion_subjects: str,
    static_subjects: str,
    motion_speed: str,
    duration: int,
    additional_instruction: str,
    ratio: str,
):
    if not approved_path:
        yield "### 상태: 실패\n\n먼저 이미지를 승인해 주세요.", "", None, ""
        return

    options = VideoOptions(
        motion_subjects=motion_subjects,
        static_subjects=static_subjects,
        motion_speed=motion_speed,
        duration=int(duration),
        additional_instruction=additional_instruction,
        ratio=ratio,
    )
    yield "### 상태: 요청 중\n\nRunway에 생성 요청을 보내고 있습니다.", "", None, ""
    try:
        task_id = video_generator.create_task(approved_path, options)
        yield "### 상태: 대기\n\nRunway task가 대기열에 있습니다.", task_id, None, ""

        deadline = time.monotonic() + video_generator.poll_timeout_seconds()
        while time.monotonic() < deadline:
            snapshot = video_generator.retrieve_task(task_id)
            if snapshot.status == "SUCCEEDED":
                if not snapshot.output_urls:
                    raise VideoGenerationError("완료된 task에 결과 URL이 없습니다.")
                video_path = video_generator.download_output(snapshot.output_urls[0])
                yield (
                    "### 상태: 완료\n\n영상 다운로드와 로컬 저장을 마쳤습니다.",
                    task_id,
                    str(video_path),
                    str(video_path),
                )
                return
            if snapshot.status in TERMINAL_FAILURE_STATES:
                yield f"### 상태: 실패\n\n{snapshot.failure}", task_id, None, ""
                return

            display_state = "대기" if snapshot.status in {"PENDING", "THROTTLED"} else "생성 중"
            yield (
                f"### 상태: {display_state}\n\nRunway 상태: `{snapshot.status}`",
                task_id,
                None,
                "",
            )
            time.sleep(video_generator.poll_interval_seconds())

        yield "### 상태: 실패\n\n상태 조회 제한 시간을 초과했습니다.", task_id, None, ""
    except (VideoGenerationError, ValueError) as exc:
        yield f"### 상태: 실패\n\n{exc}", "", None, ""


def upload_media(
    video_path: str,
    bucket: str,
    place_id: int,
    city_id: int,
    place_name: str,
    logical_name: str,
    city_name: str,
    country_code: str,
    timezone: str,
):
    if not video_path:
        return "### 업로드 실패\n\n생성 완료된 영상이 없습니다.", "exit code: 실행 안 함"
    try:
        options = MediaUploadOptions(
            bucket=bucket,
            place_id=int(place_id),
            city_id=int(city_id),
            place_name=place_name,
            logical_name=logical_name,
            city_name=city_name,
            country_code=country_code,
            timezone=timezone,
        )
        result = media_uploader.run(video_path, options)
    except (MediaUploadError, TypeError, ValueError) as exc:
        return f"### 업로드 실패\n\n{exc}", "exit code: 실행 안 함"

    heading = "업로드 완료" if result.exit_code == 0 else "업로드 실패"
    output = result.output or "스크립트 출력이 없습니다."
    return f"### {heading}\n\nexit code: `{result.exit_code}`", output


def build_app() -> gr.Blocks:
    with gr.Blocks(title="Asset Generation Pipeline") as demo:
        candidate_image = gr.State("")
        approved_image = gr.State("")
        generated_video = gr.State("")

        gr.Markdown(
            "# Asset Generation Pipeline\n"
            "이미지를 만들거나 가져온 뒤 검수하고, Runway 영상 생성과 기존 S3 업로드까지 이어갑니다."
        )

        with gr.Row(equal_height=False):
            with gr.Column(scale=1, elem_classes="pipeline-panel"):
                gr.Markdown("## 1. 이미지 선택")
                source_mode = gr.Radio(
                    ["새 이미지 생성", "기존 이미지 사용"],
                    value="새 이미지 생성",
                    label="입력 방식",
                )
                with gr.Group(visible=True) as generate_group:
                    image_prompt = gr.Textbox(
                        label="이미지 prompt",
                        lines=5,
                        placeholder="장면, 구도, 조명, 스타일을 구체적으로 입력하세요.",
                    )
                    with gr.Row():
                        generate_button = gr.Button("이미지 생성", variant="primary")
                        regenerate_button = gr.Button("prompt로 재생성")
                with gr.Group(visible=False) as upload_group:
                    upload_input = gr.File(
                        label="로컬 이미지",
                        file_types=["image"],
                        type="filepath",
                    )

                gr.Markdown("## 2. 이미지 검수")
                image_preview = gr.Image(label="Preview", type="filepath", interactive=False)
                image_inspection = gr.Markdown("### 자동 검수\n\n검수할 이미지가 없습니다.")
                approve_button = gr.Button("이미지 승인")
                approval_status = gr.Markdown("대기")

            with gr.Column(scale=1, elem_classes="pipeline-panel"):
                gr.Markdown("## 3. 영상 생성 옵션")
                motion_subjects = gr.Textbox(
                    label="움직일 요소",
                    placeholder="예: 수면의 잔물결과 나뭇잎",
                )
                static_subjects = gr.Textbox(
                    label="움직이지 않을 요소",
                    placeholder="예: 카메라, 건물, 테이블",
                )
                with gr.Row():
                    motion_speed = gr.Dropdown(
                        ["very slow", "slow", "natural", "fast"],
                        value="slow",
                        label="움직임 속도",
                    )
                    duration = gr.Slider(2, 10, value=5, step=1, label="영상 길이(초)")
                ratio = gr.Dropdown(
                    list(SUPPORTED_RATIOS),
                    value="1280:720",
                    label="출력 비율",
                )
                additional_instruction = gr.Textbox(
                    label="추가 영상 prompt",
                    lines=4,
                    placeholder="예: Locked camera, soft morning light, seamless motion.",
                )
                video_button = gr.Button("Runway 영상 생성", variant="primary")
                video_status = gr.Markdown("### 상태: 대기\n\n승인된 이미지를 기다리고 있습니다.")
                task_id_output = gr.Textbox(label="Runway task id", interactive=False)
                video_preview = gr.Video(label="영상 Preview", interactive=False)

        with gr.Accordion("4. 미디어 업로드", open=False, elem_classes="upload-panel"):
            gr.Markdown(
                "기존 `infra/scripts/upload-media.sh`를 실행합니다. 원본/썸네일 업로드 후 "
                "`R__seed_places.sql`도 갱신됩니다. AWS credential은 로컬 AWS CLI 설정을 사용합니다."
            )
            with gr.Row():
                bucket = gr.Textbox(label="S3 bucket")
                logical_name = gr.Textbox(label="logical name", placeholder="1_lisbon_1")
            with gr.Row():
                place_id = gr.Number(label="place id", precision=0, minimum=1)
                city_id = gr.Number(label="city id", precision=0, minimum=1)
            with gr.Row():
                place_name = gr.Textbox(label="place name")
                city_name = gr.Textbox(label="city name")
            with gr.Row():
                country_code = gr.Textbox(label="country code", placeholder="PT", max_lines=1)
                timezone = gr.Textbox(label="timezone", placeholder="Europe/Lisbon", max_lines=1)
            upload_button = gr.Button("기존 파이프라인으로 업로드")
            upload_status = gr.Markdown("업로드하지 않음")
            upload_output = gr.Code(label="스크립트 결과", language="shell")

        source_mode.change(
            switch_source_mode,
            inputs=source_mode,
            outputs=[generate_group, upload_group],
        )
        for button in (generate_button, regenerate_button):
            button.click(
                generate_image,
                inputs=image_prompt,
                outputs=[
                    image_preview,
                    candidate_image,
                    image_inspection,
                    approval_status,
                    approved_image,
                ],
            )
        upload_input.change(
            use_uploaded_image,
            inputs=upload_input,
            outputs=[
                image_preview,
                candidate_image,
                image_inspection,
                approval_status,
                approved_image,
            ],
        )
        approve_button.click(
            approve_image,
            inputs=candidate_image,
            outputs=[approved_image, approval_status],
        )
        video_button.click(
            create_video,
            inputs=[
                approved_image,
                motion_subjects,
                static_subjects,
                motion_speed,
                duration,
                additional_instruction,
                ratio,
            ],
            outputs=[video_status, task_id_output, video_preview, generated_video],
        )
        upload_button.click(
            upload_media,
            inputs=[
                generated_video,
                bucket,
                place_id,
                city_id,
                place_name,
                logical_name,
                city_name,
                country_code,
                timezone,
            ],
            outputs=[upload_status, upload_output],
        )
    return demo


demo = build_app()


if __name__ == "__main__":
    demo.queue().launch(css_paths=CSS_PATH)
