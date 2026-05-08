# Run sbt directly, or inside a Docker image when $env:DOCKER_IMAGE is set.
#
# Mirror of run-sbt.sh for Windows developers. CI does not exercise this
# script. SBT_PROPS, when set, is split on whitespace and prepended to
# the sbt argv. sbt 2.x resolution is coursier-based, so only the
# coursier cache is mounted.
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
    '-w', "$PWD"
)
# On Linux Docker, pin container UID/GID to the host user so files written
# back via the bind mount inherit caller ownership rather than root. On
# Windows hosts (Docker Desktop), --user is unnecessary — Docker maps
# Windows ACLs differently and forcing a numeric UID would mismatch.
if ($IsLinux -or $IsMacOS) {
    $uid = & id -u
    $gid = & id -g
    $dockerArgs += @('--user', "${uid}:${gid}")
}

foreach ($name in 'GITHUB_OUTPUT', 'GITHUB_PATH', 'GITHUB_ENV', 'GITHUB_STEP_SUMMARY') {
    $val = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrEmpty($val)) {
        $dockerArgs += @('-v', "${val}:${val}", '-e', $name)
    }
}

foreach ($name in 'HOME', 'TERM', 'CI', 'GITHUB_TOKEN', 'GITHUB_REPOSITORY', 'GITHUB_REF', 'GITHUB_REF_NAME', 'GITHUB_SHA', 'GITHUB_ACTIONS', 'GITHUB_WORKSPACE', 'SBT_OPTS', 'SBT_PROPS', 'COURSIER_CACHE') {
    $val = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrEmpty($val)) {
        $dockerArgs += @('-e', $name)
    }
}

& docker run @dockerArgs $env:DOCKER_IMAGE sbt @extraArgs @args
exit $LASTEXITCODE
