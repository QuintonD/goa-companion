#!/bin/sh
# goa-gradle for the checkout you are in (a worktree too), one build at a time on this machine:
# parallel hill-climb agents queue on the lock instead of starting builds side by side (oldboi, ADR-038).
# Usage: tools/ui-hillclimb/build.sh <gradle tasks...>
root="$(git rev-parse --show-toplevel)" || exit 1
[ -f "$root/local.properties" ] || cp "$HOME/goa-companion/local.properties" "$root/local.properties" 2>/dev/null
mkdir -p "$HOME/.cache"
# /tmp is a 3.9 GB tmpfs with a per-user quota, and each Robolectric test JVM copies a 205 MB native runtime and
# fonts into java.io.tmpdir and leaves it there; a full record filled the quota ("Disk quota exceeded"). Keep the
# JVMs' temp on disk and drop copies older than a day (a running build's own copies are newer).
tmp="$HOME/.cache/goa-build-tmp"
mkdir -p "$tmp"
find "$tmp" -maxdepth 1 -mindepth 1 -mmin +1440 -exec rm -rf {} + 2>/dev/null
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:+$JAVA_TOOL_OPTIONS }-Djava.io.tmpdir=$tmp"
echo "[build.sh] waiting for the build lock ($root)" >&2
# No daemons left behind: with several worktrees, each kept an idle Gradle and Kotlin daemon (up to ~2 GB each)
# and the next build was OOM-killed. Slower to start, but the memory goes back when the build ends.
exec flock "$HOME/.cache/goa-hillclimb-build.lock" env GOA_DIR="$root" goa-gradle --no-daemon \
  -Pkotlin.compiler.execution.strategy=in-process "$@"
