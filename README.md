# version

A modular Scala 3 toolkit for **intent-based versioning** conforming to SemVer 2.0.0.

Cross-platform (JVM, Scala.js, Scala Native) with sbt integration, CLI tooling, and codec support.

## Overview

Traditional versioning tools derive version numbers from repository _history_—counting commits since the last tag,
appending distance suffixes, or inferring from branch patterns. This answers "how far are we from the last release?" but
leaves the _intended next version_ implicit.

`version` inverts this paradigm: developers declare the **target version** through commit message directives, and the
system validates and enforces that intent. Snapshots represent pre-releases of the _upcoming_ version, not post-release
distances from a _previous_ one.

| Paradigm                         | Question Answered             | Snapshot Semantics                          |
|----------------------------------|-------------------------------|---------------------------------------------|
| History-based (e.g., sbt-dynver) | "How far since last release?" | `1.2.3+5-abc1234` (5 commits after 1.2.3)   |
| Intent-based (version)           | "What are we releasing next?" | `1.3.0-snapshot+...` (working toward 1.3.0) |

**Key properties:**

- **Explicit control** — versioning intent declared in commit history
- **Validation** — target directives validated against regression rules
- **Auditability** — version decisions traceable through commits
- **Determinism** — identical repository state always produces identical versions

## Quick Start

### sbt Plugin

> **Note:** Requires sbt 2.x. Not compatible with sbt 1.x.

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "<version>")
```

The plugin automatically derives and sets `version` for all projects.

### Library

```scala
//> using dep "africa.shuwari::version::<version>"

import version.*

// Parse
val v = "2.1.0-rc.1+build.123".toVersion // Either[ParseError, Version]

// Construct
val release = Version(
  MajorVersion.fromUnsafe(1),
  MinorVersion.fromUnsafe(2),
  PatchNumber.fromUnsafe(3)
) // 1.2.3

// Operations
release.next[MinorVersion] // 1.3.0
release.as[Snapshot] // 1.2.3-SNAPSHOT
release.as[Alpha] // 1.2.3-alpha.1
```

### Commit Directives

Embed directives anywhere in commit messages to control versioning:

```text
target: 2.5.0           # Set explicit target
version: major          # Increment major (1.2.3 → 2.0.0)
version: minor          # Increment minor (1.2.3 → 1.3.0)
version: patch          # Increment patch (1.2.3 → 1.2.4)
```

Standalone shorthands (requires non-empty text after colon):

```text
breaking: Remove deprecated API     # Major increment
feat: Add caching support           # Minor increment
fix: Handle edge case               # Patch increment
```

## Modules

| Module                    | Platforms       | Description                                |
|---------------------------|-----------------|--------------------------------------------|
| `sbt-version`             | sbt 2.x         | sbt plugin for build integration           |
| `version-cli`             | JVM, Native     | CLI application                            |
| `version-cli-core`        | JVM, Native     | Git-based version derivation engine        |
| `version`                 | JVM, JS, Native | Core SemVer model, parsing, and operations |
| `version-codecs-jsoniter` | JVM, JS, Native | jsoniter-scala codecs                      |
| `version-codecs-zio`      | JVM, JS, Native | ZIO JSON codecs                            |
| `version-codecs-yaml`     | JVM, JS, Native | scala-yaml codecs                          |
| `version-zio-prelude`     | JVM, JS, Native | ZIO Prelude type class instances           |

## Documentation

See the **[documentation microsite](https://dev.shuwari.africa/version/docs)** for detailed documentation.

- [Getting Started](https://dev.shuwari.africa/version/docs/getting-started)
- [Core Library](https://dev.shuwari.africa/version/docs/core/overview)
- [sbt Plugin](https://dev.shuwari.africa/version/docs/sbt/overview)
- [CLI](https://dev.shuwari.africa/version/docs/resolution/overview)
- [Version Resolution Specification](https://dev.shuwari.africa/version/docs/specification)

## Licence

Apache 2.0
