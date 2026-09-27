# ==========================================
# 1. Builder Stage
# ==========================================
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Copy gradle wrapper and configurations for efficient layer caching
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# Pre-fetch dependencies
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build production distribution
COPY src ./src
RUN ./gradlew installDist --no-daemon -x test

# ==========================================
# 2. Runtime Stage
# ==========================================
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Create non-root user and group
RUN groupadd -g 10001 tracker && \
    useradd -u 10001 -g tracker -d /app -s /sbin/nologin tracker && \
    mkdir -p /app/data && \
    chown -R tracker:tracker /app

# Copy pre-packaged application from builder
COPY --from=builder --chown=tracker:tracker /app/build/install/tracker ./

USER tracker

# JVM optimizations: honor container memory limits, use G1GC, exit immediately on OOM
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"
ENV SQLITE_DB_PATH="/app/data/tracker.db"

VOLUME ["/app/data"]

ENTRYPOINT ["./bin/tracker"]
