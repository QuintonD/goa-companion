# The game server (ADR-056, docs/ONLINE.md). Runtime only: deploy.sh builds the distribution on the
# host under goa-gradle and stages it in dist/, because a compile inside BuildKit would run outside
# the memory cap (ADR-038). Never add Gradle here.
FROM eclipse-temurin:21-jre

# Baked in, not set by compose, so goa-game:previous keeps its own value through a rollback.
ARG GOA_GAME_RULES_BUILD
RUN test -n "$GOA_GAME_RULES_BUILD" || { echo "GOA_GAME_RULES_BUILD is empty: build through deploy.sh" >&2; exit 1; } \
    && groupadd --system --gid 10001 goa \
    && useradd --system --uid 10001 --gid goa --no-create-home --home-dir /nonexistent --shell /usr/sbin/nologin goa \
    && mkdir -p /var/lib/goa-game \
    && chown goa:goa /var/lib/goa-game \
    && chmod 0700 /var/lib/goa-game
COPY dist/ /opt/goa-game/
ENV GOA_GAME_RULES_BUILD=$GOA_GAME_RULES_BUILD
USER goa:goa
EXPOSE 8788
ENTRYPOINT ["/opt/goa-game/bin/goa-game"]
