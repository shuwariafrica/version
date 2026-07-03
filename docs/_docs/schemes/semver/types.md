---
title: SemVer Components
---

# SemVer Component Types

The `SemVer` scheme composes these component types.

---

## Core Version

### `Major`

The major version number. Must be non-negative.

```scala
import version.semver.*

// Literal construction, checked at compile time (a negative literal fails to compile)
val major = Major(2)

// Runtime value, validated: Either[InvalidComponent, Major]
Major.from(2)  // Right(Major(2))
Major.from(-1) // Left(InvalidComponent(-1, "Major version", "a non-negative number (>= 0)"))

// Runtime value, unchecked (throws on an invalid value)
Major.fromUnsafe(2)

major.value     // 2
major.increment // Major(3)
major.isStable  // true (> 0)
Major.reset     // Major(0)
```

### `Minor`

The minor version number. Must be non-negative.

```scala
val minor = Minor(5)
minor.value     // 5
minor.increment // Minor(6)
Minor.reset     // Minor(0)
```

### `Patch`

The patch version number. Must be non-negative.

```scala
val patch = Patch(3)
patch.value     // 3
patch.increment // Patch(4)
Patch.reset     // Patch(0)
```

### `PreReleaseNumber`

The pre-release version number. Must be positive (>= 1).

```scala
val prn = PreReleaseNumber(1)
PreReleaseNumber.from(0) // Left(InvalidComponent(0, "Pre-release number", "a positive number (>= 1)"))
prn.value     // 1
prn.increment // PreReleaseNumber(2)
```

---

## Pre-release Types

### `PreReleaseClassifier`

An enumeration of constrained classifiers with defined precedence (lowest to highest):

| Classifier         | Aliases                | Requires Number |
|--------------------|------------------------|-----------------|
| `Dev`              | `dev`                  | Yes             |
| `Milestone`        | `milestone`, `m`       | Yes             |
| `Alpha`            | `alpha`, `a`           | Yes             |
| `Beta`             | `beta`, `b`            | Yes             |
| `ReleaseCandidate` | `rc`, `cr`             | Yes             |
| `Snapshot`         | `SNAPSHOT`             | No              |

```scala
import version.semver.PreReleaseClassifier
import version.semver.PreReleaseClassifier.*

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

### `PreRelease`

Combines a classifier with an optional version number:

```scala
import version.semver.{PreRelease, PreReleaseNumber}

// Factory methods (no validation needed)
PreRelease.snapshot // SNAPSHOT
PreRelease.alpha(PreReleaseNumber(1)) // alpha.1
PreRelease.beta(PreReleaseNumber(2)) // beta.2
PreRelease.releaseCandidate(PreReleaseNumber(1)) // rc.1

// Safe construction with validation
PreRelease.from(PreReleaseClassifier.Alpha, Some(PreReleaseNumber(1)))
// Right(PreRelease(Alpha, Some(1)))

PreRelease.from(PreReleaseClassifier.Alpha, None)
// Left(MissingQualifierNumber("alpha"))

PreRelease.from(PreReleaseClassifier.Snapshot, Some(PreReleaseNumber(1)))
// Left(UnexpectedQualifierNumber("SNAPSHOT", 1))

// Operations
val pr = PreRelease.alpha(PreReleaseNumber(1))
pr.show // "alpha.1"
pr.increment // PreRelease(Alpha, Some(2))
pr.isAlpha // true
```

---

## `Metadata`

Build metadata identifiers. Each must match `[0-9A-Za-z-]+`.

```scala
import version.semver.Metadata

// Construction
Metadata.from(List("build", "456"))
// Right(Metadata(List("build", "456")))

Metadata.from(List("")) // Left(InvalidMetadata(...))
Metadata.from(List("a@b")) // Left(InvalidMetadata(...))

// Access
val bm = Metadata(List("build", "456"))
bm.identifiers // List("build", "456")
bm.show // "build.456"
```

---

## `SemVer`

The complete SemVer 2.0.0 representation:

```scala
import version.semver.*

// Construction via apply overloads
SemVer(major, minor, patch) // Final release
SemVer(major, minor, patch, preRelease) // With pre-release
SemVer(major, minor, patch, metadata) // With metadata
SemVer(major, minor, patch, preRelease, meta) // Full form

// Full example
val v = SemVer(
  Major(1),
  Minor(2),
  Patch(3),
  Some(PreRelease.alpha(PreReleaseNumber(1))),
  None
)
v.show // "1.2.3-alpha.1"
```

---

## Ordering

All types provide `Ordering` instances following SemVer precedence:

```scala
val versions = List(
  SemVer.parseUnsafe("1.0.0"),
  SemVer.parseUnsafe("1.0.0-alpha.1"),
  SemVer.parseUnsafe("1.0.0-beta.1"),
  SemVer.parseUnsafe("0.9.0")
)

versions.sorted
// List(0.9.0, 1.0.0-alpha.1, 1.0.0-beta.1, 1.0.0)
```

Precedence rules:

1. Major, minor, patch compared numerically
2. Pre-release versions rank below final releases of the same core
3. Pre-releases compared by classifier precedence, then number
4. Build metadata does not affect precedence
