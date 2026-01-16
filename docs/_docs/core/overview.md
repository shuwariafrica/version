---
title: Core Library
---

# Core Library

The `version` module provides a type-safe SemVer 2.0.0 model using Scala 3 opaque types.

```scala
libraryDependencies += "africa.shuwari" %% /* or `%%%` */ "version" % "@VERSION@"
```

## Overview

The core library offers:

- **Opaque types** for version components — zero-cost abstractions with validation
- **Constrained pre-release classifiers** — a defined hierarchy with precedence ordering
- **Composable operations** — bump, release, advance through pre-release stages
- **Robust parsing** — handles common version formats with customisable pre-release mapping

## Package Structure

All types are exported from the `version` package:

```scala
import version.*
```

## Types at a Glance

| Type                   | Purpose                | Constraint                                |
|------------------------|------------------------|-------------------------------------------|
| `MajorVersion`         | Major component        | >= 0                                      |
| `MinorVersion`         | Minor component        | >= 0                                      |
| `PatchNumber`          | Patch component        | >= 0                                      |
| `PreReleaseNumber`     | Pre-release version    | >= 1                                      |
| `PreReleaseClassifier` | Classifier enum        | dev, milestone, alpha, beta, rc, snapshot |
| `PreRelease`           | Structured pre-release | Classifier + optional number              |
| `Metadata`        | Build identifiers      | `[0-9A-Za-z-]+` per identifier            |
| `Version`              | Complete version       | MAJOR.MINOR.PATCH[-PRERELEASE][+METADATA] |

## Quick Reference

```scala
import version.*

// Construction
val v = "1.2.3-alpha.1+build.456".toVersionUnsafe

// Access
v.major.value // 1
v.preRelease // Some(PreRelease(Alpha, Some(1)))
v.metadata // Some(Metadata(List("build", "456")))

// Status
v.isPreRelease // true
v.isFinal // false
v.isStable // true (major > 0)
v.isSnapshot // false

// Bumping
v.nextMajor // 2.0.0
v.nextMinor // 1.3.0
v.nextPatch // 1.2.4

// Pre-release transitions
v.release // 1.2.3
v.toSnapshot // 1.2.3-SNAPSHOT
v.advance[PreReleaseClassifier.Beta.type] // Right(1.2.3-beta.1)
```

## Platform Support

| Platform                             | Status |
|--------------------------------------|:------:|
| JVM (JDK @JDK_VERSION@+)             |   ✅    |
| Scala.js (@SCALAJS_VERSION@)         |   ✅    |
| Scala Native (@SCALANATIVE_VERSION@) |   ✅    |

## In This Section

- [Version Types](types.md) — detailed type documentation
- [Parsing](parsing.md) — parsing versions from strings
- [Operations](operations.md) — version manipulation
