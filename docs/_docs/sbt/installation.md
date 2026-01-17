---
title: Installation
---

# Installation

## Add the Plugin

In `project/plugins.sbt`:

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "@VERSION@")
```

## Verify

```
> show version
[info] 0.1.0-SNAPSHOT+branchmain.commits0.shaabc1234
```

## Create a Release

Tag your commit:

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
```

```
> show version
[info] 1.0.0
```

## Requirements

- **sbt 2.x** — the plugin uses sbt 2.x APIs
- **Git repository** — `.git` directory must exist
- **Git CLI** — available in PATH

## Annotated Tags

The plugin only recognises **annotated** tags:

```bash
# Correct: annotated tag
git tag -a v1.0.0 -m "Release 1.0.0"

# Ignored: lightweight tag
git tag v1.0.0
```
