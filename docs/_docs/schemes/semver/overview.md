---
title: SemVer
---

Type-safe [Semantic Versioning 2.0.0](https://semver.org/) using Scala 3 opaque types.

```scala
libraryDependencies += "africa.shuwari" %%% "version" % "@VERSION@"
```

## Import

```scala
import version.semver.*
```

All SemVer types, extensions, and `given` instances are accessible from this single import.

## Types

| Type | Purpose | Constraint |
|------|---------|------------|
| `Major` | Major component | >= 0 |
| `Minor` | Minor component | >= 0 |
| `Patch` | Patch component | >= 0 |
| `PreReleaseNumber` | Pre-release version | >= 1 |
| `PreReleaseClassifier` | Classifier enum | dev, milestone, alpha, beta, rc, snapshot |
| `PreRelease` | Structured pre-release | Classifier + optional number |
| `Metadata` | Build identifiers | `[0-9A-Za-z-]+` per identifier |
| `SemVer` | Complete version | `MAJOR.MINOR.PATCH[-PRERELEASE][+META]` |

## Quick Reference

```scala
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-alpha.1+build.456")

// Components
v.major.value       // 1
v.preRelease        // Some(PreRelease(Alpha, Some(1)))
v.metadata          // Some(Metadata(List("build", "456")))
v.stable            // true (major > 0 and not snapshot)

// Bumping
v.next[Major]       // 2.0.0
v.next[Minor]       // 1.3.0
v.next[Patch]       // 1.2.4

// Pre-release transitions
v.next[Alpha]       // 1.2.3-alpha.2 (same - increment)
v.next[Beta]        // 1.2.3-beta.1  (higher - advance)
v.next[Dev]         // 1.2.4-dev.1   (lower - new patch cycle)

// Direct assignment
v.core              // 1.2.3
v.as[Snapshot]      // 1.2.3-SNAPSHOT
v.as[Beta]          // 1.2.3-beta.1

// Rendering
v.show                              // "1.2.3-alpha.1"
SemVer.Formatter.extended.format(v) // "1.2.3-alpha.1+build.456"
```

Supports JVM, Scala.js, and Scala Native.

### See Also

- [Types](types.md) - detailed type documentation
- [Parsing](parsing.md) - parsing versions from strings
- [Operations](operations.md) - version manipulation and rendering
