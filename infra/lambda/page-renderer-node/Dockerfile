FROM public.ecr.aws/lambda/nodejs:20

WORKDIR ${LAMBDA_TASK_ROOT}

COPY package.json ./
RUN npm install --omit=dev

COPY app.mjs notion_renderer.mjs naver_renderer.mjs ./

CMD ["app.handler"]
