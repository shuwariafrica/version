#!/usr/bin/env bash
# Run sbt directly, or inside a Docker image when DOCKER_IMAGE is set.
#
# Single entry point so the host-vs-container choice lives in one place.
# Required because the Alpine arm64 image cannot be expressed as a
# `container:` (actions/runner#801) and we want one model rather than two.
#
# SBT_PROPS, when set, is split on whitespace and prepended to the sbt
# argv (intended for matrix-driven `-D...=...` flags).
#
# Two host caches are mounted at the same path inside the container —
# both are content-addressed and safe to share between host and container:
#   - ~/.cache/coursier: coursier's resolved-artefact cache
#   - ~/.cache/sbt:      sbt 2.x's content-addressed compile cache (cas/)
# Sharing the cas store is what keeps `target/` symlinks valid across
# host and container invocations. Note: ~/.sbt (the launcher's boot/locks
# dir) is intentionally NOT shared; the container creates its own.
#
# Containers run with --user matching the caller's UID/GID so files
# written back via the bind mount inherit caller ownership rather than
# root. HOME is redirected to a per-UID /tmp directory so sbt's launcher
# can create ~/.sbt/boot/ etc. without bumping into the overlay rootfs.
set -euo pipefail

extra_args=()
if [[ -n "${SBT_PROPS:-}" ]]; then
  read -ra extra_args <<< "$SBT_PROPS"
fi

if [[ -z "${DOCKER_IMAGE:-}" ]]; then
  exec sbt "${extra_args[@]}" "$@"
fi

mkdir -p "$HOME/.cache/coursier" "$HOME/.cache/sbt"
container_home="/tmp/sbt-build-$(id -u)"
docker_args=(
  --rm
  --user "$(id -u):$(id -g)"
  -v "$PWD:$PWD"
  -v "$HOME/.cache/coursier:$HOME/.cache/coursier"
  -v "$HOME/.cache/sbt:$HOME/.cache/sbt"
  -w "$PWD"
  -e "HOME=$container_home"
  -e "COURSIER_CACHE=$HOME/.cache/coursier"
  -e "SBT_LOCAL_CACHE=$HOME/.cache/sbt"
)

for env_file in GITHUB_OUTPUT GITHUB_PATH GITHUB_ENV GITHUB_STEP_SUMMARY; do
  if [[ -n "${!env_file:-}" ]]; then
    docker_args+=(-v "${!env_file}:${!env_file}" -e "$env_file")
  fi
done

for env_var in TERM CI GITHUB_TOKEN GITHUB_REPOSITORY GITHUB_REF GITHUB_REF_NAME GITHUB_SHA GITHUB_ACTIONS GITHUB_WORKSPACE SBT_OPTS SBT_PROPS; do
  if [[ -n "${!env_var:-}" ]]; then
    docker_args+=(-e "$env_var")
  fi
done

# Materialise the per-UID /tmp HOME inside the container before sbt runs.
# /tmp is world-writable (sticky) so the unprivileged user can mkdir there.
exec docker run "${docker_args[@]}" --entrypoint sh "$DOCKER_IMAGE" -c \
  "mkdir -p \"\$HOME\" && exec sbt \"\$@\"" sh "${extra_args[@]}" "$@"
