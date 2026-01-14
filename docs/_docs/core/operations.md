---
title: Operations
---
# Operations

The core library provides composable operations for version manipulation.

## Status Checks

```scala
import version.*
import version.given

val v = Version.parseUnsafe("1.2.3-alpha.1")

v.isStable // true (major > 0)
v.isPreRelease // true (has pre-release)
v.isFinal // false (has pre-release)
v.isStableRelease // false (stable but not final)
v.isSnapshot // false
v.isCandidate // true (pre-release but not snapshot)
```

## Core Extraction

Strip pre-release and metadata:

```scala
val v = Version.parseUnsafe("1.2.3-alpha.1+build.456")
v.core  // Version(1, 2, 3)
```

## Bumping

### By Component

```scala
val v = Version.parseUnsafe("1.2.3")

v.nextMajor  // 2.0.0 (resets minor and patch)
v.nextMinor  // 1.3.0 (resets patch)
v.nextPatch  // 1.2.4
```

### Generic

Use `next[F]` for type-safe generic bumping:

```scala
v.next[MajorVersion]  // 2.0.0
v.next[MinorVersion]  // 1.3.0
v.next[PatchNumber]   // 1.2.4

// Useful in generic code
def bump[F](v: Version)(using Version.Increment[F]): Version = v.next[F]
```

## Pre-release Operations

### Converting to Snapshot

```scala
val v = Version.parseUnsafe("1.2.3")
v.toSnapshot  // 1.2.3-SNAPSHOT
```

### Releasing

Remove pre-release (preserve metadata):

```scala
val v = Version.parseUnsafe("1.2.3-alpha.1")
v.release  // 1.2.3
```

### Setting Pre-release

```scala
val v = Version.parseUnsafe("1.2.3")
v.set(PreRelease.alpha(PreReleaseNumber.fromUnsafe(1)))
// 1.2.3-alpha.1
```

### Advancing Through Classifiers

`advance[C]` moves within or between classifiers:

```scala
val v = Version.parseUnsafe("1.2.3-alpha.1")

// Same classifier: increment number
v.advance[PreReleaseClassifier.Alpha.type]
// Right(1.2.3-alpha.2)

// Higher precedence: start at 1
v.advance[PreReleaseClassifier.Beta.type]
// Right(1.2.3-beta.1)

// To snapshot
v.advance[PreReleaseClassifier.Snapshot.type]
// Right(1.2.3-SNAPSHOT)

// Lower precedence: error
v.advance[PreReleaseClassifier.Dev.type]
// Left(InvalidPreReleaseTransition(Alpha, Dev))

// From final version: error
Version.parseUnsafe("1.2.3").advance[PreReleaseClassifier.Beta.type]
// Left(NotAPreReleaseVersion())
```

### Force-Setting Classifier

`as[C]` sets the classifier directly (works on any version):

```scala
val v = Version.parseUnsafe("1.2.3")

// With specific number
v.as[PreReleaseClassifier.Alpha.type](1)
// Right(1.2.3-alpha.1)

// With default number
v.as[PreReleaseClassifier.ReleaseCandidate.type]
// 1.2.3-rc.1

v.as[PreReleaseClassifier.Snapshot.type]
// 1.2.3-SNAPSHOT
```

## Build Metadata

### Setting

```scala
val v = Version.parseUnsafe("1.2.3")
v.set(BuildMetadata(List("build", "456")))
// 1.2.3+build.456
```

### Removing

```scala
val v = Version.parseUnsafe("1.2.3+build.456")
v.dropMetadata  // 1.2.3
```

## Rendering

### Standard Show

Default excludes build metadata:

```scala
import version.given

val v = Version.parseUnsafe("1.2.3-alpha.1+build.456")
v.show  // "1.2.3-alpha.1"
```

### Extended Show

Include metadata (SHAs truncated to 7 chars):

```scala
given Version.Show = Version.Show.Extended

val v = Version.parseUnsafe("1.2.3-SNAPSHOT+sha1234567890abcdef")
v.show  // "1.2.3-SNAPSHOT+sha1234567"
```

### Raw String

`toString` always includes everything:

```scala
v.toString  // "1.2.3-alpha.1+build.456"
```

## Comparison

Versions implement `Ordered[Version]`:

```scala
val a = Version.parseUnsafe("1.0.0")
val b = Version.parseUnsafe("2.0.0")

a < b           // true
a.compare(b)    // -1
List(b, a).sorted  // List(a, b)
```
