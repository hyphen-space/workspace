# syntax=docker/dockerfile:1

ARG NODE_VERSION=24-bookworm-slim
ARG GRADLE_VERSION=jdk25-noble

FROM docker.io/library/node:${NODE_VERSION} AS node

FROM docker.io/library/gradle:${GRADLE_VERSION}

USER root

# Copy Node.js from its official image so the Codex CLI can run without adding
# a third-party package repository to the development image.
COPY --from=node /usr/local/ /usr/local/

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        bash-completion \
        build-essential \
        ca-certificates \
        curl \
        git \
        jq \
        less \
        openssh-client \
        procps \
        ripgrep \
        unzip \
        zip \
    && npm install --global @openai/codex \
    && npm cache clean --force \
    && rm -rf /var/lib/apt/lists/*

ENV CODEX_HOME=/home/gradle/.codex \
    GRADLE_USER_HOME=/home/gradle/.gradle \
    JAVA_HOME=/opt/java/openjdk

RUN mkdir -p /workspace "${CODEX_HOME}" "${GRADLE_USER_HOME}" \
    && chown -R gradle:gradle /workspace /home/gradle

WORKDIR /workspace

USER gradle

EXPOSE 8080

CMD ["sleep", "infinity"]
