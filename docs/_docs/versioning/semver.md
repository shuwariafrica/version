---
title: SemVer Derivation
---

# SemVer Derivation

How SemVer versions are derived by the automatic versioning engine.

## Tag Recognition

Tags are recognised if they are **annotated** and parse as valid SemVer (optional `v`/`V` prefix). Lightweight tags are
ignored.

Pre-release classifiers are recognised case-insensitively:

| Classifier        | Aliases          | Example Tag       |
|-------------------|------------------|-------------------|
| Development       | `dev`            | `v1.0.0-dev.1`    |
| Milestone         | `milestone`, `m` | `v1.0.0-m.1`      |
| Alpha             | `alpha`, `a`     | `v1.0.0-alpha.1`  |
| Beta              | `beta`, `b`      | `v1.0.0-beta.1`   |
| Release Candidate | `rc`, `cr`       | `v1.0.0-rc.1`     |
| Snapshot          | `SNAPSHOT`       | `v1.0.0-SNAPSHOT` |

When multiple tags exist on one commit, a final release outranks a pre-release of the same core. Otherwise, the highest
version wins.

## Keyword Mapping

SemVer reads two kinds of word. One names a kind of change and leaves the choice of component to the scheme; the other
names a component and is obeyed literally.

| Keyword             | Stands for           | Above `1.0.0`, from `1.4.5`      |
|---------------------|----------------------|-----------------------------------|
| `breaking`          | A breaking change    | `2.0.0`                           |
| `feat`, `feature`   | A feature            | `1.5.0`                           |
| `fix`               | A fix                | `1.4.6`                           |
| `stable`            | The first stable release | `1.4.5` - already there       |
| `major`             | The major component  | `2.0.0`                           |
| `minor`             | The minor component  | `1.5.0`                           |
| `patch`             | The patch component  | `1.4.6`                           |

Each works in every [directive form](directives.md) - `version: breaking`, `breaking: <text>`, `[breaking]` - and the
three component names additionally take a value, as `version: minor: 9`.

Advancing a component resets every component below it, and discards any pre-release and build metadata the base
carried.

## Initial Development (Major Version 0)

Below `1.0.0` the public API is not yet settled, so a kind of change means one component less than it would above it: a
breaking change advances the minor, and a feature or fix advances the patch. Below `0.1.0`, where nothing at all is
settled, every kind of change advances the patch.

A component named outright is exempt: `version: major` means the major, at any base. So is `stable`, which is how a
project graduates when it decides it has, rather than when a change happens to be breaking:
`stable: the public API is settled`.

| Base     | Directive           | Result   |
|----------|---------------------|----------|
| `0.93.9` | `version: breaking` | `0.94.0` |
| `0.93.9` | `version: feat`     | `0.93.10`|
| `0.0.7`  | `version: breaking` | `0.0.8`  |
| `0.93.9` | `version: major`    | `1.0.0`  |
| `0.93.9` | `version: stable`   | `1.0.0`  |
| `0.93.9` | `version: major: 1` | `1.0.0`  |
| `1.4.5`  | `version: breaking` | `2.0.0`  |

## Releasing a Pending Pre-release

Where the base is a pre-release, a request whose boundary that pre-release already sits on is satisfied by releasing
it rather than by advancing past it: from `1.3.0-rc.1`, a feature yields `1.3.0`, not `1.4.0`. A request that reaches
beyond it still advances - from `1.3.0-rc.1`, a breaking change yields `2.0.0`. Setting a component with a value is
never absorbed this way.

## Default Behaviour

When no directives apply:

| Base Version                     | Result                                      |
|----------------------------------|---------------------------------------------|
| Final release (e.g. `1.4.5`)     | Patch + 1 (`1.4.6`)                         |
| Pre-release (e.g. `3.0.0-rc.3`)  | Core unchanged (`3.0.0`)                    |
| No reachable tags, repo has tags | Highest tag + a breaking bump (`4.3.0` -> `5.0.0`; pre-1.0 `0.5.0` -> `0.6.0`) |
| No tags anywhere                 | `0.1.0`                                     |

## Default Development Rendering

The SemVer scheme's `developmentVersion` writes the resolution metadata into the `+` build-metadata section in this
fixed order:

```
<core>-SNAPSHOT+<yyyymmddhhmm>.<branch>.<sha>[.pr<N>][.dirty]
```

The 12-character UTC committer timestamp leads so that raw string comparison of two snapshots of the same base sorts
them in commit-time order. The branch slot carries the active branch, or, on PR builds, the target branch where the
merge will land. Branch names are sanitised for the SemVer build-metadata grammar at render time; the raw label remains
available via `DevelopmentMetadata.branch`. The SHA is the basis commit's full hash; `SemVer.Formatter.Full.withShaLength(n)`
truncates it for display.

This is the *default* rendering. Whether build metadata appears in the final version string is controlled by
the [Formatter](../schemes/semver/operations.md#rendering); a custom `Formatter` can render the metadata identifiers in
any other shape needed.

**Examples** (rendered with `SemVer.Formatter.Full.withShaLength(12)` to keep the SHA readable):

| Scenario                              | Output                                                         |
|---------------------------------------|----------------------------------------------------------------|
| Clean tag `v2.3.1`                    | `2.3.1`                                                        |
| After `1.4.5`, no directives          | `1.4.6-SNAPSHOT+202605170145.main.1234567890ab`                |
| After `1.0.0`, `breaking: API change` | `2.0.0-SNAPSHOT+202605170145.main.1234567890ab`                |
| Dirty worktree at `v1.0.0`            | `1.0.1-SNAPSHOT+202605170145.main.1234567890ab.dirty`          |
| No tags, fresh repo                   | `0.1.0-SNAPSHOT+202605170145.main.1234567890ab`                |
| PR build (PR 42 targeting `main`)     | `1.2.4-SNAPSHOT+202605170145.main.1234567890ab.pr42`           |
| PR build on `release/v2.0.x` branch   | `1.2.4-SNAPSHOT+202605170145.release-v2-0-x.1234567890ab.pr42` |

## Custom Tag Parsing

By default, tags are parsed by stripping a `v`/`V` prefix and calling `SemVer.parse`. Override `tagParser` in
`ResolutionConfig` for non-standard tag formats:

```scala
val config = ResolutionConfig.default[SemVer]("/path/to/repo").copy(
  tagParser = name =>
    val stripped = name.stripPrefix("release-")
    val raw = if stripped.startsWith("v") then stripped.drop(1) else stripped
    SemVer.parse(raw).toOption
)
```
