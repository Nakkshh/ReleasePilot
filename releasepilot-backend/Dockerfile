# syntax=docker/dockerfile:1

# ---- build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
# Tests need Postgres and are run separately (mvn clean test), not during the image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

# ---- runtime (Ubuntu-based JRE, glibc: needed by ONNX Runtime) ----
FROM eclipse-temurin:17-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home --home-dir /app app
WORKDIR /app

COPY --from=build --chown=app:app /build/target/*.jar /app/app.jar
# The model files are git-ignored, so they come from the local build context.
COPY --chown=app:app models/ /app/models/

RUN mkdir -p /app/cache/tokenizers /app/cache/djl && chown -R app:app /app/cache

ENV EMBEDDING_MODEL_DIR=/app/models \
    TOKENIZERS_CACHE=/app/cache/tokenizers \
    DJL_CACHE_DIR=/app/cache/djl \
    JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]