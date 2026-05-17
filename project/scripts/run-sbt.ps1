# Mirror of run-sbt.sh for Windows / PowerShell developers. CI does not
# exercise this script.
#
# SBT_PROPS, when set, is split on whitespace and prepended to the sbt
# argv (intended for matrix-driven `-D...=...` flags).
#
# Two host caches are mounted at the same path inside the container -
# both are content-addressed and safe to share between host and container:
#   - ~/.cache/coursier: coursier's resolved-artefact cache
#   - ~/.cache/sbt:      sbt 2.x's content-addressed compile cache (cas/)
# Sharing the cas store keeps `target/` symlinks valid across host and
# container invocations. ~/.sbt (the launcher's boot/locks dir) is
# intentionally NOT shared; the container creates its own.
#
# On Linux/macOS the container runs with --user matching the caller's
# UID/GID and HOME redirected to a per-UID /tmp directory so files
# written through the bind mount inherit caller ownership and the sbt
# launcher can create ~/.sbt/boot/ without bumping into the overlay
# rootfs. On Windows hosts (Docker Desktop) --user is omitted because
# Docker Desktop maps Windows ACLs differently and a numeric UID would
# mismatch.
$ErrorActionPreference = 'Stop'

$extraArgs = @()
if (-not [string]::IsNullOrEmpty($env:SBT_PROPS)) {
    $extraArgs = $env:SBT_PROPS -split '\s+' | Where-Object { $_ -ne '' }
}

if ([string]::IsNullOrEmpty($env:DOCKER_IMAGE)) {
    & sbt @extraArgs @args
    exit $LASTEXITCODE
}

$coursierCache = Join-Path $HOME '.cache/coursier'
$sbtCache = Join-Path $HOME '.cache/sbt'
New-Item -ItemType Directory -Force -Path $coursierCache | Out-Null
New-Item -ItemType Directory -Force -Path $sbtCache | Out-Null

$dockerArgs = @(
    '--rm',
    '-v', "${PWD}:${PWD}",
    '-v', "${coursierCache}:${coursierCache}",
    '-v', "${sbtCache}:${sbtCache}",
    '-w', "$PWD",
    '-e', "COURSIER_CACHE=${coursierCache}",
    '-e', "SBT_LOCAL_CACHE=${sbtCache}"
)

if ($IsLinux -or $IsMacOS) {
    $uid = (& id -u).Trim()
    $gid = (& id -g).Trim()
    $containerHome = "/tmp/sbt-build-${uid}"
    $dockerArgs += @(
        '--user', "${uid}:${gid}",
        '-e', "HOME=${containerHome}"
    )
} else {
    # Windows / Docker Desktop: keep the host HOME so coursier and the sbt cas
    # mounts resolve to the same path inside the container as outside.
    $dockerArgs += @('-e', "HOME=${HOME}")
}

foreach ($name in 'GITHUB_OUTPUT', 'GITHUB_PATH', 'GITHUB_ENV', 'GITHUB_STEP_SUMMARY') {
    $val = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrEmpty($val)) {
        $dockerArgs += @('-v', "${val}:${val}", '-e', $name)
    }
}

foreach ($name in 'TERM', 'CI', 'GITHUB_TOKEN', 'GITHUB_REPOSITORY', 'GITHUB_REF', 'GITHUB_REF_NAME', 'GITHUB_SHA', 'GITHUB_ACTIONS', 'GITHUB_WORKSPACE', 'SBT_OPTS') {
    $val = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrEmpty($val)) {
        $dockerArgs += @('-e', $name)
    }
}

if ($IsLinux -or $IsMacOS) {
    # Materialise the per-UID /tmp HOME inside the container before sbt runs.
    & docker run @dockerArgs --entrypoint sh $env:DOCKER_IMAGE -c `
        'mkdir -p "$HOME" && exec sbt "$@"' sh @extraArgs @args
} else {
    & docker run @dockerArgs $env:DOCKER_IMAGE sbt @extraArgs @args
}
exit $LASTEXITCODE
