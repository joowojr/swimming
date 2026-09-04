# syntax=docker/dockerfile:1

FROM eclipse-temurin:21.0.8_9-jdk-jammy@sha256:adb9b2d15adf1833d9dae0bdc1cff61ef5a804dc58dfbfb34269f32432b2e5dc AS builder

WORKDIR /workspace

COPY . .

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x gradlew \
    && ./gradlew --no-daemon clean bootJar

RUN java -Djarmode=tools -jar build/libs/*.jar \
    extract --layers --destination /workspace/extracted

FROM eclipse-temurin:21.0.8_9-jre-jammy@sha256:db1689535962d757a5adabf57387584ed543d38c0b9d1fe870123ea362ad73b0 AS runtime

RUN groupadd --system --gid 10001 swimming \
    && useradd --system --uid 10001 --gid swimming --home-dir /application swimming

WORKDIR /application

COPY --from=builder --chown=swimming:swimming /workspace/extracted/dependencies/ ./
COPY --from=builder --chown=swimming:swimming /workspace/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=swimming:swimming /workspace/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=swimming:swimming /workspace/extracted/application/ ./

RUN mv backend-*.jar application.jar

# sslmode=verify-full 은 루트 인증서를 요구하고, pgjdbc 의 기본 탐색 경로는
# ~/.postgresql/root.crt 다. 컨테이너에는 그 파일이 없으므로 RDS 트러스트 스토어
# 번들을 이미지에 넣고 DB_URL 의 sslrootcert 로 가리킨다. 번들을 빼면 기동 시
# DB 연결이 SSLException 으로 실패한다.
ADD --chown=swimming:swimming --chmod=644 \
    https://truststore.pki.rds.amazonaws.com/ap-northeast-2/ap-northeast-2-bundle.pem \
    /application/rds-ca.pem

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Dfile.encoding=UTF-8" \
    TZ=UTC

USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
