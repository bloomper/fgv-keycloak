# syntax=docker.io/docker/dockerfile:1

ARG KEYCLOAK_VERSION=26.8

FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS providers

WORKDIR /src

COPY providers ./

RUN ./gradlew --no-daemon -q build

FROM --platform=$BUILDPLATFORM node:22-bookworm AS theme

RUN apt-get update -qq \
    && apt-get install -y -qq --no-install-recommends maven openjdk-17-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src

COPY theme ./

RUN npm ci --no-audit --no-fund --loglevel=error && npm run build-keycloak-theme

FROM quay.io/keycloak/keycloak:${KEYCLOAK_VERSION}

COPY --from=providers --chown=1000:0 /src/build/libs/*.jar /opt/keycloak/providers/
COPY --from=theme --chown=1000:0 /src/dist_keycloak/keycloak-theme-for-kc-all-other-versions.jar /opt/keycloak/providers/
COPY --chown=1000:0 realm/fgv.json /opt/keycloak/data/import/fgv.json
