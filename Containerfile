# syntax=docker/dockerfile:1

ARG GRADLE_IMAGE=docker.io/library/gradle:9.7.1-jdk25-noble
ARG RUNTIME_IMAGE=docker.io/library/eclipse-temurin:25-jre-noble

FROM ${GRADLE_IMAGE} AS build

WORKDIR /workspace
USER gradle

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts gradle.properties ./

RUN gradle --no-daemon --quiet dependencies --configuration runtimeClasspath

COPY --chown=gradle:gradle src ./src

RUN gradle --no-daemon installDist

FROM ${RUNTIME_IMAGE}

ARG APP_UID=10001
ARG APP_GID=10001

RUN groupadd --gid "${APP_GID}" wgkeys \
    && useradd --uid "${APP_UID}" --gid wgkeys --no-create-home --shell /usr/sbin/nologin wgkeys \
    && mkdir -p /app /data \
    && chown wgkeys:wgkeys /app /data

WORKDIR /app

COPY --from=build --chown=wgkeys:wgkeys /workspace/build/install/keyexchange-kotlin/ /app/

ENV PORT=8080 \
    DATABASE_PATH=/data/wgkeys.db

USER wgkeys:wgkeys

VOLUME ["/data"]
EXPOSE 8080

ENTRYPOINT ["/app/bin/keyexchange-kotlin"]
