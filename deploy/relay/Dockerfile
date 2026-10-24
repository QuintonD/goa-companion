# syntax=docker/dockerfile:1
# The group sync relay (ADR-036, docs/SYNC-SERVER.md). Build context: server/relay (see compose.yaml).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Wrapper and build scripts first so the Gradle distribution and plugins stay in the layer cache.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon --console=plain --quiet help
COPY src ./src
# The unit tests are the image's gate: a red test means no image.
RUN --mount=type=cache,target=/root/.gradle \
    sh ./gradlew --no-daemon --console=plain test installDist

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 goa \
    && useradd --system --uid 10001 --gid goa --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin goa \
    && mkdir -p /var/lib/goa-relay \
    && chown goa:goa /var/lib/goa-relay \
    && chmod 0700 /var/lib/goa-relay
COPY --from=build /src/build/install/goa-relay /opt/goa-relay
ENV GOA_RELAY_HOST=0.0.0.0 \
    GOA_RELAY_PORT=8787 \
    GOA_RELAY_DATA=/var/lib/goa-relay \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:-UsePerfData -Djava.io.tmpdir=/tmp"
USER goa:goa
EXPOSE 8787
ENTRYPOINT ["/opt/goa-relay/bin/goa-relay"]
