# version

A modular Scala 3 **versioning toolkit** - version types, parsing, manipulation, automatic derivation from Git, and
build integration.

Cross-platform (JVM, Scala Native) with sbt integration and a CLI binary. Scala.js axis is deferred pending upstream sbt
2.x support.

## Overview

Traditional versioning tools derive version numbers from repository _history_ - counting commits since the last tag,
appending distance suffixes, or inferring from branch patterns. This answers "how far are we from the last release?" but
leaves the _intended next version_ implicit.

`version` inverts this paradigm: developers declare the **target version** through commit message directives, and the
system validates and enforces that intent. Snapshots represent pre-releases of the _upcoming_ version, not post-release
distances from a _previous_ one.

| Paradigm                         | Question Answered             | Snapshot Semantics                          |
|----------------------------------|-------------------------------|---------------------------------------------|
| History-based (e.g., sbt-dynver) | "How far since last release?" | `1.2.3+5-abc1234` (5 commits after 1.2.3)   |
| Intent-based (version)           | "What are we releasing next?" | `1.3.0-SNAPSHOT+...` (working toward 1.3.0) |

## Quick Start

### sbt Plugin

> **Note:** Requires sbt 2.x. Not compatible with sbt 1.x.

Add to `project/plugins.sbt`:

```scala
addSbtPlugin("africa.shuwari" % "sbt-version" % "0.7.0")
```

The plugin automatically derives and sets `version` for all projects.

### Library

```scala
//> using dep "africa.shuwari::version::0.7.0"

import version.semver.*

// Parse
val v = SemVer.parse("2.1.0-rc.1+abc1234.123") // Either[ParseError, SemVer]

// Construct
val release = SemVer(Major(1), Minor(2), Patch(3)) // 1.2.3

// Operations
release.next[Minor] // 1.3.0
release.as[Snapshot] // 1.2.3-SNAPSHOT
release.as[Alpha] // 1.2.3-alpha.1
```

### Commit Directives

Embed directives anywhere in commit messages to control versioning:

```text
target: 2.5.0           # Set explicit target
version: major          # Increment major (1.2.3 -> 2.0.0)
version: minor          # Increment minor (1.2.3 -> 1.3.0)
version: patch          # Increment patch (1.2.3 -> 1.2.4)
```

Standalone shorthands (requires non-empty text after colon):

```text
breaking: Remove deprecated API     # Major increment
feat: Add caching support           # Minor increment
fix: Handle edge case               # Patch increment
```

## Modules

| Module               | Platforms   | Description                             |
|----------------------|-------------|-----------------------------------------|
| `version`            | JVM, Native | Version model, parsing, operations      |
| `version-resolution` | JVM, Native | Git-based version derivation            |
| `version-cli`        | Native      | CLI binary (shipped on GitHub Releases) |
| `sbt-version`        | sbt 2.x     | Build integration                       |

## Licence

Apache 2.0
