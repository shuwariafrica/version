---
title: SemVer Behaviour
---

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

| Commit Directive                                    | Effect                                                                 |
|-----------------------------------------------------|------------------------------------------------------------------------|
| `version: major` or `breaking: text`                | Increment major, reset minor and patch                                 |
| `version: minor` or `feat: text` or `feature: text` | Increment minor, reset patch                                           |
| `version: major: 3`                                 | Set major to 3, reset minor and patch                                  |
| `version: minor: 5`                                 | Set minor to 5, reset patch                                            |
| `version: patch: 2`                                 | Set patch to 2                                                         |
| `fix: text` or `patch: text`                        | No effect (patch increment is the default)                             |
| `target: 2.0.0`                                     | Set target version explicitly (subject to [validation](validation.md)) |

See [Commit Directives](directives.md) for the full directive syntax including ignore directives.

## Default Behaviour

When no directives apply:

| Base Version                     | Result                                      |
|----------------------------------|---------------------------------------------|
| Final release (e.g. `1.4.5`)     | Patch + 1 (`1.4.6`)                         |
| Pre-release (e.g. `3.0.0-rc.3`)  | Core unchanged (`3.0.0`)                    |
| No reachable tags, repo has tags | Highest major + 1 (e.g. `4.3.0` -> `5.0.0`) |
| No tags anywhere                 | `0.1.0`                                     |

## Default Development Rendering

The SemVer scheme's `developmentVersion` writes the resolution metadata into the `+` build-metadata section in this
fixed order:

```
<core>-SNAPSHOT+<yyyymmddhhmm>.<branch>.<short-sha>[.pr<N>][.dirty]
```

The 12-character UTC committer timestamp leads so that raw string comparison of two snapshots of the same base sorts
them in commit-time order. The branch slot carries the active branch, or, on PR builds, the target branch where the
merge will land. Branch names are sanitised for the SemVer build-metadata grammar at render time; the raw label remains
available via `DevelopmentMetadata.branch`.

This is the *default* rendering. Whether build metadata appears in the final version string is controlled by
the [Formatter](../schemes/semver/operations.md#rendering); a custom `Formatter` can render the metadata identifiers in
any other shape needed.

**Examples** (using `SemVer.Formatter.full` to display the metadata):

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
