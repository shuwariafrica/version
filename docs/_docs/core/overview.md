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
- **Composable operations** — bump, progress through pre-release stages
- **Robust parsing** — handles common version formats with customisable pre-release mapping

## Package Structure

All types are exported from the `version` package:

```scala
import version.*
```

## Types at a Glance

| Type                             | Purpose                | Constraint                                |
|----------------------------------|------------------------|-------------------------------------------|
| [[version.MajorVersion]]         | Major component        | >= 0                                      |
| [[version.MinorVersion]]         | Minor component        | >= 0                                      |
| [[version.PatchNumber]]          | Patch component        | >= 0                                      |
| [[version.PreReleaseNumber]]     | Pre-release version    | >= 1                                      |
| [[version.PreReleaseClassifier]] | Classifier enum        | dev, milestone, alpha, beta, rc, snapshot |
| [[version.PreRelease]]           | Structured pre-release | Classifier + optional number              |
| [[version.Metadata]]             | Build identifiers      | `[0-9A-Za-z-]+` per identifier            |
| [[version.Version]]              | Complete version       | MAJOR.MINOR.PATCH[-PRERELEASE][+METADATA] |

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
v.stable // true (major > 0 and not snapshot)
v.snapshot // false
v.preRelease.isDefined // true

// Bumping core components
v.next[MajorVersion] // 2.0.0
v.next[MinorVersion] // 1.3.0
v.next[PatchNumber]  // 1.2.4

// Pre-release transitions (precedence-aware)
v.next[Alpha]   // 1.2.3-alpha.2 (same classifier → increment)
v.next[Beta]    // 1.2.3-beta.1  (higher → advance in cycle)
v.next[Dev]     // 1.2.4-dev.1   (lower → new patch cycle)

// Direct pre-release assignment
v.core // 1.2.3
v.as[Snapshot] // 1.2.3-SNAPSHOT
v.as[Beta] // 1.2.3-beta.1
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
