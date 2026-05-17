---
title: sbt Plugin
---

Integrates automatic version derivation into sbt builds.

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")
```

The plugin is auto-triggered. Once added, `ThisBuild / version` is derived from Git state automatically.

## Behaviour

A clean, annotated tag resolves to that exact version; otherwise the plugin produces a snapshot with build metadata.

By default, `version` renders as `v.show` (core plus pre-release, **excludes** build metadata). Configure
[`versionResolver`](settings.md#versionresolver) with a formatter to include it:

```scala
versionResolver := VersionResolver.withDefaults[SemVer]
  .withFormatter(SemVer.Formatter.Full)
```

## At a Release Tag

```bash
git tag -a v1.2.3 -m "Release 1.2.3"
```

```
> show version
[info] 1.2.3
```

## During Development

```
> show version
[info] 1.2.4-SNAPSHOT
```

## With Full Rendering

```scala
versionResolver := VersionResolver.withDefaults[SemVer]
  .withFormatter(SemVer.Formatter.Full)
```

```
> show version
[info] 1.2.4-SNAPSHOT+202605170145.main.0123456789abcdef0123456789abcdef01234567
```

The commit SHA is emitted at full hash length so the rendered string round-trips through `SemVer.parse`. To produce
a shorter form (still well-formed SemVer build metadata) configure `Formatter.Full.withShaLength`:

```scala
versionResolver := VersionResolver.withDefaults[SemVer]
  .withFormatter(SemVer.Formatter.Full.withShaLength(12))
```

```
> show version
[info] 1.2.4-SNAPSHOT+202605170145.main.0123456789ab
```

## Requirements

- **sbt 2.x**
- **Git repository** - detached worktrees supported via `versionBranchOverride`

If your CI fetches shallow clones, ensure relevant tags are present. For detached HEAD builds, supply the branch via
`VERSION_BRANCH` or `versionBranchOverride`.

### See Also

- [Settings](settings.md) - all configuration keys
- [Automatic Versioning](../versioning/overview.md) - how version derivation works
