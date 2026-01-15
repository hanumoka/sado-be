# =============================================================================
# SADO MiniPACS Backend - Docker Image
# =============================================================================
# Multi-stage build for optimized image size
# Base: Eclipse Temurin JDK 21
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM gradle:8.14-jdk21 AS builder

WORKDIR /app

# Copy Gradle configuration first (for caching)
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY sado-common/build.gradle ./sado-common/
COPY sado-minipacs/build.gradle ./sado-minipacs/

# Download dependencies (cached if build.gradle unchanged)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY sado-common/src ./sado-common/src
COPY sado-minipacs/src ./sado-minipacs/src

# Build JAR and copy OpenCV native library
RUN gradle :sado-minipacs:bootJar :sado-minipacs:copyOpenCVNatives --no-daemon -x test

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install curl for health check
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy JAR from builder
COPY --from=builder /app/sado-minipacs/build/libs/*.jar app.jar

# Copy OpenCV native library from builder
COPY --from=builder /app/sado-minipacs/build/natives/ /app/

# Environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Expose port
EXPOSE 10201

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:10201/actuator/health || exit 1

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS --add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -jar app.jar"]
