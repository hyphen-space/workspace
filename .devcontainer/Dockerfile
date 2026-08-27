# syntax=docker/dockerfile:1

ARG NODE_VERSION=24-bookworm-slim
ARG HUGO_VERSION=0.164.0

FROM docker.io/library/node:${NODE_VERSION}

ARG HUGO_VERSION
ARG TARGETARCH

USER root

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
    && curl -fL -o /tmp/hugo.tar.gz \
        "https://github.com/gohugoio/hugo/releases/download/v${HUGO_VERSION}/hugo_${HUGO_VERSION}_linux-${TARGETARCH}.tar.gz" \
    && tar -xzf /tmp/hugo.tar.gz -C /usr/local/bin hugo \
    && npm install --global @openai/codex \
    && npm cache clean --force \
    && rm /tmp/hugo.tar.gz \
    && rm -rf /var/lib/apt/lists/*

ENV CODEX_HOME=/home/node/.codex

RUN mkdir -p /workspace "${CODEX_HOME}" \
    && chown -R node:node /workspace /home/node

WORKDIR /workspace

USER node

EXPOSE 8080

CMD ["sleep", "infinity"]
