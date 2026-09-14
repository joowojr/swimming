# Playwright가 공식 지원하는 Ubuntu 24.04(Noble)와 정확히 일치하는 Chromium을 사용한다.
# Lambda Python 베이스 이미지는 Amazon Linux라 Playwright가 Ubuntu fallback 브라우저를
# 설치했고, 로컬에서는 성공하지만 운영 Lambda에서 renderer가 종료되는 차이가 있었다.
FROM public.ecr.aws/lambda/python:3.12 AS lambda-runtime-tools

FROM mcr.microsoft.com/playwright/python:v1.55.0-noble

ARG FUNCTION_DIR=/var/task

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright \
    DEBUG=pw:browser* \
    HOME=/tmp \
    XDG_CACHE_HOME=/tmp/.cache \
    PYTHONUNBUFFERED=1

WORKDIR ${FUNCTION_DIR}

# 비-AWS 베이스 이미지는 Lambda Runtime Interface Client가 필요하다. Playwright 이미지에는
# 브라우저와 OS 의존성이 이미 있으므로 playwright install을 다시 실행하지 않는다.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY --from=lambda-runtime-tools /usr/local/bin/aws-lambda-rie /usr/local/bin/aws-lambda-rie
COPY --chmod=755 lambda-entrypoint.sh /lambda-entrypoint.sh
COPY app.py notion_renderer.py ./

ENTRYPOINT ["/lambda-entrypoint.sh"]
CMD ["app.handler"]
