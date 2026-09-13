# Chromium을 백엔드 컨테이너 밖에서 돌리기 위한 렌더링 전용 이미지.
#
# 베이스는 Python 3.12(AL2023, glibc 2.34)를 쓴다. Lambda Python 3.11 이미지는 glibc 2.28이라
# Playwright가 요구하는 라이브러리를 채우지 못한다.
#
# 빌드는 반드시 단일 아키텍처로 푸시한다. buildx 기본값으로 밀면 manifest list가 올라가고
# Lambda가 그 이미지를 거부한다.
#   docker buildx build --platform linux/amd64 --provenance=false --sbom=false ...
FROM public.ecr.aws/lambda/python:3.12

# 런타임 파일시스템은 읽기 전용이라 $HOME 아래에 브라우저를 둘 수 없다. 빌드 시점에
# 이미지 경로로 고정하고, 실행 시에도 같은 값을 보게 ENV로 남긴다.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

# playwright install-deps 는 Debian/Ubuntu만 지원한다. AL2023에서는 dnf로 직접 채운다.
RUN dnf install -y \
        alsa-lib \
        atk \
        at-spi2-atk \
        at-spi2-core \
        cups-libs \
        libdrm \
        libX11 \
        libXcomposite \
        libXdamage \
        libXext \
        libXfixes \
        libXrandr \
        libxcb \
        libxkbcommon \
        mesa-libgbm \
        nspr \
        nss \
        pango \
        liberation-fonts \
    && dnf clean all \
    && rm -rf /var/cache/dnf

COPY requirements.txt ${LAMBDA_TASK_ROOT}/
RUN pip install --no-cache-dir -r ${LAMBDA_TASK_ROOT}/requirements.txt \
    && playwright install chromium

COPY app.py notion_renderer.py ${LAMBDA_TASK_ROOT}/

CMD ["app.handler"]
