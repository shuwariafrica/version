---
title: SemVer
---

# SemVer Scheme

[Semantic Versioning 2.0.0](https://semver.org/) as the specification defines it, modelled with Scala 3 opaque types.
Ordering follows clause 11 exactly, including its case-sensitive comparison of pre-release identifiers, and parsing
preserves identifiers as written rather than normalising them.

```scala
libraryDependencies += "africa.shuwari" %%% "version" % "@VERSION@"
```

## Import

```scala
import version.semver.*
```

One import brings in the types, the extensions, and every capability instance.

## Types

| Type                   | Purpose                              | Constraint                                |
|------------------------|--------------------------------------|-------------------------------------------|
| `Major`                | Major component                      | `>= 0`, carried as `Long`                 |
| `Minor`                | Minor component                      | `>= 0`, carried as `Long`                 |
| `Patch`                | Patch component                      | `>= 0`, carried as `Long`                 |
| `PreReleaseNumber`     | Number in the construction helpers   | `>= 1`, carried as `Long`                 |
| `PreReleaseClassifier` | Labels recognised by name            | dev, milestone, alpha, beta, rc, SNAPSHOT |
| `PreRelease`           | Dot-separated identifier list        | `[0-9A-Za-z-]+` per identifier            |
| `Metadata`             | Build identifiers                    | `[0-9A-Za-z-]+` per identifier            |
| `SemVer`               | Complete version                     | `MAJOR.MINOR.PATCH[-PRERELEASE][+META]`   |

## Capabilities

SemVer supplies all four: reading and ranking, advancement, release workflow, and two named compatibility rules
(compatibility is never a `given` - see [Version Schemes](../overview.md)).

## Quick Reference

```scala
import version.semver.*

val v = SemVer.parseUnsafe("1.2.3-alpha.1+build.456")

// Projections
v.major.value // 1
v.preRelease  // Some(alpha.1)
v.metadata    // Some(build.456)
v.stable      // false - it carries a pre-release
v.release     // 1.2.3 - pre-release and metadata stripped
v.numbers     // IArray(1, 2, 3)

// Bumping a component
v.next[Major] // 2.0.0
v.next[Minor] // 1.3.0
v.next[Patch] // 1.2.4

// Moving along the pre-release labels
v.next[Alpha] // 1.2.3-alpha.2 - the same label, numbered on
v.next[Beta]  // 1.2.3-beta.1  - a label that outranks it
v.next[Dev]   // 1.2.4-dev.1   - one that does not, so the patch moves first

// Setting a label outright
v.as[Snapshot] // 1.2.3-SNAPSHOT
v.as[Beta]     // 1.2.3-beta.1

// Rendering
v.show                              // "1.2.3-alpha.1+build.456"
SemVer.Formatter.Standard.format(v) // "1.2.3-alpha.1"
```

Builds for the JVM, Scala.js, and Scala Native.

### See Also

- [Types](types.md) - each component type in detail
- [Parsing](parsing.md) - what is accepted, what is rejected, and why
- [Operations](operations.md) - advancement, compatibility, and rendering
