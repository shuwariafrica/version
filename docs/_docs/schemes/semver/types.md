---
title: SemVer Components
---

# SemVer Component Types

## Numeric Components

`Major`, `Minor`, and `Patch` are opaque wrappers over `Long`, so a component derived from a date stamp or a build
counter stays representable. Each rejects a negative value; `PreReleaseNumber` requires a positive one.

```scala
import version.semver.*

// A literal is checked at compile time - a negative one does not compile
val major = Major(2)

// A runtime value is validated
Major.from(2L)  // Right(2)
Major.from(-1L) // Left(InvalidComponent(-1, "Major version", "a non-negative number (>= 0)"))

// Or validated and thrown on
Major.fromUnsafe(2L)

major.value     // 2L
major.increment // 3
Major.reset     // 0
```

`increment` is total: it saturates at `Long.MaxValue` rather than wrapping.

| Type               | Minimum | Reset value |
|--------------------|---------|-------------|
| `Major`            | 0       | 0           |
| `Minor`            | 0       | 0           |
| `Patch`            | 0       | 0           |
| `PreReleaseNumber` | 1       | 1           |

## `PreRelease`

A pre-release is the identifier list the specification describes - non-empty, dot-separated, each identifier drawn
from `[0-9A-Za-z-]`, and no leading zero on a purely numeric identifier. Any list satisfying that is representable,
including labels this library has no name for.

```scala
import version.semver.*

PreRelease.from(List("x", "7", "z", "92")) // Right(x.7.z.92)
PreRelease.from(List("alpha", "01"))       // Left(InvalidPreRelease(List("alpha", "01")))
PreRelease.from(Nil)                       // Left(InvalidPreRelease(Nil))

val pr = PreRelease.alpha(PreReleaseNumber(1))
pr.identifiers // List("alpha", "1")
pr.show        // "alpha.1"
pr.classifier  // Some(Alpha)
pr.increment   // alpha.2
```

`increment` advances the trailing numeric identifier, and leaves a label ending in no number - `SNAPSHOT` - alone.

## `PreReleaseClassifier`

The labels this library builds and recognises by name. They are a convenience for construction and for the `next`
ladder; they are not what precedence is computed from, and a pre-release need not use one.

| Classifier         | Aliases          | Numbered |
|--------------------|------------------|----------|
| `Dev`              | `dev`            | Yes      |
| `Milestone`        | `milestone`, `m` | Yes      |
| `Alpha`            | `alpha`, `a`     | Yes      |
| `Beta`             | `beta`, `b`      | Yes      |
| `ReleaseCandidate` | `rc`, `cr`       | Yes      |
| `Snapshot`         | `SNAPSHOT`       | No       |

```scala
import version.semver.PreReleaseClassifier
import version.semver.PreReleaseClassifier.*

Alpha.show         // "alpha" - the canonical alias
Alpha.aliases      // List("alpha", "a")
Snapshot.versioned // false

PreReleaseClassifier.fromAlias("RC")  // Some(ReleaseCandidate) - case is ignored
PreReleaseClassifier.fromAlias("foo") // None

"beta" match
  case PreReleaseClassifier(c) => c // Beta
```

Pairing a classifier with a number is validated, since two of the combinations are contradictory:

```scala
PreRelease.from(PreReleaseClassifier.Alpha, Some(PreReleaseNumber(1)))
// Right(alpha.1)

PreRelease.from(PreReleaseClassifier.Alpha, None)
// Left(MissingQualifierNumber("alpha"))

PreRelease.from(PreReleaseClassifier.Snapshot, Some(PreReleaseNumber(1)))
// Left(UnexpectedQualifierNumber("SNAPSHOT", 1))
```

## `Metadata`

Build metadata is a non-empty, dot-separated identifier list over the same `[0-9A-Za-z-]` set, but unlike a
pre-release it may carry leading zeros, and it takes no part in precedence.

```scala
import version.semver.Metadata

Metadata.from(List("build", "0456")) // Right(build.0456)
Metadata.from(List(""))              // Left(InvalidMetadata(List("")))
Metadata.from(List("a@b"))           // Left(InvalidMetadata(List("a@b")))

val bm = Metadata(List("build", "456"))
bm.identifiers // List("build", "456")
bm.show        // "build.456"
```

## `SemVer`

```scala
import version.semver.*

SemVer(major, minor, patch)                       // a release
SemVer(major, minor, patch, preRelease)           // with a pre-release
SemVer(major, minor, patch, metadata)             // with build metadata
SemVer(major, minor, patch, preRelease, metadata) // with both

val v = SemVer(Major(1), Minor(2), Patch(3), PreRelease.alpha(PreReleaseNumber(1)))
v.show // "1.2.3-alpha.1"
```

## Ordering

`Ordering[SemVer]` implements clause 11 of the specification.

1. Major, minor, and patch are compared numerically, in that order.
2. A version carrying a pre-release ranks below the release with the same numbers.
3. Pre-releases are compared identifier by identifier: a numeric identifier ranks below an alphanumeric one, numeric
   identifiers compare as numbers, and alphanumeric identifiers compare by ASCII order. Where one list runs out first
   and everything before matched, the shorter ranks lower.
4. Build metadata is ignored.

Two consequences of comparing identifiers rather than named labels are worth stating outright, because both differ
from the ordering Java tooling conventionally applies:

- `1.0.0-SNAPSHOT` ranks **below** `1.0.0-rc.1`, because ASCII order puts `S` before `r`.
- `1.0.0-rc.4` ranks **below** `1.0.0-rc3`, because `rc3` is a single identifier and `rc` sorts before `rc3`.

```scala
val versions = List("1.0.0", "1.0.0-alpha.1", "1.0.0-beta.1", "0.9.0").map(SemVer.parseUnsafe)

versions.sorted
// List(0.9.0, 1.0.0-alpha.1, 1.0.0-beta.1, 1.0.0)
```
