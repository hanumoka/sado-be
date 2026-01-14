# =============================================================================
# SADO MiniPACS Backend - Docker Image
# =============================================================================
# Multi-stage build for optimized image size
# Base: Eclipse Temurin JDK 21
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM gradle:8.11-jdk21 AS builder

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

# Build JAR
RUN gradle :sado-minipacs:bootJar --no-daemon -x test

# -----------------------------------------------------------------------------
# Stage 2: Runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Install native library dependencies for OpenCV
RUN apt-get update && apt-get install -y --no-install-recommends \
    libopencv-core4.5d \
    libopencv-imgproc4.5d \
    libopencv-imgcodecs4.5d \
    && rm -rf /var/lib/apt/lists/*

# Copy JAR from builder
COPY --from=builder /app/sado-minipacs/build/libs/*.jar app.jar

# Copy OpenCV native library (Linux)
# The Gradle build downloads platform-specific native libraries
# We need to extract and place the .so file correctly
COPY --from=builder /root/.gradle/caches/modules-2/files-2.1/org.weasis.thirdparty.org.opencv/libopencv_java/4.9.0-dcm/*/libopencv_java-4.9.0-dcm-linux-x86-64.so /app/natives/libopencv_java.so 2>/dev/null || true

# Create natives directory and set library path
RUN mkdir -p /app/natives

# Environment variables
ENV SPRING_PROFILES_ACTIVE=docker
ENV JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV JAVA_LIBRARY_PATH=/app/natives

# Expose port
EXPOSE 10201

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:10201/actuator/health || exit 1

# Run application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.library.path=$JAVA_LIBRARY_PATH --add-opens java.desktop/javax.imageio.stream=ALL-UNNAMED --add-opens java.base/java.io=ALL-UNNAMED -jar app.jar"]
