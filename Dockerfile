# syntax=docker/dockerfile:1

# ---------------------------------------------------------------
# Build stage: compile and assemble the application distribution
# ---------------------------------------------------------------
FROM gradle:8.5-jdk21 AS build

WORKDIR /home/gradle/project

# Copy the build definition first so dependency resolution can be cached.
COPY --chown=gradle:gradle settings.gradle build.gradle ./
COPY --chown=gradle:gradle gradle ./gradle
COPY --chown=gradle:gradle gradlew gradlew.bat ./
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    gradle --no-daemon --console=plain dependencies > /dev/null || true

# Copy the sources and build the installable distribution (tests run in CI, not here).
COPY --chown=gradle:gradle src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle,uid=1000,gid=1000 \
    gradle --no-daemon --console=plain clean installDist -x test

# ---------------------------------------------------------------
# Runtime stage: slim JRE with only the application distribution
# ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

LABEL org.opencontainers.image.title="ChaosBridge" \
      org.opencontainers.image.description="A chaos testing tool for building resilient systems" \
      org.opencontainers.image.source="https://github.com/sahariardev/ChaosBridge" \
      org.opencontainers.image.url="https://sahariardev.github.io/ChaosBridge/" \
      org.opencontainers.image.licenses="MIT"

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75" \
    MICRONAUT_SERVER_PORT=9091

# Run as an unprivileged user.
RUN groupadd --system chaos \
    && useradd --system --gid chaos --home-dir /app --shell /usr/sbin/nologin chaos

WORKDIR /app
COPY --from=build --chown=chaos:chaos /home/gradle/project/build/install/ChaosBridge/ /app/
RUN mkdir -p /app/logs && chown -R chaos:chaos /app

USER chaos

EXPOSE 9091

# The generated launcher exec's the JVM, so SIGTERM reaches the process for a graceful shutdown.
ENTRYPOINT ["/app/bin/ChaosBridge"]
