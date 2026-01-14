---
title: Version Types
---

# Version Types

## Component Types

### MajorVersion

The major version number. Must be non-negative.

```scala
import version.*

// Safe construction
MajorVersion.from(2) // Right(MajorVersion(2))
MajorVersion.from(-1) // Left(InvalidMajorVersion(-1))

// Unsafe construction
val major = MajorVersion.fromUnsafe(2)

// Access and operations
major.value // 2
major.increment // MajorVersion(3)
major.isStable // true (> 0)

// Constants
MajorVersion.minimum // MajorVersion(0)
MajorVersion.reset // MajorVersion(0)
```

### MinorVersion

The minor version number. Must be non-negative.

```scala
val minor = MinorVersion.fromUnsafe(5)
minor.value // 5
minor.increment // MinorVersion(6)
MinorVersion.reset // MinorVersion(0)
```

### PatchNumber

The patch version number. Must be non-negative.

```scala
val patch = PatchNumber.fromUnsafe(3)
patch.value // 3
patch.increment // PatchNumber(4)
PatchNumber.reset // PatchNumber(0)
```

### PreReleaseNumber

The pre-release version number. Must be positive (>= 1).

```scala
PreReleaseNumber.from(1) // Right(PreReleaseNumber(1))
PreReleaseNumber.from(0) // Left(InvalidPreReleaseNumber(0))

val prn = PreReleaseNumber.fromUnsafe(1)
prn.value // 1
prn.increment // PreReleaseNumber(2)
```

## Pre-release Types

### PreReleaseClassifier

An enumeration of constrained classifiers with defined precedence (lowest to highest):

| Classifier         | Aliases                | Requires Number |
|--------------------|------------------------|:---------------:|
| `Dev`              | `dev`                  |        ✅        |
| `Milestone`        | `milestone`, `m`       |        ✅        |
| `Alpha`            | `alpha`, `a`           |        ✅        |
| `Beta`             | `beta`, `b`            |        ✅        |
| `ReleaseCandidate` | `rc`, `cr`             |        ✅        |
| `Snapshot`         | `snapshot`, `SNAPSHOT` |        ❌        |

```scala
import version.PreReleaseClassifier
import version.PreReleaseClassifier.*

// Properties
Alpha.show // "alpha"
Alpha.aliases // List("alpha", "a")
Alpha.versioned // true
Snapshot.versioned // false

// Parsing from alias
PreReleaseClassifier.fromAlias("rc") // Some(ReleaseCandidate)
PreReleaseClassifier.fromAlias("a") // Some(Alpha)
PreReleaseClassifier.fromAlias("foo") // None

// Pattern matching
"beta" match
  case PreReleaseClassifier(c) => c // Beta
```

### PreRelease

Combines a classifier with an optional version number:

```scala
import version.{PreRelease, PreReleaseNumber}

// Factory methods (no validation needed)
PreRelease.snapshot // snapshot
PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)) // alpha.1
PreRelease.beta(PreReleaseNumber.fromUnsafe(2)) // beta.2
PreRelease.releaseCandidate(PreReleaseNumber.fromUnsafe(1)) // rc.1

// Safe construction with validation
PreRelease.from(PreReleaseClassifier.Alpha, Some(PreReleaseNumber.fromUnsafe(1)))
// Right(PreRelease(Alpha, Some(1)))

PreRelease.from(PreReleaseClassifier.Alpha, None)
// Left(MissingPreReleaseNumber(Alpha))

PreRelease.from(PreReleaseClassifier.Snapshot, Some(PreReleaseNumber.fromUnsafe(1)))
// Left(UnexpectedPreReleaseNumber(Snapshot, 1))

// Operations
val pr = PreRelease.alpha(PreReleaseNumber.fromUnsafe(1))
pr.show // "alpha.1"
pr.increment // PreRelease(Alpha, Some(2))
pr.isAlpha // true
```

## BuildMetadata

Build metadata identifiers. Each must match `[0-9A-Za-z-]+`.

```scala
import version.BuildMetadata

// Construction
BuildMetadata.from(List("build", "456"))
// Right(BuildMetadata(List("build", "456")))

BuildMetadata.from(List("")) // Left(InvalidBuildMetadata(...))
BuildMetadata.from(List("a@b")) // Left(InvalidBuildMetadata(...))

// Access
val bm = BuildMetadata(List("sha", "abc123"))
bm.identifiers // List("sha", "abc123")
bm.show // "+sha.abc123"
```

## Version

The complete SemVer 2.0.0 representation:

```scala
import version.*
import version.given

// Construction via apply overloads
Version(major, minor, patch) // Final release
Version(major, minor, patch, preRelease) // With pre-release
Version(major, minor, patch, metadata) // With metadata
Version(major, minor, patch, preRelease, meta) // Full form

// Full example
val v = Version(
  MajorVersion.fromUnsafe(1),
  MinorVersion.fromUnsafe(2),
  PatchNumber.fromUnsafe(3),
  Some(PreRelease.alpha(PreReleaseNumber.fromUnsafe(1))),
  None
)
v.toString // "1.2.3-alpha.1"
```

## Ordering

All types provide `Ordering` instances following SemVer precedence:

```scala
val versions = List(
  Version.parseUnsafe("1.0.0"),
  Version.parseUnsafe("1.0.0-alpha.1"),
  Version.parseUnsafe("1.0.0-beta.1"),
  Version.parseUnsafe("0.9.0")
)

versions.sorted
// List(0.9.0, 1.0.0-alpha.1, 1.0.0-beta.1, 1.0.0)
```

Precedence rules:

1. Major, minor, patch compared numerically
2. Pre-release versions rank below final releases of the same core
3. Pre-releases compared by classifier precedence, then number
4. Build metadata does not affect precedence
