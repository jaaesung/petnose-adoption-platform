# ─── 빌드 스테이지 ────────────────────────────────────────────────────────────
FROM gradle:8.7-jdk21 AS build

WORKDIR /app

# 의존성 캐시 레이어: build 파일만 먼저 복사
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon --quiet 2>/dev/null || true

# 소스 복사 후 JAR 빌드 (테스트 제외)
COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ─── 런타임 스테이지 ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre

WORKDIR /app

# healthcheck 및 운영용 도구 설치
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/petnose-api.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
