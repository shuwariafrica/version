---
title: sbt Plugin
---

## sbt Plugin

The `sbt-version` plugin integrates Git-based version resolution into sbt builds.

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")
```

### Overview

The plugin automatically:

1. Resolves the current version from Git state
2. Sets `ThisBuild / version`
3. Supports custom rendering via [[version.Version.Show]]

By default, [[version.Version.Show.Standard]] is used, which outputs the core version and pre-release but **excludes build metadata**. Use [[version.Version.Show.Extended]] to include metadata, or provide your own custom instance.

No manual version management required.

### Quick Start

The plugin is auto-triggered — just add it to `project/plugins.sbt`:

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")
```

Check the resolved version:

```
> show version
[info] 1.2.3-SNAPSHOT
```

---

### Behaviour

The plugin shells out to `git` and mirrors SemVer rules from the core module. A clean, annotated tag resolves to that
exact version; otherwise it derives the next logical version with a snapshot classifier and build metadata.

For the complete derivation algorithm, including commit message directives and validation rules, see the [Version Resolution Specification](../specification.md).

#### At a Release Tag

```bash
git tag -a v1.2.3 -m "Release 1.2.3"
```

```
> show version
[info] 1.2.3
```

#### During Development

```
> show version
[info] 1.2.4-SNAPSHOT
```

#### With Extended Rendering

To include build metadata (branch, commits, SHA):

```scala
// build.sbt
versionShow := Some(Version.Show.Extended)
```

```
> show version
[info] 1.2.4-SNAPSHOT+branchmain.commits5.shaabc1234
```

---

### Requirements

- **sbt 2.x** — the plugin only supports sbt 2.x
- **Git repository** — the project must live in a Git repo; detached worktrees are supported via `versionBranchOverride`
- **Git CLI** — `git` must be available on `PATH`

If your CI fetches shallow clones, ensure the relevant tags are present. Detached HEAD builds should supply the branch via `VERSION_BRANCH` or `versionBranchOverride`.

#### See also

- [Settings](settings.md) — configuration reference
- [Core Operations](../core/operations.md) — operations reference, including custom rendering

#### Related

- [Version Resolution Specification](../specification.md) — complete derivation algorithm
