---
title: Operations
---
# Operations

The core library provides composable operations for version manipulation.

## Status Checks

```scala
import version.*

val v = "1.2.3-alpha.1".toVersionUnsafe

v.stable // true (major > 0 and not snapshot)
v.snapshot // false
v.preRelease.isDefined // true

// Check via PreRelease extensions
v.preRelease.get.isAlpha // true
v.preRelease.get.isSnapshot // false
```

## Core Extraction

Strip pre-release and metadata:

```scala
val v = "1.2.3-alpha.1+build.456".toVersionUnsafe
v.core  // Version(1, 2, 3)
```

## Bumping

### Core Components

Use `next[F]` for type-safe bumping. Clears pre-release and metadata:

```scala
val v = "1.2.3".toVersionUnsafe

v.next[MajorVersion]  // 2.0.0 (resets minor and patch)
v.next[MinorVersion]  // 1.3.0 (resets patch)
v.next[PatchNumber]   // 1.2.4

// Useful in generic code
def bump[F](v: Version)(using Version.Increment[F]): Version = v.next[F]
```

### Pre-release Classifiers

`next[C]` also supports versioned pre-release classifiers with precedence-aware semantics:

```scala
import version.PreReleaseClassifier.*

val final = "1.2.3".toVersionUnsafe
val alpha = "1.2.3-alpha.1".toVersionUnsafe
val beta = "1.2.3-beta.1".toVersionUnsafe
val snap = "1.2.3-SNAPSHOT".toVersionUnsafe

// Final version → start pre-release cycle
final.next[Alpha]  // 1.2.3-alpha.1

// Same classifier → increment number
alpha.next[Alpha]  // 1.2.3-alpha.2

// Higher-precedence classifier → advance within cycle
alpha.next[Beta]   // 1.2.3-beta.1
alpha.next[ReleaseCandidate] // 1.2.3-rc.1

// Lower-precedence classifier → new patch cycle
beta.next[Alpha]   // 1.2.4-alpha.1

// Snapshot (highest) → any versioned = new patch cycle
snap.next[Alpha]   // 1.2.4-alpha.1
```

**Note:** `Snapshot` has no `Increment` instance — use `as[Snapshot]` instead.

## Pre-release Operations

### Setting Classifier Directly

`as[C]` sets the pre-release classifier without precedence logic. Clears build metadata.

```scala
val v = "1.2.3".toVersionUnsafe

// With default number (1 for versioned classifiers)
v.as[Alpha] // 1.2.3-alpha.1
v.as[ReleaseCandidate] // 1.2.3-rc.1
v.as[Snapshot] // 1.2.3-SNAPSHOT

// With specific number
v.as[Alpha](5)
// Right(1.2.3-alpha.5)

// Error: snapshot cannot have a number
v.as[Snapshot](1)
// Left(ClassifierNotVersioned(Snapshot))
```

### Removing Pre-release

Use `core` or `copy` to remove pre-release:

```scala
val v = "1.2.3-alpha.1".toVersionUnsafe
v.core // 1.2.3 (also removes metadata)
v.copy(preRelease = None) // 1.2.3 (preserves metadata)
```

## Build Metadata

Use `copy` for metadata operations:

```scala
val v = "1.2.3".toVersionUnsafe

// Setting metadata
v.copy(metadata = Some(Metadata(List("build", "456"))))
// 1.2.3+build.456

// Removing metadata
val vMeta = "1.2.3+build.456".toVersionUnsafe
vMeta.copy(metadata = None) // 1.2.3
```

## Rendering

### Standard Show

Default excludes build metadata:

```scala
import version.*

val v = "1.2.3-alpha.1+build.456".toVersionUnsafe
v.show  // "1.2.3-alpha.1"
```

### Extended Show

Include metadata (SHAs truncated to 7 chars):

```scala
import version.*
given Version.Show = Version.Show.Extended

val v = "1.2.3-SNAPSHOT+sha1234567890abcdef".toVersionUnsafe
v.show  // "1.2.3-SNAPSHOT+sha1234567"
```

## Comparison

Versions implement `Ordered[Version]`:

```scala
val a = "1.0.0".toVersionUnsafe
val b = "2.0.0".toVersionUnsafe

a < b           // true
a.compare(b)    // -1
List(b, a).sorted  // List(a, b)
```
