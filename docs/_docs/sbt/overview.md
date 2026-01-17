---
title: sbt Plugin
---

# sbt Plugin

The `sbt-version` plugin integrates Git-based version resolution into sbt builds.

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")
```

## Overview

The plugin automatically:

1. Resolves the current version from Git state
2. Sets `ThisBuild / version`
3. Supports custom rendering via [[version.Version.Show]]

By default, [[version.Version.Show.Standard]] is used, which outputs the core version and pre-release but **excludes build metadata**. Use [[version.Version.Show.Extended]] to include metadata.

No manual version management required.

## Quick Start

The plugin is auto-triggered — just add it to `project/plugins.sbt`:

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")
```

Check the resolved version:

```
> show version
[info] 1.2.3-SNAPSHOT
```

## Behaviour

### At a Release Tag

```bash
git tag -a v1.2.3 -m "Release 1.2.3"
```

```
> show version
[info] 1.2.3
```

### During Development

```
> show version
[info] 1.2.4-SNAPSHOT
```

### With Extended Rendering

To include build metadata (branch, commits, SHA):

```scala
// build.sbt
versionShow := Some(Version.Show.Extended)
```

```
> show version
[info] 1.2.4-SNAPSHOT+branchmain.commits5.shaabc1234
```

## Platform Support

| sbt Version | Status |
|-------------|:------:|
| sbt 2.x     |   ✅    |
| sbt 1.x     |   —    |

## In This Section

- [Installation](installation.md) — setup guide
- [Settings](settings.md) — configuration reference
- [Custom Rendering](rendering.md) — version string customisation
