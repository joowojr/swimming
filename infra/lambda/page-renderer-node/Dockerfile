FROM public.ecr.aws/lambda/nodejs:24

WORKDIR ${LAMBDA_TASK_ROOT}

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY app.mjs notion_renderer.mjs naver_renderer.mjs youtube_renderer.mjs ./

CMD ["app.handler"]
