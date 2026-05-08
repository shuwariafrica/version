#!/usr/bin/env bash
# Run sbt directly, or inside a Docker image when DOCKER_IMAGE is set.
#
# Single entry point so the host-vs-container choice lives in one place.
# Required because the Alpine arm64 image cannot be expressed as a
# `container:` (actions/runner#801) and we want one model rather than two.
#
# SBT_PROPS, when set, is split on whitespace and prepended to the sbt
# argv (intended for matrix-driven `-D...=...` flags). sbt 2.x resolution
# is coursier-based, so only the coursier cache is mounted.
set -euo pipefail

extra_args=()
if [[ -n "${SBT_PROPS:-}" ]]; then
  read -ra extra_args <<< "$SBT_PROPS"
fi

if [[ -z "${DOCKER_IMAGE:-}" ]]; then
  exec sbt "${extra_args[@]}" "$@"
fi

mkdir -p "$HOME/.cache/coursier"
docker_args=(
  --rm
  -v "$PWD:$PWD"
  -v "$HOME/.cache/coursier:$HOME/.cache/coursier"
  -w "$PWD"
)

for env_file in GITHUB_OUTPUT GITHUB_PATH GITHUB_ENV GITHUB_STEP_SUMMARY; do
  if [[ -n "${!env_file:-}" ]]; then
    docker_args+=(-v "${!env_file}:${!env_file}" -e "$env_file")
  fi
done

for env_var in HOME TERM CI GITHUB_TOKEN GITHUB_REPOSITORY GITHUB_REF GITHUB_REF_NAME GITHUB_SHA GITHUB_ACTIONS GITHUB_WORKSPACE SBT_OPTS SBT_PROPS COURSIER_CACHE; do
  if [[ -n "${!env_var:-}" ]]; then
    docker_args+=(-e "$env_var")
  fi
done

exec docker run "${docker_args[@]}" "$DOCKER_IMAGE" sbt "${extra_args[@]}" "$@"
